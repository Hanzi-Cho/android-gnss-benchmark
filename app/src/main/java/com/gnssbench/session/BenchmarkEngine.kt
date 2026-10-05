package com.gnssbench.session

import com.gnssbench.analysis.Geodesy
import com.gnssbench.analysis.StatisticsEngine
import com.gnssbench.analysis.LiveAnalyzer
import com.gnssbench.model.*

class BenchmarkEngine(val config: Config, val startNanos: Long) {
    var phase = if (config.warmupSeconds == 0) Phase.Measuring else Phase.WarmingUp
        private set
    val measurementStart = startNanos + config.warmupNanos
    val deadline = measurementStart + config.measurementNanos
    private val analyzer = LiveAnalyzer(config.groundTruth, measurementStart)
    private var stoppedNanos: Long? = null
    fun analysis(now: Long) = analyzer.snapshot(((stoppedNanos ?: now).coerceAtMost(deadline) - measurementStart).coerceAtLeast(0) / 1e9)
    private var lastFixNanos = Long.MIN_VALUE
    private val errors = mutableListOf<Double>()
    var rejected = 0
        private set
    val active get() = phase == Phase.WarmingUp || phase == Phase.Measuring
    val statistics get() = StatisticsEngine.calculate(errors)
    val sampleCount get() = errors.size
    fun tick(now: Long) {
        if (!active) return
        phase = when { now >= deadline -> Phase.Completed; now >= measurementStart -> Phase.Measuring; else -> Phase.WarmingUp }
    }
    fun accept(fix: Fix, received: Long, satellites: SatelliteSnapshot?): Sample? {
        if (!active) return null
        if (fix.mock || fix.elapsedNanos < startNanos || fix.elapsedNanos >= deadline || fix.elapsedNanos <= lastFixNanos || fix.elapsedNanos > received || received - fix.elapsedNanos > 5_000_000_000L) {
            rejected++; return null
        }
        lastFixNanos = fix.elapsedNanos
        val samplePhase = if (fix.elapsedNanos < measurementStart) Phase.WarmingUp else Phase.Measuring
        val error = Geodesy.distanceM(config.groundTruth, fix.coordinate)
        if (samplePhase == Phase.Measuring) errors += error
        val freshSat = satellites?.takeIf { received - it.elapsedNanos in 0..5_000_000_000L }
        return Sample(fix, received, samplePhase, error, freshSat).also { analyzer.add(it) }
    }
    fun stop(error: Boolean = false, now: Long? = null) { if (active) { stoppedNanos = now; phase = if (error) Phase.Error else Phase.Completed } }
}
