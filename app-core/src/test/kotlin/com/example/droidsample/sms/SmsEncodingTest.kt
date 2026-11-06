package com.example.droidsample.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsEncodingTest {

    @Test
    fun testEmptyMessageEncoding() {
        val info = SmsSenderHelper.calculateSmsEncoding("")
        assertFalse(info.isUnicode)
        assertEquals(0, info.characterCount)
        assertEquals(1, info.segmentCount)
        assertEquals(160, info.remainingInCurrentSegment)
        assertEquals(160, info.segmentLimit)
    }

    @Test
    fun testStandardGsmSingleSegment() {
        val text = "Hello, this is a standard GSM 7-bit test SMS message."
        val info = SmsSenderHelper.calculateSmsEncoding(text)

        assertFalse(info.isUnicode)
        assertEquals(text.length, info.characterCount)
        assertEquals(1, info.segmentCount)
        assertEquals(160 - text.length, info.remainingInCurrentSegment)
        assertEquals(160, info.segmentLimit)
    }

    @Test
    fun testGsmExactBoundary160Characters() {
        val text = "A".repeat(160)
        val info = SmsSenderHelper.calculateSmsEncoding(text)

        assertFalse(info.isUnicode)
        assertEquals(160, info.characterCount)
        assertEquals(1, info.segmentCount)
        assertEquals(0, info.remainingInCurrentSegment)
        assertEquals(160, info.segmentLimit)
    }

    @Test
    fun testGsmMultipart161Characters() {
        // 161 characters exceeds 160, so multipart splitting activates:
        // Each segment holds at most 153 characters (due to 7-byte UDH concatenation header).
        // 161 chars -> 1st segment: 153 chars, 2nd segment: 8 chars (remaining: 153 - 8 = 145)
        val text = "B".repeat(161)
        val info = SmsSenderHelper.calculateSmsEncoding(text)

        assertFalse(info.isUnicode)
        assertEquals(161, info.characterCount)
        assertEquals(2, info.segmentCount)
        assertEquals(153 - 8, info.remainingInCurrentSegment)
        assertEquals(153, info.segmentLimit)
    }

    @Test
    fun testGsmExtensionCharactersWeight() {
        // Characters like € are in GSM extension table and take 2 7-bit characters
        val text = "Cost: 50€"
        // 'C','o','s','t',':',' ','5','0' = 8 chars + '€' (2 chars) = 10 chars
        val info = SmsSenderHelper.calculateSmsEncoding(text)

        assertFalse(info.isUnicode)
        assertEquals(10, info.characterCount)
        assertEquals(1, info.segmentCount)
        assertEquals(160 - 10, info.remainingInCurrentSegment)
    }

    @Test
    fun testUnicodeHindiSingleSegment() {
        val text = "नमस्ते एंड्रॉइड" // Hindi Devanagari script (UCS-2)
        val info = SmsSenderHelper.calculateSmsEncoding(text)

        assertTrue(info.isUnicode)
        assertEquals(text.length, info.characterCount)
        assertEquals(1, info.segmentCount)
        assertEquals(70 - text.length, info.remainingInCurrentSegment)
        assertEquals(70, info.segmentLimit)
    }

    @Test
    fun testUnicodeMultipartExceeding70Chars() {
        // 71 Unicode characters -> 2 segments of max 67 chars each.
        // 1st segment: 67 chars, 2nd segment: 4 chars (remaining: 67 - 4 = 63)
        val text = "स".repeat(71)
        val info = SmsSenderHelper.calculateSmsEncoding(text)

        assertTrue(info.isUnicode)
        assertEquals(71, info.characterCount)
        assertEquals(2, info.segmentCount)
        assertEquals(67 - 4, info.remainingInCurrentSegment)
        assertEquals(67, info.segmentLimit)
    }

    @Test
    fun testEmojiUnicodeDetection() {
        val text = "Hello! 🚀"
        val info = SmsSenderHelper.calculateSmsEncoding(text)

        assertTrue(info.isUnicode)
        assertEquals(1, info.segmentCount)
        assertEquals("UCS-2 (Unicode)", info.encodingName)
    }
}
