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
import com.gnssbench.gnss.BenchmarkService
import com.gnssbench.model.Coordinate
import com.gnssbench.recording.Recording
import android.os.Handler
import android.os.Looper

class BenchmarkViewModel(app: Application) : AndroidViewModel(app) {
    var latitude by mutableStateOf("")
    var longitude by mutableStateOf("")
    var minutes by mutableIntStateOf(30)
    var skipWarmup by mutableStateOf(false)
    var screen by mutableIntStateOf(0)
    var feedback by mutableStateOf("")
    var exporting by mutableStateOf(false)
    val state = BenchmarkService.state
    private val context get() = getApplication<Application>()
    val precise get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val providerEnabled get() = context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
    fun start() {
        val gt = Coordinate.parse(latitude, longitude)
        if (gt == null) { feedback = "GT 오류: latitude -90..90 / longitude -180..180 범위의 유한한 숫자가 필요합니다."; return }
        if (!precise) { feedback = "정확한 위치 권한이 필요합니다. 앱 설정 > 권한 > 위치 > 정확한 위치를 허용하세요."; return }
        if (!providerEnabled) { feedback = "GPS가 꺼져 있습니다. 위치 설정에서 켜주세요."; return }
        runCatching { ContextCompat.startForegroundService(context, Intent(context, BenchmarkService::class.java).putExtra("lat", gt.latitude).putExtra("lon", gt.longitude).putExtra("minutes", minutes).putExtra("skip", skipWarmup)) }
            .onSuccess { feedback = "시작 요청됨" }.onFailure { feedback = "시작 실패: ${it.message}" }
    }
    fun stop() { context.startService(Intent(context, BenchmarkService::class.java).setAction(BenchmarkService.ACTION_STOP)) }
    fun reset() { BenchmarkService.reset(); feedback = "새 세션 준비. 기존 저장 기록은 유지됩니다." }
    fun export(uri: Uri, csv: Boolean) {
        exporting = true
        Recording.io.execute {
            val result = runCatching {
                val file = Recording.latest(context) ?: error("저장된 세션이 없습니다.")
                val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("파일을 열 수 없습니다.")
                Recording.export(file, output, csv)
            }
            Handler(Looper.getMainLooper()).post { exporting = false; feedback = result.fold({ "내보내기 완료" }, { "내보내기 실패: ${it.message}" }) }
        }
    }
}
