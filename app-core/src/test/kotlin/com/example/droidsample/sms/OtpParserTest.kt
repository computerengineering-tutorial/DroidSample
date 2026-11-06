package com.example.droidsample.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OtpParserTest {

    @Test
    fun testExtractSixDigitOtp() {
        val message = "Your OTP for account login is 839201. Please do not share it."
        val otp = OtpParser.extractOtp(message)
        assertEquals("839201", otp)
    }

    @Test
    fun testExtractFourDigitCode() {
        val message = "Uber code: 4920. Never give this code to anyone."
        val otp = OtpParser.extractOtp(message)
        assertEquals("4920", otp)
    }

    @Test
    fun testExtractEightDigitPasscode() {
        val message = "Your Microsoft security passcode is 98712345."
        val otp = OtpParser.extractOtp(message)
        assertEquals("98712345", otp)
    }

    @Test
    fun testSmsRetrieverFormat() {
        val template = "<#> 618302 is your verification passcode for MyApp. FA+9qCX9VSu"
        val otp = OtpParser.extractOtp(template)
        val hash = OtpParser.extractAppHash(template)

        assertEquals("618302", otp)
        assertEquals("FA+9qCX9VSu", hash)
    }

    @Test
    fun testExtractSenderTag() {
        val msgWithBracket = "[BANK-ALERT] Your card ending in 4102 had a purchase of $15.00."
        val senderBracket = OtpParser.extractSenderTag(msgWithBracket)
        assertEquals("BANK-ALERT", senderBracket)

        val msgWithFrom = "Your secret code is 1234 from Google."
        val senderFrom = OtpParser.extractSenderTag(msgWithFrom)
        assertEquals("Google", senderFrom)
    }

    @Test
    fun testNonOtpMessageReturnsNull() {
        val message = "Hey there, see you at the restaurant tonight around 7!"
        val otp = OtpParser.extractOtp(message)
        assertNull(otp)
    }

    @Test
    fun testEmptyMessageReturnsNull() {
        val otp = OtpParser.extractOtp("")
        assertNull(otp)
    }
}
