package com.gnssbench.analysis

import com.gnssbench.model.Coordinate
import kotlin.math.*

object Geodesy {
    fun distanceM(a: Coordinate, b: Coordinate): Double {
        val lat1 = a.latitude * PI / 180
        val lat2 = b.latitude * PI / 180
        val dlat = lat2 - lat1
        val dlon = (b.longitude - a.longitude) * PI / 180
        val h = (sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)).coerceIn(0.0, 1.0)
        return 2 * 6_371_008.8 * atan2(sqrt(h), sqrt(1 - h))
    }
}
data class Statistics(val count: Int, val meanM: Double, val medianM: Double, val rmseM: Double, val cep50M: Double, val cep68M: Double, val cep95M: Double, val cep99M: Double, val maxM: Double, val withinPercent: Map<Int, Double>)
object StatisticsEngine {
    /** R7: linear interpolation at (n-1)*p. Input need not be sorted. */
    fun percentile(values: List<Double>, p: Double): Double? {
        require(p.isFinite() && p in 0.0..1.0)
        require(values.all { it.isFinite() && it >= 0 })
        if (values.isEmpty()) return null
        val s = values.sorted()
        return sortedPercentile(s, p)
    }
    private fun sortedPercentile(s: List<Double>, p: Double): Double {
        val h = (s.size - 1) * p
        val lo = floor(h).toInt()
        return s[lo] + (s[ceil(h).toInt()] - s[lo]) * (h - lo)
    }
    fun calculate(errors: List<Double>): Statistics? {
        require(errors.all { it.isFinite() && it >= 0 })
        if (errors.isEmpty()) return null
        val s = errors.sorted()
        fun p(v: Double) = sortedPercentile(s, v)
        return Statistics(s.size, s.average(), p(.5), sqrt(s.sumOf { it * it } / s.size), p(.5), p(.68), p(.95), p(.99), s.last(), listOf(1, 2, 3, 5, 10).associateWith { r -> 100.0 * s.count { it <= r } / s.size })
    }
}
