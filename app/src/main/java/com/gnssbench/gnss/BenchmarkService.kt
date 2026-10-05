package com.gnssbench.gnss

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.gnssbench.BuildConfig
import com.gnssbench.analysis.Statistics
import com.gnssbench.model.*
import com.gnssbench.recording.*
import com.gnssbench.session.BenchmarkEngine
import com.gnssbench.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

data class BenchmarkState(val phase: Phase = Phase.Idle, val config: Config? = null, val elapsedSeconds: Long = 0, val remainingSeconds: Long = 0,
    val measurementElapsedSeconds: Long = 0, val latest: Sample? = null, val satellites: SatelliteSnapshot? = null, val statistics: Statistics? = null,
    val rejected: Int = 0, val rawEvents: Int = 0, val message: String = "정확한 위치 권한을 허용한 뒤 시작하세요.", val hardwareModel: String = "미보고", val hardwareYear: String = "미보고", val providerEnabled: Boolean = false, val savedFile: String? = null) {
    val active get() = phase == Phase.WarmingUp || phase == Phase.Measuring
}

class BenchmarkService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: BenchmarkEngine? = null
    private var collector: GnssCollector? = null
    private var journal: File? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var finishing = false
    @Volatile private var recordingError: String? = null
    private val ticker = object : Runnable {
        override fun run() {
            val e = engine ?: return
            if (!e.active) return
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { finish("Precise location 권한이 취소되었습니다.", true); return }
            e.tick(SystemClock.elapsedRealtimeNanos())
            update()
            if (!e.active) finish(if (e.sampleCount == 0) "측정 완료: 유효한 GNSS fix가 없습니다." else "측정 완료")
            else handler.postDelayed(this, 1000)
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish("사용자가 중단한 partial session"); return START_NOT_STICKY }
        if (engine?.active == true) return START_NOT_STICKY
        try {
            require(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { "정확한 위치 권한이 필요합니다. 앱 설정 > 권한 > 위치 > 정확한 위치를 허용하세요." }
            val config = Config(Coordinate(intent!!.getDoubleExtra("lat", Double.NaN), intent.getDoubleExtra("lon", Double.NaN)), intent.getIntExtra("minutes", 30), if (BuildConfig.DEBUG && intent.getBooleanExtra("skip", false)) 0 else 300)
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            finishing = false
            recordingError = null
            val now = SystemClock.elapsedRealtimeNanos()
            val wallStart = System.currentTimeMillis()
            engine = BenchmarkEngine(config, now)
            mutableState.value = BenchmarkState(phase = engine!!.phase, config = config, message = "GNSS fix 대기 중; raw observation 미수신", remainingSeconds = config.warmupSeconds + config.durationMinutes * 60L)
            Recording.io.execute {
                try {
                    val file = Recording.create(this, config, wallStart, now)
                    handler.post {
                        if (finishing) return@post
                        journal = file
                        mutableState.value = mutableState.value.copy(savedFile = file.absolutePath)
                        beginCollection()
                    }
                } catch (e: Exception) { handler.post { finish("저장 시작 실패: ${e.message}", true) } }
            }
        } catch (e: Exception) { finish("시작 실패: ${e.message}", true) }
        return START_NOT_STICKY
    }
    private fun beginCollection() {
        try {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gnssbench:measurement").apply { acquire(70 * 60 * 1000L) }
            collector = GnssCollector(this,
                onFix = { fix ->
                    val e = engine!!
                    val now = SystemClock.elapsedRealtimeNanos()
                    val sample = e.accept(fix, now, mutableState.value.satellites)
                    if (sample != null) { write("sample", sample.toJson()); mutableState.value = mutableState.value.copy(latest = sample) }
                    update()
                },
                onSatellites = { s -> if (engine?.active == true) { mutableState.value = mutableState.value.copy(satellites = s); write("satellites", s.toJson()) } },
                onRaw = { raw -> if (engine?.active == true) { write("raw", raw); mutableState.value = mutableState.value.copy(rawEvents = mutableState.value.rawEvents + 1) } },
                onEvent = { message -> if (engine?.active == true) { mutableState.value = mutableState.value.copy(message = message); write("event", json("elapsedRealtimeNanos" to SystemClock.elapsedRealtimeNanos(), "message" to message)) } },
                onFailure = { finish(it, true) })
            mutableState.value = mutableState.value.copy(hardwareModel = collector!!.hardwareModel, hardwareYear = collector!!.hardwareYear, providerEnabled = collector!!.providerEnabled)
            write("event", json("elapsedRealtimeNanos" to SystemClock.elapsedRealtimeNanos(), "source" to "DETECTED", "gnssHardwareModel" to collector!!.hardwareModel, "gnssHardwareYear" to collector!!.hardwareYear))
            collector!!.start()
            handler.post(ticker)
        } catch (e: Exception) { finish("GNSS 수집 실패: ${e.message}", true) }
    }
    private fun update() {
        val e = engine ?: return
        val now = SystemClock.elapsedRealtimeNanos()
        mutableState.value = mutableState.value.copy(phase = e.phase, elapsedSeconds = ((now - e.startNanos) / 1_000_000_000).coerceAtLeast(0),
            remainingSeconds = ((e.deadline - now + 999_999_999) / 1_000_000_000).coerceAtLeast(0),
            measurementElapsedSeconds = ((now - e.measurementStart).coerceIn(0, e.config.measurementNanos) / 1_000_000_000), statistics = e.statistics, rejected = e.rejected)
    }
    private fun write(type: String, payload: JSONObject) {
        val file = journal ?: return
        Recording.io.execute {
            try {
                if (type == "summary" && recordingError != null) {
                    payload.put("status", "Error").put("reason", recordingError).put("recordingIncomplete", true)
                }
                Recording.append(file, type, payload)
            } catch (e: Exception) {
                val message = "저장 실패: ${e.message}. 기록이 불완전할 수 있습니다."
                recordingError = message
                handler.post {
                    if (!finishing) finish(message, true)
                    else mutableState.value = mutableState.value.copy(phase = Phase.Error, message = message)
                }
            }
        }
    }
    private fun finish(message: String, error: Boolean = false) {
        if (finishing) return
        finishing = true
        val e = engine
        e?.stop(error)
        handler.removeCallbacks(ticker)
        runCatching { collector?.stop() }
        collector = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        update()
        mutableState.value = mutableState.value.copy(phase = if (error) Phase.Error else Phase.Completed, message = message)
        write("summary", json("status" to mutableState.value.phase.name, "reason" to message, "testEndWallTimeMillis" to System.currentTimeMillis(),
            "endElapsedRealtimeNanos" to SystemClock.elapsedRealtimeNanos(), "measurementElapsedSeconds" to mutableState.value.measurementElapsedSeconds,
            "rejectedSamples" to e?.rejected, "rawEventCount" to mutableState.value.rawEvents, "statistics" to e?.statistics?.toJson()))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("benchmark", "GNSS measurement", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, BenchmarkService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "benchmark").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("GNSS Benchmark 측정 중")
            .setContentText("Warm-up / 측정 데이터를 저장하고 있습니다.").setContentIntent(open).setOngoing(true).addAction(0, "Stop", stop).build()
    }
    override fun onDestroy() {
        if (engine?.active == true) finish("Service destroyed; interrupted", true)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    companion object {
        const val ACTION_STOP = "com.gnssbench.STOP"
        private val mutableState = MutableStateFlow(BenchmarkState())
        val state = mutableState.asStateFlow()
        fun reset() { if (!mutableState.value.active) mutableState.value = BenchmarkState() }
    }
}
