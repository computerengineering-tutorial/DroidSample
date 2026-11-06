package com.example.droidsample.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PhoneNumberHelper] and phone number formatting logic.
 */
class PhoneNumberTest {

    @Test
    fun testCountryCallingCodeResolution() {
        assertEquals("+1", PhoneNumberHelper.getCountryCallingCode("US"))
        assertEquals("+1", PhoneNumberHelper.getCountryCallingCode("CA"))
        assertEquals("+91", PhoneNumberHelper.getCountryCallingCode("IN"))
        assertEquals("+44", PhoneNumberHelper.getCountryCallingCode("GB"))
        assertEquals("+49", PhoneNumberHelper.getCountryCallingCode("DE"))
        assertEquals("+33", PhoneNumberHelper.getCountryCallingCode("FR"))
        assertEquals("+81", PhoneNumberHelper.getCountryCallingCode("JP"))
        assertEquals("+86", PhoneNumberHelper.getCountryCallingCode("CN"))
        assertEquals("+61", PhoneNumberHelper.getCountryCallingCode("AU"))
        assertEquals("+971", PhoneNumberHelper.getCountryCallingCode("AE"))
    }

    @Test
    fun testCountryCallingCodeCaseInsensitivity() {
        assertEquals("+91", PhoneNumberHelper.getCountryCallingCode("in"))
        assertEquals("+1", PhoneNumberHelper.getCountryCallingCode("us"))
        assertEquals("+44", PhoneNumberHelper.getCountryCallingCode("gb"))
    }

    @Test
    fun testUnknownCountryCallingCode() {
        assertNull(PhoneNumberHelper.getCountryCallingCode("ZZ"))
        assertNull(PhoneNumberHelper.getCountryCallingCode(""))
        assertNull(PhoneNumberHelper.getCountryCallingCode("   "))
    }

    @Test
    fun testFormattedNumberResultDataModel() {
        val result = FormattedNumberResult(
            rawInput = "+1 650 555 1234",
            e164 = "+16505551234",
            international = "+1 650-555-1234",
            national = "(650) 555-1234",
            normalized = "16505551234",
            isValidGlobal = true,
            countryIso = "US",
            countryCallingCode = "+1"
        )

        assertEquals("+1 650 555 1234", result.rawInput)
        assertEquals("+16505551234", result.e164)
        assertEquals("+1 650-555-1234", result.international)
        assertEquals("(650) 555-1234", result.national)
        assertEquals("16505551234", result.normalized)
        assertTrue(result.isValidGlobal)
        assertEquals("US", result.countryIso)
        assertEquals("+1", result.countryCallingCode)
    }

    @Test
    fun testEmptyInputHandling() {
        val emptyResult = PhoneNumberHelper.formatComprehensive("", "US")
        assertEquals("", emptyResult.rawInput)
        assertNull(emptyResult.e164)
        assertEquals("", emptyResult.international)
    }
}
