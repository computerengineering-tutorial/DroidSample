package com.example.droidsample.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestResultModelTest {

    @Test
    fun testTestSuiteReportCalculations() {
        val results = listOf(
            FeatureTestResult(
                featureId = "TEST-01",
                apiName = "Camera Test",
                category = "Camera",
                status = TestStatus.PASS,
                outputData = "Success"
            ),
            FeatureTestResult(
                featureId = "TEST-02",
                apiName = "Audio Test",
                category = "Audio",
                status = TestStatus.FAIL,
                outputData = "Audio failed",
                errorMessage = "Device not ready"
            ),
            FeatureTestResult(
                featureId = "TEST-03",
                apiName = "SMS Test",
                category = "SMS",
                status = TestStatus.BLOCKED,
                outputData = "Permission needed"
            ),
            FeatureTestResult(
                featureId = "TEST-04",
                apiName = "NFC Test",
                category = "Connectivity",
                status = TestStatus.UNSUPPORTED,
                outputData = "No hardware"
            )
        )

        val total = results.size
        val pass = results.count { it.status == TestStatus.PASS }
        val fail = results.count { it.status == TestStatus.FAIL }
        val blocked = results.count { it.status == TestStatus.BLOCKED }
        val unsupported = results.count { it.status == TestStatus.UNSUPPORTED }
        val passRate = (pass.toDouble() / total.toDouble()) * 100.0

        val report = TestSuiteReport(
            results = results,
            totalTests = total,
            passCount = pass,
            failCount = fail,
            blockedCount = blocked,
            unsupportedCount = unsupported,
            passRatePercent = passRate,
            deviceModel = "Pixel 8 Pro",
            androidVersion = "Android 15 (API 35)"
        )

        assertEquals(4, report.totalTests)
        assertEquals(1, report.passCount)
        assertEquals(1, report.failCount)
        assertEquals(1, report.blockedCount)
        assertEquals(1, report.unsupportedCount)
        assertEquals(25.0, report.passRatePercent, 0.01)
        assertEquals("Pixel 8 Pro", report.deviceModel)
    }

    @Test
    fun testExportToJsonContainsRequiredFields() {
        val result = FeatureTestResult(
            featureId = "CAM-01",
            apiName = "CameraManager.cameraIdList",
            category = "Camera",
            status = TestStatus.PASS,
            outputData = "Found [0, 1] cameras"
        )
        val report = TestSuiteReport(
            results = listOf(result),
            totalTests = 1,
            passCount = 1,
            failCount = 0,
            blockedCount = 0,
            unsupportedCount = 0,
            passRatePercent = 100.0,
            deviceModel = "TestDevice",
            androidVersion = "Android 14"
        )

        val json = TestSuiteRunner.exportToJson(report)

        assertTrue(json.contains("\"totalTests\": 1"))
        assertTrue(json.contains("\"passCount\": 1"))
        assertTrue(json.contains("\"passRatePercent\": 100.00"))
        assertTrue(json.contains("\"featureId\": \"CAM-01\""))
        assertTrue(json.contains("\"status\": \"PASS\""))
        assertTrue(json.contains("\"outputData\": \"Found [0, 1] cameras\""))
    }

    @Test
    fun testExportToMarkdownContainsTableHeadersAndRows() {
        val result = FeatureTestResult(
            featureId = "NET-01",
            apiName = "ConnectivityManager",
            category = "Network",
            status = TestStatus.PASS,
            outputData = "Connected to Wi-Fi"
        )
        val report = TestSuiteReport(
            results = listOf(result),
            totalTests = 1,
            passCount = 1,
            failCount = 0,
            blockedCount = 0,
            unsupportedCount = 0,
            passRatePercent = 100.0,
            deviceModel = "TestPhone",
            androidVersion = "Android 14"
        )

        val markdown = TestSuiteRunner.exportToMarkdown(report)

        assertTrue(markdown.contains("# DroidSample Master API Diagnostic Report"))
        assertTrue(markdown.contains("| ID | Category | API / Feature | Status | Details |"))
        assertTrue(markdown.contains("`NET-01`"))
        assertTrue(markdown.contains("Network"))
        assertTrue(markdown.contains("✅ PASS"))
        assertTrue(markdown.contains("Connected to Wi-Fi"))
    }
}
