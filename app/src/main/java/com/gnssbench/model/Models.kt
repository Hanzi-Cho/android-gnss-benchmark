package com.gnssbench.model

data class Coordinate(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) { "유효한 WGS84 좌표를 입력하세요." } }
    companion object {
        fun parse(lat: String, lon: String): Coordinate? = runCatching { Coordinate(lat.trim().toDouble(), lon.trim().toDouble()) }.getOrNull()
    }
}
data class Satellite(val svid: Int, val constellation: String, val cn0DbHz: Double, val used: Boolean, val carrierHz: Double?, val elevationDeg: Double, val azimuthDeg: Double)
data class SatelliteSnapshot(val elapsedNanos: Long, val satellites: List<Satellite>) {
    val count get() = satellites.size
    val used get() = satellites.count { it.used }
    val meanCn0 get() = satellites.map { it.cn0DbHz }.takeIf { it.isNotEmpty() }?.average()
    val strongestCn0 get() = satellites.maxOfOrNull { it.cn0DbHz }
}
data class Fix(val wallTimeMillis: Long, val elapsedNanos: Long, val coordinate: Coordinate, val altitudeM: Double?, val accuracyM: Double?, val speedMps: Double?, val bearingDeg: Double?, val mock: Boolean = false)
data class Sample(val fix: Fix, val receivedNanos: Long, val phase: Phase, val errorM: Double, val satellites: SatelliteSnapshot?)
enum class Phase { Idle, WarmingUp, Measuring, Completed, Error }
data class Config(val groundTruth: Coordinate, val durationMinutes: Int = 30, val warmupSeconds: Int = 300) {
    init { require(durationMinutes in listOf(5, 15, 30, 60)); require(warmupSeconds in listOf(0, 300)) }
    val warmupNanos get() = warmupSeconds * 1_000_000_000L
    val measurementNanos get() = durationMinutes * 60_000_000_000L
}
