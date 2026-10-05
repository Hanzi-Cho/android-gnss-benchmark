package com.gnssbench.recording

import android.util.JsonReader
import android.util.JsonToken
import com.gnssbench.analysis.*
import com.gnssbench.model.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.Reader

data class SessionEntry(val path: String, val title: String)
data class SessionReport(val title: String, val config: Config, val phase: Phase, val analysis: LiveAnalysis, val note: String, val filePath: String? = null)

/** Recompute from recorded coordinates; never trust imported summary/derived errors as observations. */
class SessionReplay {
    private var metadata: JSONObject? = null
    private var summary: JSONObject? = null
    private val samples = mutableListOf<JSONObject>()
    private var malformed = 0
    fun record(type: String, payload: JSONObject) {
        when (type) {
            "metadata" -> { require(metadata == null) { "Metadata 중복" }; metadata = payload }
            "sample" -> { require(samples.size < 50000) { "지원 표본 수(50,000) 초과" }; samples += payload }
            "summary" -> { require(summary == null) { "Summary 중복" }; summary = payload }
            "recoveryWarning" -> malformed++
        }
    }
    fun finish(filePath: String? = null): SessionReport {
        val m = requireNotNull(metadata) { "세션 metadata가 없습니다." }
        require(m.getInt("schemaVersion") == 1) { "지원하지 않는 schemaVersion" }
        require(m.optString("provider") == "gps") { "GPS baseline 파일만 지원합니다." }
        val gt = m.getJSONObject("groundTruth")
        val durationSeconds = m.getInt("measurementDurationSeconds")
        require(durationSeconds % 60 == 0)
        val config = Config(Coordinate(gt.getDouble("latitude"), gt.getDouble("longitude")), durationSeconds / 60,
            m.getInt("warmupSeconds"), gt.optString("source", ""), gt.optionalDouble("declaredUncertaintyM"))
        val start = m.getLong("startElapsedRealtimeNanos") + config.warmupNanos
        val analyzer = LiveAnalyzer(config.groundTruth, start)
        var last = start
        var previous = Long.MIN_VALUE
        samples.forEach { p ->
            if (p.getString("phase") == Phase.Measuring.name) {
                val timestamp = p.getLong("elapsedRealtimeNanos")
                require(timestamp >= start && timestamp - start < config.measurementNanos && timestamp > previous) { "표본 시간 순서/측정 구간 오류" }
                previous = timestamp
                val coordinate = Coordinate(p.getDouble("latitude"), p.getDouble("longitude"))
                val fix = Fix(p.getLong("wallTimeMillis"), timestamp, coordinate, p.optionalDouble("altitudeM"), p.optionalDouble("reportedHorizontalAccuracyM"), p.optionalDouble("speedMps"), p.optionalDouble("bearingDeg"))
                analyzer.add(Sample(fix, timestamp, Phase.Measuring, Geodesy.distanceM(config.groundTruth, coordinate), null))
                last = timestamp
            }
        }
        val s = summary
        val interrupted = s == null || s.optString("status") == "Interrupted"
        val end = if (s != null && s.has("endElapsedRealtimeNanos")) s.getLong("endElapsedRealtimeNanos") else last
        require(end >= last || samples.none { it.optString("phase") == "Measuring" }) { "종료 시각이 표본보다 빠릅니다." }
        val elapsed = (end - start).coerceIn(0, config.measurementNanos) / 1e9
        val phase = if (interrupted || malformed > 0 || s?.optString("status") == "Error" || s?.optBoolean("recordingIncomplete") == true) Phase.Error else Phase.Completed
        val note = buildString {
            append(s?.optString("reason") ?: "Interrupted: 종료 기록 없음. 경과 시간은 마지막 기록 표본까지만 추정.")
            if (malformed > 0) append(" · 손상 기록 $malformed 개; 저장된 유효 표본만 분석")
            append(" · 좌표에서 통계 재계산 (warm-up 제외)")
        }
        return SessionReport("${m.optString("manufacturer")} ${m.optString("model")} · ${m.optString("sessionId")}", config, phase, analyzer.snapshot(elapsed), note, filePath)
    }
}

private fun JSONObject.optionalDouble(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key).also { require(it.isFinite() && (key != "reportedHorizontalAccuracyM" || it >= 0)) }

object SessionReader {
    fun readJournal(file: File): SessionReport {
        val replay = SessionReplay()
        file.bufferedReader().useLines { lines -> lines.forEach { line ->
            val record = runCatching { JSONObject(line) }.getOrNull()
            if (record == null) replay.record("recoveryWarning", JSONObject())
            else replay.record(record.getString("type"), record.getJSONObject("payload"))
        } }
        return replay.finish(file.absolutePath)
    }
    /** Streaming import skips large raw observation arrays without materializing them in RAM. */
    fun readExport(reader: Reader): SessionReport {
        val replay = SessionReplay()
        var schema = -1
        JsonReader(reader).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (val name = r.nextName()) {
                    "schemaVersion" -> schema = r.nextInt()
                    "metadata", "samples", "summary", "recoveryWarnings" -> {
                        val type = when (name) { "samples" -> "sample"; "recoveryWarnings" -> "recoveryWarning"; else -> name }
                        r.beginArray()
                        while (r.hasNext()) replay.record(type, readObject(r))
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            require(r.peek() == JsonToken.END_DOCUMENT) { "파일 뒤에 추가 데이터가 있습니다." }
        }
        require(schema == 1) { "지원하지 않는 export schema" }
        return replay.finish()
    }
    private fun readObject(r: JsonReader): JSONObject {
        val result = JSONObject()
        r.beginObject()
        while (r.hasNext()) result.put(r.nextName(), readValue(r, 0))
        r.endObject()
        return result
    }
    private fun readValue(r: JsonReader, depth: Int): Any {
        require(depth < 12) { "JSON nesting limit" }
        return when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val obj = JSONObject(); r.beginObject()
                while (r.hasNext()) obj.put(r.nextName(), readValue(r, depth + 1))
                r.endObject(); obj
            }
            JsonToken.BEGIN_ARRAY -> {
                val array = JSONArray(); r.beginArray()
                while (r.hasNext()) { require(array.length() < 50000); array.put(readValue(r, depth + 1)) }
                r.endArray(); array
            }
            JsonToken.NULL -> { r.nextNull(); JSONObject.NULL }
            JsonToken.BOOLEAN -> r.nextBoolean()
            JsonToken.NUMBER -> { val text = r.nextString(); text.toLongOrNull() ?: text.toDouble().also { require(it.isFinite()) } }
            else -> r.nextString().also { require(it.length <= 65536) }
        }
    }
}
