package com.gnssbench.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gnssbench.analysis.*
import com.gnssbench.model.Config
import com.gnssbench.model.Phase
import java.util.Locale
import kotlin.math.*

internal fun cm(m: Double?) = m?.let { "${String.format(Locale.US, "%.0f", it * 100)} cm" } ?: "—"
private fun decimal(n: Double?) = n?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
private fun clock(s: Double) = "%02d:%02d".format(Locale.US, s.toInt() / 60, s.toInt() % 60)
private val ActualColor = Color(0xFFB3261E)
private val ReportedColor = Color(0xFF2465AD)
private val RollingColor = Color(0xFF00836B)

@Composable
fun LiveDashboard(data: LiveAnalysis, config: Config?, phase: Phase) {
    var showMethod by remember { mutableStateOf(false) }
    val stats = data.statistics
    val full = phase == Phase.Completed && config != null && data.elapsedSeconds >= config.durationMinutes * 60
    val status = when {
        phase == Phase.Error -> "오류로 중단된 세션"
        phase == Phase.WarmingUp -> "Warm-up · 실제 오차 통계 집계 전"
        full -> "설정한 측정시간 완료 · 해당 세션 관측 결과"
        phase == Phase.Completed -> "중간에 중단한 부분 측정"
        phase == Phase.Measuring -> "측정 중 · 잠정 누적 결과"
        else -> "측정 대기"
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status, style = MaterialTheme.typography.labelLarge)
            Text("이 세션의 수평 오차는?", style = MaterialTheme.typography.titleMedium)
            Text(cm(stats?.cep95M), style = MaterialTheme.typography.displayMedium)
            Text("누적 CEP95 · ${stats?.let { String.format(Locale.US, "%.2f m", it.cep95M) } ?: "측정 표본 없음"}")
            if (stats != null) Text("약 95%의 위치가 GT에서 이 거리 이내 · R7 백분위수")
            else Text("Warm-up이 끝난 뒤 유효한 GNSS 위치가 수신되면 표시합니다. 0 cm로 간주하지 않습니다.")
        }
    }
    Text("측정 ${clock(data.elapsedSeconds)} / ${config?.let { "${it.durationMinutes}:00" } ?: "—"} · 표본 ${stats?.count ?: 0}", style = MaterialTheme.typography.titleMedium)
    if (config != null && (config.groundTruthSource.isBlank() || config.groundTruthUncertaintyM == null)) Text("GT 품질 미확인 · 입력 좌표 기준 오차", color = MaterialTheme.colorScheme.error)
    Text("평균 ${cm(stats?.meanM)} · 중앙값 ${cm(stats?.medianM)}\nRMSE ${cm(stats?.rmseM)} · 최대 ${cm(stats?.maxM)}")
    Text("최근 60초 CEP95: ${cm(data.rolling95M)} · ${data.rollingCount}개 표본", style = MaterialTheme.typography.titleMedium)
    Text("수신 ${decimal(data.coveragePercent)}% · 마지막 fix ${decimal(data.lastFixAgeSeconds)}초 전", style = MaterialTheme.typography.bodySmall)
    if ((data.lastFixAgeSeconds ?: data.elapsedSeconds) > 3 && phase == Phase.Measuring) Text("최근 위치 수신이 지연되고 있습니다. 누적 오차는 이전 표본 기준입니다.", color = MaterialTheme.colorScheme.error)
    ErrorTimeline(data)
    ScatterPlot(data)
    Text("cm는 표시 단위이며 cm급 정확도를 보증하지 않습니다. CEP95는 해당 세션의 관측 백분위수이며 95% 신뢰구간이 아닙니다.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { showMethod = !showMethod }) { Text(if (showMethod) "GT / 수집 품질 / 지표 해석 접기" else "GT / 수집 품질 / 지표 해석 펼치기") }
    if (showMethod) {
        config?.let {
            Text("GT: ${it.groundTruth.latitude}, ${it.groundTruth.longitude}\n출처: ${it.groundTruthSource.ifBlank { "미확인" }}\n불확실성(사용자 입력): ${cm(it.groundTruthUncertaintyM)}")
            if (it.warmupSeconds == 0) Text("개발용 warm-up 생략 세션", color = MaterialTheme.colorScheme.error)
        }
        Text("R7 CEP95 이하 실제 표본 비율: ${decimal(data.withinCep95Percent)}%. 반올림 표시와 실제 계산값은 다를 수 있습니다.")
        Text("1초 구간 수신 비율 ${decimal(data.coveragePercent)}% (${data.occupiedSeconds}/${data.totalSeconds})\n최대 무수신 간격 ${decimal(data.maxGapSeconds)}초. 수신 비율은 정확도 신뢰도나 독립 표본 비율이 아닙니다.")
        Text("Rolling 첫 60초는 가용 구간만 사용하며 최근 구간에 표본이 없으면 —로 표시합니다.")
        Text("모든 통계는 warm-up 제외·GT 중심·보정 전 위치 기준입니다. 표본의 시간 상관과 GT 오차가 있으므로 30분 측정만으로 다른 환경의 정확도를 보장하지 않습니다. 거리 계산은 구면 근사입니다.")
    }
}

@Composable
private fun ErrorTimeline(data: LiveAnalysis) {
    var lastFiveMinutes by remember { mutableStateOf(false) }
    var selected by remember(data.points.firstOrNull()) { mutableStateOf<ErrorPoint?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!lastFiveMinutes, { lastFiveMinutes = false; selected = null }, label = { Text("전체 시간") })
        FilterChip(lastFiveMinutes, { lastFiveMinutes = true; selected = null }, label = { Text("최근 5분") })
    }
    Text("수평 오차 추이 · 세로 cm / 가로 측정 경과", style = MaterialTheme.typography.titleMedium)
    Text("● 실제 오차", color = ActualColor)
    Text("● Android reported accuracy (68% 추정 반경)", color = ReportedColor)
    Text("● 최근 60초 CEP95", color = RollingColor)
    val end = max(1.0, data.elapsedSeconds)
    val start = if (lastFiveMinutes) max(0.0, end - 300) else 0.0
    val visible = remember(data.points, start) { data.points.filter { it.seconds >= start } }
    val series = remember(visible) { listOf(
        chartSegments(visible.map { it.seconds to it.errorM }),
        chartSegments(visible.mapNotNull { p -> p.reportedM?.let { p.seconds to it } }),
        chartSegments(visible.mapNotNull { p -> p.rolling95M?.let { p.seconds to it } }),
    ) }
    val ymax = max(.1, visible.maxOfOrNull { max(it.errorM, max(it.reportedM ?: 0.0, it.rolling95M ?: 0.0)) }?.times(1.12) ?: 1.0)
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = "실시간 오차 그래프. ${visible.size}개 표본, 누적 CEP95 ${cm(data.statistics?.cep95M)}. 그래프를 누르면 해당 표본을 읽습니다."
    Canvas(Modifier.fillMaxWidth().height(230.dp).semantics { contentDescription = description }
        .pointerInput(visible, start, end) { detectTapGestures { touch ->
            val left = 58.dp.toPx()
            val ratio = ((touch.x - left) / (size.width - left - 12.dp.toPx())).coerceIn(0f, 1f)
            val second = start + ratio * (end - start)
            selected = visible.minByOrNull { abs(it.seconds - second) }
        } }) {
        val left = 58.dp.toPx(); val top = 12.dp.toPx(); val right = size.width - 12.dp.toPx(); val bottom = size.height - 28.dp.toPx()
        fun x(t: Double) = left + ((t - start) / (end - start) * (right - left)).toFloat()
        fun y(m: Double) = bottom - (m / ymax * (bottom - top)).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor.toArgb(); textSize = 11.sp.toPx() }
        for (i in 0..4) {
            val v = ymax * i / 4
            drawLine(gridColor, Offset(left, y(v)), Offset(right, y(v)))
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.0f", v * 100), 0f, y(v) + 4.dp.toPx(), paint)
        }
        listOf(start, (start + end) / 2, end).forEach { t ->
            val label = clock(t)
            drawContext.canvas.nativeCanvas.drawText(label, (x(t) - paint.measureText(label) / 2).coerceIn(0f, size.width - paint.measureText(label)), size.height - 4.dp.toPx(), paint)
        }
        series.forEachIndexed { index, segments ->
            val color = listOf(ActualColor, ReportedColor, RollingColor)[index]
            segments.forEach { segment ->
                if (segment.size == 1) drawCircle(color, 2.dp.toPx(), Offset(x(segment[0].first), y(segment[0].second)))
                else {
                    val path = Path().apply { segment.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.first), y(p.second)) else lineTo(x(p.first), y(p.second)) } }
                    drawPath(path, color, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        selected?.takeIf { it.seconds in start..end }?.let { drawCircle(textColor, 4.dp.toPx(), Offset(x(it.seconds), y(it.errorM))) }
    }
    selected?.let { Text("선택 ${clock(it.seconds)} · 실제 ${cm(it.errorM)} · reported ${cm(it.reportedM)} · rolling95 ${cm(it.rolling95M)}") }
    if (visible.isEmpty()) Text("표시할 측정 표본이 없습니다.")
    Text("3초 초과 수신 공백은 선을 끊습니다. 긴 그래프는 구간별 최소·최대를 보존해 그리며 통계/저장 데이터는 줄이지 않습니다.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ScatterPlot(data: LiveAnalysis) {
    Text("GT 중심 위치 분포 · 동(E) / 북(N)", style = MaterialTheme.typography.titleMedium)
    val points = data.points
    val extent = max(.1, (points.maxOfOrNull { it.errorM } ?: 1.0) * 1.12)
    // At most 2000 drawing primitives; explicitly label sampling and always retain the largest error.
    val drawn = remember(points) {
        if (points.size <= 2000) points else points.filterIndexed { i, _ -> i % ceil(points.size / 1998.0).toInt() == 0 } + listOf(points.maxBy { it.errorM }, points.last())
    }
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "GT 중심 산점도. 동일 축척, 축 범위 양방향 ${cm(extent)}, ${drawn.size}점 표시. 중심 십자가 Ground Truth." }) {
        val c = center
        val radius = min(size.width, size.height) / 2 - 34.dp.toPx()
        fun offset(p: ErrorPoint) = Offset(c.x + (p.eastM / extent * radius).toFloat(), c.y - (p.northM / extent * radius).toFloat())
        drawLine(gridColor, Offset(c.x - radius, c.y), Offset(c.x + radius, c.y))
        drawLine(gridColor, Offset(c.x, c.y - radius), Offset(c.x, c.y + radius))
        drawCircle(gridColor, radius, c, style = Stroke(1.dp.toPx()))
        data.statistics?.let { drawCircle(RollingColor, (it.cep95M / extent * radius).toFloat(), c, style = Stroke(2.dp.toPx())) }
        drawn.forEach { drawCircle(ActualColor.copy(alpha = .38f), 2.dp.toPx(), offset(it)) }
        points.lastOrNull()?.let { drawCircle(ReportedColor, 4.dp.toPx(), offset(it)) }
        drawLine(textColor, c - Offset(6.dp.toPx(), 0f), c + Offset(6.dp.toPx(), 0f), 2.dp.toPx())
        drawLine(textColor, c - Offset(0f, 6.dp.toPx()), c + Offset(0f, 6.dp.toPx()), 2.dp.toPx())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor.toArgb(); textSize = 12.sp.toPx() }
        drawContext.canvas.nativeCanvas.drawText("N", c.x - 5.dp.toPx(), c.y - radius - 8.dp.toPx(), paint)
        drawContext.canvas.nativeCanvas.drawText("E", c.x + radius + 8.dp.toPx(), c.y + 4.dp.toPx(), paint)
    }
    Text("중심 + = GT · 빨강 = 관측 · 파랑 = 마지막 위치 · 초록 원 = 누적 CEP95\n동서/남북 동일 축척, ±${cm(extent)} · ${drawn.size}/${points.size}점 표시 (통계는 전체)", style = MaterialTheme.typography.bodySmall)
}
