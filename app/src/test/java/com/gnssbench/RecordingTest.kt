package com.gnssbench

import com.gnssbench.recording.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class RecordingTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun jsonPreservesNanosecondsAndNulls() {
        val file = temporary.newFile()
        Recording.append(file, "metadata", json("model" to "TC\"test,\nname"))
        Recording.append(file, "sample", json("elapsedRealtimeNanos" to 912345678901234567L, "reportedHorizontalAccuracyM" to null))
        Recording.append(file, "raw", json("clock" to json("timeNanos" to 999999999999999999L)))
        Recording.append(file, "summary", json("status" to "Completed"))
        val out = ByteArrayOutputStream()
        Recording.export(file, out, false)
        val result = JSONObject(out.toString("UTF-8"))
        assertEquals("TC\"test,\nname", result.getJSONArray("metadata").getJSONObject(0).getString("model"))
        val sample = result.getJSONArray("samples").getJSONObject(0)
        assertEquals(912345678901234567L, sample.getLong("elapsedRealtimeNanos"))
        assertTrue(sample.isNull("reportedHorizontalAccuracyM"))
        assertEquals(1, result.getJSONArray("rawObservations").length())
        assertEquals("Completed", result.getJSONArray("summary").getJSONObject(0).getString("status"))
    }
    @Test fun interruptedAndTornRecordAreExplicit() {
        val file = temporary.newFile()
        Recording.append(file, "metadata", json("model" to "test"))
        file.appendText("{broken")
        val out = ByteArrayOutputStream()
        Recording.export(file, out, false)
        val result = JSONObject(out.toString("UTF-8"))
        assertEquals("Interrupted", result.getJSONArray("summary").getJSONObject(0).getString("status"))
        assertEquals(1, result.getJSONArray("recoveryWarnings").length())
        assertEquals(0, result.getJSONArray("samples").length())
    }
    @Test fun csvCarriesPayloadAndEscapesQuotes() {
        val file = temporary.newFile()
        Recording.append(file, "metadata", json("model" to "A,B\"C"))
        val out = ByteArrayOutputStream()
        Recording.export(file, out, true)
        val csv = out.toString("UTF-8")
        assertTrue(csv.startsWith("record_type,wallTimeMillis,"))
        assertTrue(csv.contains("payload_json"))
        assertTrue(csv.contains("\"\"model\"\""))
        assertTrue(csv.contains("Interrupted"))
    }
    @Test fun nonFiniteSensorNumbersAreUnavailable() {
        val value = json("nan" to Double.NaN, "infinity" to Float.POSITIVE_INFINITY, "zero" to 0.0)
        assertTrue(value.isNull("nan")); assertTrue(value.isNull("infinity")); assertEquals(0.0, value.getDouble("zero"), 0.0)
    }
}
