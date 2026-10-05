package com.gnssbench

import com.gnssbench.recording.*
import com.gnssbench.model.Phase
import org.junit.Assert.*
import org.junit.Test

class SessionReplayTest {
    private fun replay() = SessionReplay().apply {
        record("metadata", json("schemaVersion" to 1, "provider" to "gps", "measurementDurationSeconds" to 1800, "warmupSeconds" to 0,
            "startElapsedRealtimeNanos" to 0, "groundTruth" to json("latitude" to 0.0, "longitude" to 0.0)))
    }
    private fun point(time: Long = 0) = json("phase" to "Measuring", "wallTimeMillis" to 0, "elapsedRealtimeNanos" to time, "latitude" to 0.0, "longitude" to .00001, "measuredHorizontalErrorM" to 0.0)
    @Test fun recomputesInsteadOfTrustingExportedDerivedError() {
        val r = replay(); r.record("sample", point())
        r.record("summary", json("status" to "Completed", "endElapsedRealtimeNanos" to 1_800_000_000_000L))
        val report = r.finish()
        assertEquals(Phase.Completed, report.phase)
        assertTrue(report.analysis.statistics!!.cep95M > 1.0)
        assertEquals(1800.0, report.analysis.elapsedSeconds, 0.0)
    }
    @Test fun missingSummaryShowsInterrupted() {
        val r = replay(); r.record("sample", point(10_000_000_000))
        val report = r.finish()
        assertEquals(Phase.Error, report.phase)
        assertTrue(report.note.contains("Interrupted"))
        assertEquals(10.0, report.analysis.elapsedSeconds, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateTimestampRejected() {
        val r = replay(); r.record("sample", point()); r.record("sample", point()); r.finish()
    }
    @Test fun corruptJournalCannotLookNormallyCompleted() {
        val r = replay(); r.record("sample", point()); r.record("recoveryWarning", json())
        r.record("summary", json("status" to "Completed", "endElapsedRealtimeNanos" to 1_800_000_000_000L))
        assertEquals(Phase.Error, r.finish().phase)
    }
}
