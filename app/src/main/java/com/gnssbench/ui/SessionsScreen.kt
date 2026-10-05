package com.gnssbench.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SessionsScreen(vm: BenchmarkViewModel, active: Boolean) {
    LaunchedEffect(active) { if (!active) vm.refreshSessions() }
    val importA = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.importSession(uri, false) } }
    val importB = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> vm.importSession(uri, true) } }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.export(uri, false) } }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> vm.export(uri, true) } }
    val enabled = !active && !vm.loading && !vm.exporting
    Text("저장된 세션 A를 다시 열거나, 두 기기의 JSON export를 A/B로 불러와 비교합니다. CSV는 분석 도구용 내보내기를 지원합니다.")
    if (active) Text("현재 측정 종료 후 파일을 불러올 수 있습니다.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled, onClick = { importA.launch(arrayOf("application/json", "text/plain")) }) { Text("JSON → A") }
        OutlinedButton(enabled = enabled, onClick = { importB.launch(arrayOf("application/json", "text/plain")) }) { Text("JSON → B") }
    }
    TextButton(enabled = enabled, onClick = vm::refreshSessions) { Text("저장 목록 새로고침") }
    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (vm.sessions.isEmpty()) Text("저장 세션 없음")
    vm.sessions.forEach { entry ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(entry.title)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = enabled, onClick = { vm.loadSession(entry, false) }) { Text("A로 열기") }
                    TextButton(enabled = enabled, onClick = { vm.loadSession(entry, true) }) { Text("B로 비교") }
                }
            }
        }
    }
    val a = vm.reportA
    val b = vm.reportB
    if (a != null && b != null) {
        Text("A / B 비교 · 재계산한 관측 지표", style = MaterialTheme.typography.titleLarge)
        Text("A: ${a.title}\nB: ${b.title}")
        Text("CEP95: A ${cm(a.analysis.statistics?.cep95M)} / B ${cm(b.analysis.statistics?.cep95M)}\nRMSE: A ${cm(a.analysis.statistics?.rmseM)} / B ${cm(b.analysis.statistics?.rmseM)}\nMAX: A ${cm(a.analysis.statistics?.maxM)} / B ${cm(b.analysis.statistics?.maxM)}\n표본: A ${a.analysis.statistics?.count ?: 0} / B ${b.analysis.statistics?.count ?: 0}")
        Text("측정 경과 A ${a.analysis.elapsedSeconds.toInt()}초 / B ${b.analysis.elapsedSeconds.toInt()}초\n설정 A ${a.config.durationMinutes}분 / B ${b.config.durationMinutes}분\nWarm-up A ${a.config.warmupSeconds}초 / B ${b.config.warmupSeconds}초")
        Text("환경·GT 품질·설치 자세가 같다는 보장은 없습니다. 수치 차이만으로 기기 우열을 확정하지 마세요. A 상태 ${a.phase} / B 상태 ${b.phase}", style = MaterialTheme.typography.bodySmall)
        if (a.config.durationMinutes != b.config.durationMinutes || a.config.warmupSeconds != b.config.warmupSeconds) Text("프로토콜 불일치: 측정시간 또는 warm-up 설정이 다릅니다.", color = MaterialTheme.colorScheme.error)
    }
    a?.let { report ->
        HorizontalDivider()
        Text("A · ${report.title}", style = MaterialTheme.typography.titleMedium)
        Text(report.note)
        report.filePath?.let { path ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = enabled, onClick = { vm.prepareExport(path); jsonExport.launch("session-a.json") }) { Text("A JSON export") }
                OutlinedButton(enabled = enabled, onClick = { vm.prepareExport(path); csvExport.launch("session-a.csv") }) { Text("A CSV export") }
            }
        }
        LiveDashboard(report.analysis, report.config, report.phase)
    }
    b?.let { report ->
        HorizontalDivider()
        Text("B · ${report.title}", style = MaterialTheme.typography.titleMedium)
        Text(report.note)
        LiveDashboard(report.analysis, report.config, report.phase)
    }
}
