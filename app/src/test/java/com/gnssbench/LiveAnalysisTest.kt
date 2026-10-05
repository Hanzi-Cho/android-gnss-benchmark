package com.gnssbench

import com.gnssbench.analysis.*
import com.gnssbench.model.*
import com.gnssbench.session.BenchmarkEngine
import org.junit.Assert.*
import org.junit.Test

class LiveAnalysisTest {
    private val gt = Coordinate(0.0, 0.0)
    private fun sample(seconds: Double, error: Double, phase: Phase = Phase.Measuring): Sample {
        val time = (seconds * 1e9).toLong()
        return Sample(Fix(0, time, Coordinate(0.0, .00001), null, 5.0, null, null), time, phase, error, null)
    }
    @Test fun rollingUsesClockAndHalfOpenBoundary() {
        val a = LiveAnalyzer(gt, 0)
        a.add(sample(0.0, 100.0)); a.add(sample(30.0, 2.0)); a.add(sample(60.0, 4.0))
        val s = a.snapshot(60.0)
        assertEquals(3, s.statistics!!.count)
        assertEquals(2, s.rollingCount)
        assertEquals(3.9, s.rolling95M!!, 1e-9)
        assertNull(a.snapshot(121.0).rolling95M)
        assertEquals(61.0, a.snapshot(121.0).lastFixAgeSeconds!!, 0.0)
        assertEquals(61.0, a.snapshot(121.0).maxGapSeconds, 0.0)
    }
    @Test fun coverageCountsBinsNotSampleRateAndIncludesLeadingTrailingGaps() {
        val a = LiveAnalyzer(gt, 0)
        a.add(sample(2.1, 1.0)); a.add(sample(2.8, 2.0)); a.add(sample(7.0, 3.0))
        val s = a.snapshot(10.0)
        assertEquals(2, s.occupiedSeconds); assertEquals(10, s.totalSeconds)
        assertEquals(20.0, s.coveragePercent!!, 0.0)
        assertEquals(4.2, s.maxGapSeconds, 1e-9)
        assertEquals(3.0, s.lastFixAgeSeconds!!, 0.0)
    }
    @Test fun emptyAndWarmupDoNotInventError() {
        val a = LiveAnalyzer(gt, 0)
        a.add(sample(0.0, 20.0, Phase.WarmingUp))
        val s = a.snapshot(30.0)
        assertNull(s.statistics); assertNull(s.rolling95M); assertNull(s.lastFixAgeSeconds)
        assertEquals(30.0, s.maxGapSeconds, 0.0)
        assertEquals(0.0, s.coveragePercent!!, 0.0)
        assertNull(a.snapshot(0.0).coveragePercent)
    }
    @Test fun scatterAxesAndAntimeridianPreserveDistance() {
        val east = offsetM(gt, Coordinate(0.0, .001))
        assertTrue(east.first > 100); assertEquals(0.0, east.second, 1e-8)
        val north = offsetM(gt, Coordinate(.001, 0.0))
        assertEquals(0.0, north.first, 1e-8); assertTrue(north.second > 100)
        val origin = Coordinate(0.0, 179.999)
        val other = Coordinate(0.0, -179.999)
        val offset = offsetM(origin, other)
        assertTrue(offset.first > 0)
        assertEquals(Geodesy.distanceM(origin, other), kotlin.math.hypot(offset.first, offset.second), 1e-8)
    }
    @Test fun chartKeepsSpikesAndReceptionGaps() {
        val points = (0..1000).map { it.toDouble() to if (it == 503) 999.0 else 1.0 } + listOf(2000.0 to 2.0)
        val segments = chartSegments(points, 80)
        assertEquals(2, segments.size)
        assertTrue(segments[0].size <= 84)
        assertTrue(segments[0].any { it.second == 999.0 })
        assertEquals(0.0, segments[0].first().first, 0.0)
        assertEquals(1000.0, segments[0].last().first, 0.0)
        assertEquals(2000.0, segments[1].single().first, 0.0)
    }
    @Test fun thirtyMinuteSyntheticReplayAndStopFreeze() {
        val e = BenchmarkEngine(Config(gt, 30, 0), 0)
        for (second in 0 until 1800) {
            val t = second * 1_000_000_000L
            e.accept(Fix(second * 1000L, t, Coordinate(0.0, .00001), null, 3.0, null, null), t, null)
        }
        e.tick(e.deadline)
        val report = e.analysis(e.deadline)
        assertEquals(1800, report.statistics!!.count)
        assertEquals(100.0, report.coveragePercent!!, 0.0)
        assertEquals(1.0, report.maxGapSeconds, 0.0)
        assertEquals(111.195, report.statistics.cep95M * 100, .001)
        val partial = BenchmarkEngine(Config(gt, 30, 0), 0)
        partial.stop(now = 10_000_000_000L)
        assertEquals(10.0, partial.analysis(100_000_000_000L).elapsedSeconds, 0.0)
    }
    @Test fun publishedSeriesIsImmutableAcrossNewSamples() {
        val a = LiveAnalyzer(gt, 0)
        a.add(sample(0.0, 1.0)); val old = a.snapshot(1.0)
        a.add(sample(1.0, 2.0)); val newer = a.snapshot(2.0)
        assertEquals(1, old.points.size); assertEquals(2, newer.points.size)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidGtUncertaintyRejected() { Config(gt, groundTruthUncertaintyM = Double.NaN) }
}
