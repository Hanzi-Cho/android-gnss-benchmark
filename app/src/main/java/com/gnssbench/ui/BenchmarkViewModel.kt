package com.gnssbench.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.gnssbench.gnss.BenchmarkService
import com.gnssbench.model.Coordinate
import com.gnssbench.recording.Recording
import com.gnssbench.recording.SessionEntry
import com.gnssbench.recording.SessionReport
import com.gnssbench.recording.SessionReader
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import android.os.Handler
import android.os.Looper

class BenchmarkViewModel(app: Application, private val savedState: SavedStateHandle) : AndroidViewModel(app) {
    var latitude by mutableStateOf("")
    var longitude by mutableStateOf("")
    var gtSource by mutableStateOf("")
    var gtUncertaintyM by mutableStateOf("")
    var showLive by mutableStateOf(false)
    var keepScreenOn by mutableStateOf(true)
    var minutes by mutableIntStateOf(30)
    var skipWarmup by mutableStateOf(false)
    var screen by mutableIntStateOf(0)
    var feedback by mutableStateOf("")
    var exporting by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var sessions by mutableStateOf<List<SessionEntry>>(emptyList())
    var reportA by mutableStateOf<SessionReport?>(null)
    var reportB by mutableStateOf<SessionReport?>(null)
    val state = BenchmarkService.state
    private val context get() = getApplication<Application>()
    val precise get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val providerEnabled get() = context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
    fun start() {
        val gt = Coordinate.parse(latitude, longitude)
        if (gt == null) { feedback = "GT 오류: latitude -90..90 / longitude -180..180 범위의 유한한 숫자가 필요합니다."; return }
        val uncertainty = gtUncertaintyM.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (gtUncertaintyM.isNotBlank() && (uncertainty == null || !uncertainty.isFinite() || uncertainty < 0)) { feedback = "GT 불확실성은 0 이상의 유한한 m 값으로 입력하거나 비워두세요."; return }
        if (!precise) { feedback = "정확한 위치 권한이 필요합니다. 앱 설정 > 권한 > 위치 > 정확한 위치를 허용하세요."; return }
        if (!providerEnabled) { feedback = "GPS가 꺼져 있습니다. 위치 설정에서 켜주세요."; return }
        runCatching { ContextCompat.startForegroundService(context, Intent(context, BenchmarkService::class.java).putExtra("lat", gt.latitude).putExtra("lon", gt.longitude).putExtra("minutes", minutes).putExtra("skip", skipWarmup).putExtra("gtSource", gtSource.trim()).apply { uncertainty?.let { putExtra("gtUncertainty", it) } }) }
            .onSuccess { feedback = "시작 요청됨"; showLive = true }.onFailure { feedback = "시작 실패: ${it.message}" }
    }
    fun stop() { context.startService(Intent(context, BenchmarkService::class.java).setAction(BenchmarkService.ACTION_STOP)) }
    fun reset() { BenchmarkService.reset(); showLive = false; feedback = "새 세션 준비. 기존 저장 기록은 유지됩니다." }
    fun prepareExport(path: String? = null) { savedState["exportPath"] = path }
    fun export(uri: Uri, csv: Boolean) {
        exporting = true
        val selectedPath = savedState.get<String>("exportPath")
        Recording.io.execute {
            val result = runCatching {
                val file = selectedPath?.let { File(it) } ?: Recording.latest(context) ?: error("저장된 세션이 없습니다.")
                val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("파일을 열 수 없습니다.")
                Recording.export(file, output, csv)
            }
            Handler(Looper.getMainLooper()).post { exporting = false; feedback = result.fold({ "내보내기 완료" }, { "내보내기 실패: ${it.message}" }) }
        }
    }
    fun refreshSessions() {
        Recording.io.execute {
            val result = runCatching {
                Recording.directory(context).listFiles().orEmpty().filter { it.extension == "jsonl" }.sortedByDescending { it.lastModified() }.map { file ->
                    val title = runCatching {
                        val meta = file.bufferedReader().use { JSONObject(it.readLine()).getJSONObject("payload") }
                        "${meta.optString("manufacturer")} ${meta.optString("model")} · ${DateFormat.getDateTimeInstance().format(Date(meta.getLong("testStartWallTimeMillis")))}"
                    }.getOrDefault(file.name)
                    SessionEntry(file.absolutePath, title)
                }
            }
            Handler(Looper.getMainLooper()).post { result.onSuccess { sessions = it }.onFailure { feedback = "세션 목록 오류: ${it.message}" } }
        }
    }
    fun loadSession(entry: SessionEntry, asB: Boolean) = loadReport(asB) { SessionReader.readJournal(File(entry.path)) }
    fun importSession(uri: Uri, asB: Boolean) = loadReport(asB) {
        val input = context.contentResolver.openInputStream(uri) ?: error("파일을 열 수 없습니다.")
        input.bufferedReader().use { SessionReader.readExport(it) }
    }
    private fun loadReport(asB: Boolean, read: () -> SessionReport) {
        loading = true
        Recording.io.execute {
            val result = runCatching(read)
            Handler(Looper.getMainLooper()).post {
                loading = false
                result.onSuccess { if (asB) reportB = it else reportA = it; feedback = "세션 불러오기 완료" }
                    .onFailure { feedback = "불러오기 실패: ${it.message}" }
            }
        }
    }
}
