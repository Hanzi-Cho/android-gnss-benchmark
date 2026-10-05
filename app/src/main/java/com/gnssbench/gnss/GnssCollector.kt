package com.gnssbench.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.*
import android.os.SystemClock
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import com.gnssbench.model.*
import com.gnssbench.recording.json
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class GnssCollector(context: Context, private val onFix: (Fix) -> Unit, private val onSatellites: (SatelliteSnapshot) -> Unit,
    private val onRaw: (JSONObject) -> Unit, private val onEvent: (String) -> Unit, private val onFailure: (String) -> Unit) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = context.mainExecutor
    val providerEnabled get() = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val hardwareModel get() = manager.gnssHardwareModelName ?: "미보고"
    val hardwareYear get() = manager.gnssYearOfHardware.takeIf { it > 0 }?.toString() ?: "미보고"
    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            val coordinate = runCatching { Coordinate(l.latitude, l.longitude) }.getOrNull()
            if (coordinate == null) { onEvent("Invalid coordinate rejected"); return }
            onFix(Fix(l.time, l.elapsedRealtimeNanos, coordinate, l.altitude.takeIf { l.hasAltitude() }, l.accuracy.toDouble().takeIf { l.hasAccuracy() }, l.speed.toDouble().takeIf { l.hasSpeed() }, l.bearing.toDouble().takeIf { l.hasBearing() }, LocationCompat.isMock(l)))
        }
        override fun onProviderDisabled(provider: String) { onFailure("GPS가 꺼졌습니다. 위치 설정을 켜고 새 세션을 시작하세요.") }
        override fun onProviderEnabled(provider: String) { onEvent("GPS provider enabled") }
    }
    private val status = object : GnssStatus.Callback() {
        override fun onStarted() { onEvent("GNSS started") }
        override fun onStopped() { onEvent("GNSS stopped") }
        override fun onFirstFix(ttffMillis: Int) { onEvent("First fix: $ttffMillis ms (callback TTFF)") }
        override fun onSatelliteStatusChanged(s: GnssStatus) {
            onSatellites(SatelliteSnapshot(SystemClock.elapsedRealtimeNanos(), (0 until s.satelliteCount).map { i ->
                Satellite(s.getSvid(i), constellation(s.getConstellationType(i)), s.getCn0DbHz(i).toDouble(), s.usedInFix(i), s.getCarrierFrequencyHz(i).toDouble().takeIf { s.hasCarrierFrequencyHz(i) }, s.getElevationDegrees(i).toDouble(), s.getAzimuthDegrees(i).toDouble())
            }))
        }
    }
    private val measurements = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(e: GnssMeasurementsEvent) {
            val c = e.clock
            onRaw(json("receivedElapsedRealtimeNanos" to SystemClock.elapsedRealtimeNanos(), "wallTimeMillis" to System.currentTimeMillis(),
                "clock" to json("timeNanos" to c.timeNanos, "fullBiasNanos" to c.fullBiasNanos.takeIf { c.hasFullBiasNanos() }, "biasNanos" to c.biasNanos.takeIf { c.hasBiasNanos() },
                    "biasUncertaintyNanos" to c.biasUncertaintyNanos.takeIf { c.hasBiasUncertaintyNanos() }, "timeUncertaintyNanos" to c.timeUncertaintyNanos.takeIf { c.hasTimeUncertaintyNanos() },
                    "driftNanosPerSecond" to c.driftNanosPerSecond.takeIf { c.hasDriftNanosPerSecond() }, "leapSecond" to c.leapSecond.takeIf { c.hasLeapSecond() },
                    "hardwareClockDiscontinuityCount" to c.hardwareClockDiscontinuityCount, "elapsedRealtimeNanos" to c.elapsedRealtimeNanos.takeIf { c.hasElapsedRealtimeNanos() }),
                "measurements" to JSONArray(e.measurements.map { m -> json("svid" to m.svid, "constellation" to constellation(m.constellationType), "state" to m.state,
                    "timeOffsetNanos" to m.timeOffsetNanos, "receivedSvTimeNanos" to m.receivedSvTimeNanos, "receivedSvTimeUncertaintyNanos" to m.receivedSvTimeUncertaintyNanos,
                    "cn0DbHz" to m.cn0DbHz, "carrierFrequencyHz" to m.carrierFrequencyHz.toDouble().takeIf { m.hasCarrierFrequencyHz() },
                    "pseudorangeRateMps" to m.pseudorangeRateMetersPerSecond, "pseudorangeRateUncertaintyMps" to m.pseudorangeRateUncertaintyMetersPerSecond,
                    "accumulatedDeltaRangeM" to m.accumulatedDeltaRangeMeters, "accumulatedDeltaRangeUncertaintyM" to m.accumulatedDeltaRangeUncertaintyMeters, "accumulatedDeltaRangeState" to m.accumulatedDeltaRangeState, "multipathIndicator" to m.multipathIndicator) })))
        }
    }
    @SuppressLint("MissingPermission") // Caller verifies precise permission; SecurityException is surfaced by service.
    fun start() {
        check(providerEnabled) { "GPS provider가 꺼져 있습니다." }
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, executor, listener)
        onEvent("Satellite callback registered: ${manager.registerGnssStatusCallback(executor, status)}")
        // Compat avoids the platform registration crash on Android R before QPR1.
        try { onEvent("Raw callback registered: ${LocationManagerCompat.registerGnssMeasurementsCallback(manager, executor, measurements)}; events may be unavailable") }
        catch (e: RuntimeException) { onEvent("Raw measurements unavailable: ${e.message}") }
    }
    fun stop() { manager.removeUpdates(listener); manager.unregisterGnssStatusCallback(status); LocationManagerCompat.unregisterGnssMeasurementsCallback(manager, measurements) }
    companion object {
        fun constellation(type: Int): String = when (type) {
            GnssStatus.CONSTELLATION_GPS -> "GPS"; GnssStatus.CONSTELLATION_SBAS -> "SBAS"; GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_QZSS -> "QZSS"; GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"; GnssStatus.CONSTELLATION_GALILEO -> "Galileo"; GnssStatus.CONSTELLATION_IRNSS -> "NavIC"; else -> "Unknown($type)"
        }
        fun band(s: Satellite): String {
            val mhz = (s.carrierHz ?: return "미보고") / 1e6
            fun near(f: Double) = abs(mhz - f) < 2
            return when {
                near(1575.42) -> when (s.constellation) { "Galileo" -> "E1"; "BeiDou" -> "B1C"; "GPS", "QZSS", "SBAS", "NavIC" -> "L1"; else -> "unclassified" }
                near(1176.45) -> when (s.constellation) { "Galileo" -> "E5a"; "BeiDou" -> "B2a"; "GPS", "QZSS", "SBAS", "NavIC" -> "L5"; else -> "unclassified" }
                near(1227.60) && s.constellation in listOf("GPS", "QZSS") -> "L2"
                near(1561.098) && s.constellation == "BeiDou" -> "B1I"
                near(1207.14) -> when (s.constellation) { "Galileo" -> "E5b"; "BeiDou" -> "B2I/B2b"; else -> "unclassified" }
                s.constellation == "GLONASS" && mhz in 1598.0..1606.0 -> "G1"
                s.constellation == "GLONASS" && mhz in 1242.0..1249.0 -> "G2"
                else -> "unclassified"
            }
        }
    }
}
