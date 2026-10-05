package com.gnssbench

import com.gnssbench.analysis.*
import com.gnssbench.model.*
import com.gnssbench.session.BenchmarkEngine
import org.junit.Assert.*
import org.junit.Test

class AnalysisTest {
    @Test fun distanceKnownArcAndSymmetry() {
        val a = Coordinate(0.0, 0.0); val b = Coordinate(0.0, 1.0)
        assertEquals(111195.0802335, Geodesy.distanceM(a, b), 0.0001)
        assertEquals(Geodesy.distanceM(a, b), Geodesy.distanceM(b, a), 0.0)
        assertEquals(0.0, Geodesy.distanceM(a, a), 0.0)
        assertEquals(22239.016, Geodesy.distanceM(Coordinate(0.0, 179.9), Coordinate(0.0, -179.9)), .001)
        assertEquals(Math.PI * 6371008.8, Geodesy.distanceM(a, Coordinate(0.0, 180.0)), .001)
    }
    @Test fun invalidGroundTruth() {
        listOf("NaN" to "0", "Infinity" to "0", "91" to "0", "0" to "181", "" to "0", "abc" to "2", "-91" to "0").forEach { (a,b) -> assertNull(Coordinate.parse(a,b)) }
        assertNotNull(Coordinate.parse(" -90 ", "-180"))
    }
    @Test fun emptySamples() { assertNull(StatisticsEngine.calculate(emptyList())); assertNull(StatisticsEngine.percentile(emptyList(), .95)) }
    @Test fun singleSample() {
        val s = StatisticsEngine.calculate(listOf(2.0))!!
        assertEquals(2.0, s.meanM, 0.0); assertEquals(2.0, s.rmseM, 0.0)
        assertEquals(2.0, s.cep50M, 0.0); assertEquals(2.0, s.cep95M, 0.0)
        assertEquals(0.0, s.withinPercent[1]!!, 0.0); assertEquals(100.0, s.withinPercent[2]!!, 0.0)
    }
    @Test fun r7AndRmseAndInclusiveThresholds() {
        val s = StatisticsEngine.calculate(listOf(10.0, 0.0, 5.0, 1.0, 3.0, 2.0))!!
        assertEquals(3.5, s.meanM, 1e-9)
        assertEquals(2.5, s.medianM, 1e-9); assertEquals(2.5, s.cep50M, 1e-9)
        assertEquals(3.8, s.cep68M, 1e-9); assertEquals(8.75, s.cep95M, 1e-9)
        assertEquals(9.75, s.cep99M, 1e-9)
        assertEquals(kotlin.math.sqrt(139.0 / 6), s.rmseM, 1e-9)
        assertEquals(10.0, s.maxM, 0.0)
        assertEquals(100.0 / 3, s.withinPercent[1]!!, 1e-9)
        assertEquals(50.0, s.withinPercent[2]!!, 1e-9)
        assertEquals(200.0 / 3, s.withinPercent[3]!!, 1e-9)
        assertEquals(500.0 / 6, s.withinPercent[5]!!, 1e-9)
        assertEquals(100.0, s.withinPercent[10]!!, 0.0)
        assertEquals(0.0, StatisticsEngine.percentile(listOf(1.0, 0.0), 0.0)!!, 0.0)
        assertEquals(1.0, StatisticsEngine.percentile(listOf(1.0, 0.0), 1.0)!!, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidPercentile() { StatisticsEngine.percentile(listOf(1.0), 1.1) }
    @Test(expected = IllegalArgumentException::class) fun invalidError() { StatisticsEngine.calculate(listOf(Double.NaN)) }
    private val gt = Coordinate(37.0, 127.0)
    private fun fix(nanos: Long, mock: Boolean = false) = Fix(1234, nanos, gt, null, null, null, null, mock)
    @Test fun warmupAndHalfOpenWindow() {
        val e = BenchmarkEngine(Config(gt, 5, 300), 100)
        assertEquals(Phase.WarmingUp, e.phase)
        assertEquals(Phase.WarmingUp, e.accept(fix(101), 101, null)!!.phase)
        assertNull(e.statistics)
        e.tick(e.measurementStart)
        assertEquals(Phase.Measuring, e.phase)
        assertEquals(Phase.Measuring, e.accept(fix(e.measurementStart), e.measurementStart, null)!!.phase)
        assertEquals(1, e.sampleCount)
        assertNull(e.accept(fix(e.deadline), e.deadline, null))
        e.tick(e.deadline)
        assertEquals(Phase.Completed, e.phase)
    }
    @Test fun rejectsCachedDuplicateFutureAndMock() {
        val e = BenchmarkEngine(Config(gt, 5, 0), 100)
        assertNull(e.accept(fix(99), 101, null))
        assertNotNull(e.accept(fix(101), 101, null))
        assertNull(e.accept(fix(101), 102, null))
        assertNull(e.accept(fix(103), 102, null))
        assertNull(e.accept(fix(104, true), 104, null))
        assertNull(e.accept(fix(105), 6_000_000_105, null))
        assertEquals(5, e.rejected)
        assertEquals(1, e.sampleCount)
    }
    @Test fun staleSatelliteIsUnavailable() {
        val e = BenchmarkEngine(Config(gt, 5, 0), 0)
        assertNull(e.accept(fix(6_000_000_001), 6_000_000_001, SatelliteSnapshot(0, emptyList()))!!.satellites)
    }
    @Test fun noFixStillCompletesAndStopIsPartial() {
        val e = BenchmarkEngine(Config(gt, 60, 300), 0)
        e.tick(e.deadline)
        assertEquals(Phase.Completed, e.phase); assertNull(e.statistics)
        val stopped = BenchmarkEngine(Config(gt, 15, 0), 0)
        stopped.stop(); stopped.tick(stopped.deadline)
        assertEquals(Phase.Completed, stopped.phase)
        assertNull(stopped.accept(fix(10), 10, null))
    }
    @Test fun errorIsTerminal() {
        val e = BenchmarkEngine(Config(gt, 30, 0), 0)
        e.stop(true); e.tick(e.deadline)
        assertEquals(Phase.Error, e.phase)
    }
}
