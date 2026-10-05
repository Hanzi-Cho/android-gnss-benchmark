package com.gnssbench.analysis

import com.gnssbench.model.Coordinate
import com.gnssbench.model.Sample
import com.gnssbench.model.Phase
import kotlin.math.*

data class ErrorPoint(val seconds: Double, val errorM: Double, val reportedM: Double?, val eastM: Double, val northM: Double, val rolling95M: Double?)
data class LiveAnalysis(
    val points: List<ErrorPoint> = emptyList(), val statistics: Statistics? = null,
    val rolling95M: Double? = null, val rollingCount: Int = 0,
    val elapsedSeconds: Double = 0.0, val occupiedSeconds: Int = 0, val totalSeconds: Int = 0,
    val coveragePercent: Double? = null, val lastFixAgeSeconds: Double? = null,
    val maxGapSeconds: Double = 0.0, val withinCep95Percent: Double? = null,
)

/** Same mean-radius spherical model as distanceM; azimuthal equidistant GT-centered plot. */
fun offsetM(gt: Coordinate, position: Coordinate): Pair<Double, Double> {
    val lat1 = gt.latitude * PI / 180
    val lat2 = position.latitude * PI / 180
    val dlon = (position.longitude - gt.longitude) * PI / 180
    val bearing = atan2(sin(dlon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dlon))
    val distance = Geodesy.distanceM(gt, position)
    return distance * sin(bearing) to distance * cos(bearing)
}

class LiveAnalyzer(private val groundTruth: Coordinate, private val measurementStartNanos: Long) {
    private val points = mutableListOf<ErrorPoint>()
    private var published: List<ErrorPoint> = emptyList()
    private var stats: Statistics? = null
    private var includedPercent: Double? = null
    private var dirty = false
    private var maxInternalGap = 0.0
    private val occupied = mutableSetOf<Int>()

    fun add(sample: Sample) {
        if (sample.phase != Phase.Measuring) return
        val seconds = (sample.fix.elapsedNanos - measurementStartNanos) / 1e9
        require(seconds >= 0 && (points.isEmpty() || seconds > points.last().seconds))
        val (east, north) = offsetM(groundTruth, sample.fix.coordinate)
        val rollingErrors = points.asReversed().takeWhile { it.seconds > seconds - 60 }.map { it.errorM } + sample.errorM
        val rolling = StatisticsEngine.percentile(rollingErrors, .95)
        maxInternalGap = max(maxInternalGap, seconds - (points.lastOrNull()?.seconds ?: 0.0))
        occupied += floor(seconds).toInt()
        points += ErrorPoint(seconds, sample.errorM, sample.fix.accuracyM, east, north, rolling)
        dirty = true
    }

    fun snapshot(elapsedSeconds: Double): LiveAnalysis {
        val elapsed = elapsedSeconds.coerceAtLeast(0.0)
        if (dirty) {
            published = points.toList()
            stats = StatisticsEngine.calculate(points.map { it.errorM })
            includedPercent = stats?.let { s -> points.count { it.errorM <= s.cep95M } * 100.0 / points.size }
            dirty = false
        }
        val recent = points.asReversed().takeWhile { it.seconds > elapsed - 60 }.filter { it.seconds <= elapsed }
        val total = ceil(elapsed).toInt()
        val occupiedCount = occupied.count { it < total }
        val age = points.lastOrNull()?.let { (elapsed - it.seconds).coerceAtLeast(0.0) }
        return LiveAnalysis(published, stats, StatisticsEngine.percentile(recent.map { it.errorM }, .95), recent.size,
            elapsed, occupiedCount, total, if (total == 0) null else occupiedCount * 100.0 / total,
            age, max(maxInternalGap, age ?: elapsed), includedPercent)
    }
}

/** Pixel-budget reduction keeps local extrema and never joins a >3s reception gap. */
fun chartSegments(points: List<Pair<Double, Double>>, budget: Int = 600): List<List<Pair<Double, Double>>> {
    require(budget >= 4)
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<Pair<Double, Double>>>()
    points.forEach { point ->
        if (segments.isEmpty() || point.first - segments.last().last().first > 3) segments.add(mutableListOf())
        segments.last() += point
    }
    return segments.map { segment ->
        if (segment.size <= budget) segment else {
            val chunkSize = ceil(segment.size / (budget / 4.0)).toInt()
            segment.chunked(chunkSize).flatMap { chunk -> listOf(chunk.first(), chunk.minBy { it.second }, chunk.maxBy { it.second }, chunk.last()).distinct().sortedBy { it.first } }
        }
    }
}
