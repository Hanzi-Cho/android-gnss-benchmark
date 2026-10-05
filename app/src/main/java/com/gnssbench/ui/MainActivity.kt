package com.gnssbench.ui

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gnssbench.BuildConfig
import com.gnssbench.gnss.BenchmarkState
import com.gnssbench.gnss.GnssCollector
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { BenchmarkApp() } } }
    }
}
private fun number(v: Double?, unit: String = "m") = v?.let { "${String.format(Locale.US, "%.3f", it)} $unit" } ?: "미관측"
private fun time(seconds: Long) = "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)

@Composable
fun BenchmarkApp(vm: BenchmarkViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val scroll = rememberScrollState()
    LaunchedEffect(vm.screen, vm.showLive) { scroll.scrollTo(0) }
    DisposableEffect(view, state.active, vm.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.active && vm.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.feedback = if (vm.precise) "정확한 위치 권한 허용됨" else "정확한 위치 권한이 없습니다. 설정에서 정확한 위치를 허용하세요."
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.feedback = if (it) "측정 알림 허용됨" else "알림 거부됨. 측정은 앱에서 Stop할 수 있습니다." }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.export(uri, false) } }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> vm.export(uri, true) } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.screen-- }, enabled = vm.screen > 0) { Text("‹ PREV") }
            Text("GNSS BENCHMARK", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            TextButton(onClick = { vm.screen++ }, enabled = vm.screen < 3) { Text("NEXT ›") }
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(listOf("Device Info", "Static Benchmark", "Results", "Sessions / Compare")[vm.screen], style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            when (vm.screen) {
                0 -> DeviceInfo(state)
                1 -> {
                    if (vm.showLive || state.active) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::stop, enabled = state.active) { Text("Stop") }
                            OutlinedButton(onClick = vm::reset, enabled = !state.active) { Text("새 측정 설정") }
                        }
                        Row {
                            Checkbox(vm.keepScreenOn, { vm.keepScreenOn = it })
                            Text("측정 중 화면 켜짐 유지", Modifier.padding(top = 12.dp))
                        }
                        Text("${state.phase} · 전체 경과 ${time(state.elapsedSeconds)} · 전체 남음 ${time(state.remainingSeconds)}")
                        LiveDashboard(state.analysis, state.config, state.phase)
                        state.latest?.let { sample ->
                            Text("마지막 위치 (${sample.phase}): ${sample.fix.coordinate.latitude}, ${sample.fix.coordinate.longitude}\n현재 표본 actual error ${cm(sample.errorM)} · reported ${cm(sample.fix.accuracyM)}\n위성 ${sample.satellites?.count ?: "—"} / fix 사용 ${sample.satellites?.used ?: "—"} · C/N0 ${number(sample.satellites?.meanCn0, "dB-Hz")}")
                        }
                    } else {
                    Text("야외 open-sky · 기기 정지 · 독립적으로 확보한 WGS84 Ground Truth")
                    OutlinedTextField(vm.latitude, { vm.latitude = it }, label = { Text("Ground Truth latitude (°)") }, enabled = !state.active, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(vm.longitude, { vm.longitude = it }, label = { Text("Ground Truth longitude (°)") }, enabled = !state.active, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(vm.gtSource, { vm.gtSource = it }, label = { Text("GT 측량 방법 / 출처 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(vm.gtUncertaintyM, { vm.gtUncertaintyM = it }, label = { Text("GT 불확실성 (m, 모르면 비워둠)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("현재 GPS fix를 GT로 쓰면 실제 정확도를 검증할 수 없습니다. 기본 실험은 warm-up 포함 총 35분입니다.")
                    Text("Measurement duration · 기본 30분")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 15, 30, 60).forEach { m -> FilterChip(selected = vm.minutes == m, onClick = { vm.minutes = m }, enabled = !state.active, label = { Text("${m}m") }) }
                    }
                    Text("Warm-up 5분 · warm-up 표본은 통계에서 제외")
                    if (BuildConfig.DEBUG) Row {
                        Checkbox(vm.skipWarmup, { vm.skipWarmup = it }, enabled = !state.active)
                        Text("개발용: warm-up 생략 (export에 기록)", Modifier.padding(top = 12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::start, enabled = !state.active && !vm.exporting) { Text("Start") }
                        OutlinedButton(onClick = vm::stop, enabled = state.active) { Text("Stop") }
                        OutlinedButton(onClick = vm::reset, enabled = !state.active) { Text("Reset") }
                    }
                    Text("상태: ${state.phase}", style = MaterialTheme.typography.titleLarge)
                    Text("전체 경과 ${time(state.elapsedSeconds)} · 남음 ${time(state.remainingSeconds)}\n측정 경과 ${time(state.measurementElapsedSeconds)} · 유효 측정 표본 ${state.statistics?.count ?: 0}")
                    state.config?.let { Text("세션 GT ${it.groundTruth.latitude}, ${it.groundTruth.longitude}\n설정 ${it.durationMinutes}분 + warm-up ${it.warmupSeconds}초") }
                    val sample = state.latest
                    Text("마지막 위치: ${sample?.fix?.coordinate?.latitude ?: "미관측"}, ${sample?.fix?.coordinate?.longitude ?: "미관측"}")
                    sample?.let { Text("표본 단계 ${it.phase} · 수신 이후 ${((SystemClock.elapsedRealtimeNanos() - it.receivedNanos) / 1e9).toInt()}초") }
                    Text("Reported accuracy: ${number(sample?.fix?.accuracyM)}")
                    Text("GT 기반 actual error: ${number(sample?.errorM)}", style = MaterialTheme.typography.titleMedium)
                    Text("위성 ${sample?.satellites?.count ?: "미관측"} · fix 사용 ${sample?.satellites?.used ?: "미관측"} · 평균 C/N0 ${number(sample?.satellites?.meanCn0, "dB-Hz")}")
                    Text("거부 표본 ${state.rejected} · raw event ${state.rawEvents}")
                    OutlinedButton(onClick = { vm.screen = 2 }) { Text("현재 통계 / 결과 보기") }
                    }
                }
                2 -> Results(state)
                3 -> SessionsScreen(vm, state.active)
            }
            HorizontalDivider()
            Text(state.message)
            if (vm.feedback.isNotEmpty()) Text(vm.feedback, color = MaterialTheme.colorScheme.primary)
            if (!state.active) {
                OutlinedButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("정확한 위치 권한 요청") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("위치 설정") }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("앱 권한 설정") }
                }
                if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("측정 알림 허용 (선택)") }
                Text("최근 저장 세션 내보내기 · Reset/프로세스 종료 후에도 보존됩니다.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !vm.exporting, onClick = { vm.prepareExport(); jsonExport.launch("gnss-${System.currentTimeMillis()}.json") }) { Text("JSON export") }
                    Button(enabled = !vm.exporting, onClick = { vm.prepareExport(); csvExport.launch("gnss-${System.currentTimeMillis()}.csv") }) { Text("CSV export") }
                }
                if (vm.exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DeviceInfo(state: BenchmarkState) {
    val manager = LocalContext.current.getSystemService(LocationManager::class.java)
    Text("DETECTED · Android public API", style = MaterialTheme.typography.titleMedium)
    val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} / ${Build.SOC_MODEL}" else "API 31 이상에서 제공"
    Text("Manufacturer: ${Build.MANUFACTURER}\nModel: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nAPI: ${Build.VERSION.SDK_INT}\nHardware: ${Build.HARDWARE}\nBoard: ${Build.BOARD}\nSoC: $soc")
    Text("GNSS hardware model: ${manager.gnssHardwareModelName ?: "미보고"}\nGNSS hardware year: ${manager.gnssYearOfHardware.takeIf { it > 0 } ?: "미보고"}\nGPS provider enabled: ${manager.isProviderEnabled(LocationManager.GPS_PROVIDER)}")
    Text("GNSS runtime 관측은 benchmark 실행 중 갱신됩니다. 아래 값은 마지막 관측이며 미관측은 미지원이라는 뜻이 아닙니다.")
    val s = state.satellites
    Text("Satellite count: ${s?.count ?: "미관측"}\nUsed in fix: ${s?.used ?: "미관측"}\nRaw measurement events: ${state.rawEvents}")
    s?.let { Text("마지막 위성 상태 수신 후 ${((SystemClock.elapsedRealtimeNanos() - it.elapsedNanos) / 1e9).toInt()}초") }
    Text("DERIVED · 관측 자료에서 계산", style = MaterialTheme.typography.titleMedium)
    Text("Mean C/N0: ${number(s?.meanCn0, "dB-Hz")}\nStrongest C/N0: ${number(s?.strongestCn0, "dB-Hz")}")
    Text("Constellations: " + (s?.satellites?.groupingBy { it.constellation }?.eachCount()?.entries?.joinToString { "${it.key} ${it.value}" } ?: "미관측"))
    Text("Observed carrier frequencies / derived band:")
    val frequencies = s?.satellites?.filter { it.carrierHz != null }?.distinctBy { it.constellation to it.carrierHz }
    if (frequencies.isNullOrEmpty()) Text("미관측")
    else frequencies.forEach { Text("${it.constellation}: ${number(it.carrierHz?.div(1e6), "MHz")} → ${GnssCollector.band(it)}") }
    Text("SPECIFICATION · Phase 1은 제조사 specification을 조회하지 않습니다. 관측 대역 목록은 기기의 전체 지원 대역을 보장하지 않습니다.")
}

@Composable
private fun Results(state: BenchmarkState) {
    LiveDashboard(state.analysis, state.config, state.phase)
    Text("${Build.MANUFACTURER} ${Build.MODEL}\n설정 측정시간: ${state.config?.durationMinutes ?: "—"}분 · 실제 측정 경과: ${time(state.measurementElapsedSeconds)}\n표본: ${state.statistics?.count ?: 0} · 상태: ${state.phase}")
    val stats = state.statistics
    if (stats == null) { Text("유효한 측정 구간 표본이 없습니다. 실제 오차 통계는 표시하지 않습니다."); return }
    listOf("Mean" to stats.meanM, "Median / CEP50" to stats.cep50M, "RMSE" to stats.rmseM, "CEP68" to stats.cep68M, "CEP95" to stats.cep95M, "CEP99" to stats.cep99M, "MAX" to stats.maxM).forEach { (label, value) -> Text("$label: ${number(value)}") }
    stats.withinPercent.forEach { (radius, percent) -> Text("Within ${radius}m: ${number(percent, "%")}") }
    Text("GT 중심 수평 오차 · warm-up 제외 · Android reported accuracy와 별개 · R7 percentile")
}
