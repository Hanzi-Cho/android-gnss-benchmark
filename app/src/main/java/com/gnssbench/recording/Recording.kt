package com.gnssbench.recording

import android.content.Context
import android.os.Build
import com.gnssbench.BuildConfig
import com.gnssbench.analysis.Statistics
import com.gnssbench.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) ->
    val safe = when (v) { is Double -> v.takeIf { it.isFinite() }; is Float -> v.takeIf { it.isFinite() }; else -> v }
    put(k, safe ?: JSONObject.NULL)
} }
fun SatelliteSnapshot.toJson() = json("elapsedRealtimeNanos" to elapsedNanos, "count" to count, "usedInFix" to used, "meanCn0DbHz" to meanCn0, "strongestCn0DbHz" to strongestCn0,
    "satellites" to JSONArray(satellites.map { json("svid" to it.svid, "constellation" to it.constellation, "cn0DbHz" to it.cn0DbHz, "usedInFix" to it.used, "carrierFrequencyHz" to it.carrierHz, "elevationDeg" to it.elevationDeg, "azimuthDeg" to it.azimuthDeg) }))
fun Sample.toJson() = json("wallTimeMillis" to fix.wallTimeMillis, "elapsedRealtimeNanos" to fix.elapsedNanos, "receivedElapsedRealtimeNanos" to receivedNanos, "phase" to phase.name,
    "latitude" to fix.coordinate.latitude, "longitude" to fix.coordinate.longitude, "altitudeM" to fix.altitudeM, "reportedHorizontalAccuracyM" to fix.accuracyM, "speedMps" to fix.speedMps, "bearingDeg" to fix.bearingDeg,
    "measuredHorizontalErrorM" to errorM, "satelliteCount" to satellites?.count, "satellitesUsedInFix" to satellites?.used, "meanCn0DbHz" to satellites?.meanCn0, "satelliteSnapshotElapsedNanos" to satellites?.elapsedNanos,
    "satelliteSnapshotAgeNanos" to satellites?.let { receivedNanos - it.elapsedNanos })
fun Statistics.toJson() = json("sampleCount" to count, "meanM" to meanM, "medianM" to medianM, "rmseM" to rmseM, "cep50M" to cep50M, "cep68M" to cep68M, "cep95M" to cep95M, "cep99M" to cep99M, "maxM" to maxM,
    "withinPercent" to JSONObject(withinPercent.mapKeys { it.key.toString() }))

/** Shared serial executor ensures export waits for all previously submitted journal writes. */
object Recording {
    val io = Executors.newSingleThreadExecutor()
    fun directory(context: Context) = File(context.filesDir, "sessions").apply { mkdirs() }
    fun latest(context: Context): File? = directory(context).listFiles()?.filter { it.extension == "jsonl" }?.maxByOrNull { it.lastModified() }
    fun create(context: Context, config: Config, wallStart: Long, monoStart: Long): File {
        val file = File(directory(context), "$wallStart-${UUID.randomUUID()}.jsonl")
        append(file, "metadata", json("schemaVersion" to 1, "sessionId" to file.nameWithoutExtension, "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "hardware" to Build.HARDWARE,
            "androidVersion" to Build.VERSION.RELEASE, "apiLevel" to Build.VERSION.SDK_INT, "appVersion" to BuildConfig.VERSION_NAME,
            "testStartWallTimeMillis" to wallStart, "startElapsedRealtimeNanos" to monoStart,
            "groundTruth" to json("latitude" to config.groundTruth.latitude, "longitude" to config.groundTruth.longitude),
            "warmupSeconds" to config.warmupSeconds, "measurementDurationSeconds" to config.durationMinutes * 60,
            "provider" to "gps", "requestedIntervalMillis" to 1000, "percentileMethod" to "R7 linear (n-1)p", "distanceMethod" to "haversine mean radius 6371008.8 m", "errorUnits" to "m"))
        return file
    }
    fun append(file: File, type: String, payload: JSONObject) { file.appendText(json("type" to type, "payload" to payload).toString() + "\n", Charsets.UTF_8) }
    private fun forEachRecord(file: File, consume: (JSONObject) -> Unit) {
        file.bufferedReader().useLines { lines -> lines.forEach { line ->
            // A torn last write after process death is retained as an explicit recovery warning.
            val record = runCatching { JSONObject(line) }.getOrElse { json("type" to "recoveryWarning", "payload" to json("reason" to "Malformed journal record", "raw" to line)) }
            consume(record)
        } }
    }
    fun export(file: File, output: OutputStream, csv: Boolean) {
        output.bufferedWriter(Charsets.UTF_8).use { w ->
            if (csv) {
                val columns = listOf("wallTimeMillis", "elapsedRealtimeNanos", "phase", "latitude", "longitude", "altitudeM", "reportedHorizontalAccuracyM", "speedMps", "bearingDeg", "measuredHorizontalErrorM", "satelliteCount", "satellitesUsedInFix", "meanCn0DbHz")
                w.appendLine("record_type,${columns.joinToString(",")},payload_json")
                var hasSummary = false
                fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
                forEachRecord(file) { record ->
                    val type = record.getString("type")
                    if (type == "summary") hasSummary = true
                    val p = record.getJSONObject("payload")
                    w.appendLine((listOf(type) + columns.map { if (p.isNull(it)) "" else p.optString(it) } + p.toString()).joinToString(",") { cell(it) })
                }
                if (!hasSummary) w.appendLine((listOf("summary") + columns.map { "" } + "{\"status\":\"Interrupted\",\"reason\":\"No final summary; process or storage interrupted\"}").joinToString(",") { cell(it) })
            } else {
                w.append("{\"schemaVersion\":1")
                for ((type, name) in listOf("metadata" to "metadata", "sample" to "samples", "satellites" to "satellites", "raw" to "rawObservations", "event" to "events", "recoveryWarning" to "recoveryWarnings", "summary" to "summary")) {
                    w.append(",\"$name\":[")
                    var first = true
                    forEachRecord(file) { r -> if (r.getString("type") == type) {
                        if (!first) w.append(',')
                        w.append(r.getJSONObject("payload").toString()); first = false
                    } }
                    if (type == "summary" && first) w.append("{\"status\":\"Interrupted\",\"reason\":\"No final summary; process or storage interrupted\"}")
                    w.append(']')
                }
                w.append('}')
            }
        }
    }
}
