package com.example.droidsample.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpAddressTest {

    @Test
    fun testIpv4Validation() {
        assertTrue(NetworkInterfaceInspector.isIpv4("192.168.1.1"))
        assertTrue(NetworkInterfaceInspector.isIpv4("10.0.0.1"))
        assertTrue(NetworkInterfaceInspector.isIpv4("127.0.0.1"))
        assertTrue(NetworkInterfaceInspector.isIpv4("0.0.0.0"))
        assertTrue(NetworkInterfaceInspector.isIpv4("255.255.255.255"))

        assertFalse(NetworkInterfaceInspector.isIpv4("256.0.0.1"))
        assertFalse(NetworkInterfaceInspector.isIpv4("192.168.1"))
        assertFalse(NetworkInterfaceInspector.isIpv4("192.168.1.1.1"))
        assertFalse(NetworkInterfaceInspector.isIpv4("abc"))
        assertFalse(NetworkInterfaceInspector.isIpv4(""))
        assertFalse(NetworkInterfaceInspector.isIpv4("192.168.01.1"))
    }

    @Test
    fun testIpv6Validation() {
        assertTrue(NetworkInterfaceInspector.isIpv6("::1"))
        assertTrue(NetworkInterfaceInspector.isIpv6("2001:db8::1"))
        assertTrue(NetworkInterfaceInspector.isIpv6("fe80::1"))
        assertTrue(NetworkInterfaceInspector.isIpv6("fe80::215:5dff:fe8c:589b%wlan0"))

        assertFalse(NetworkInterfaceInspector.isIpv6("192.168.1.1"))
        assertFalse(NetworkInterfaceInspector.isIpv6("invalid::ipv6:::1"))
        assertFalse(NetworkInterfaceInspector.isIpv6(""))
    }

    @Test
    fun testPrefixLengthToSubnetMask() {
        assertEquals("0.0.0.0", NetworkInterfaceInspector.prefixLengthToSubnetMask(0))
        assertEquals("255.0.0.0", NetworkInterfaceInspector.prefixLengthToSubnetMask(8))
        assertEquals("255.255.0.0", NetworkInterfaceInspector.prefixLengthToSubnetMask(16))
        assertEquals("255.255.255.0", NetworkInterfaceInspector.prefixLengthToSubnetMask(24))
        assertEquals("255.255.255.252", NetworkInterfaceInspector.prefixLengthToSubnetMask(30))
        assertEquals("255.255.255.255", NetworkInterfaceInspector.prefixLengthToSubnetMask(32))
    }

    @Test
    fun testCalculateSubnetRange() {
        val (net24, broad24) = NetworkInterfaceInspector.calculateSubnetRange("192.168.1.50", 24)
        assertEquals("192.168.1.0", net24)
        assertEquals("192.168.1.255", broad24)

        val (net8, broad8) = NetworkInterfaceInspector.calculateSubnetRange("10.5.20.15", 8)
        assertEquals("10.0.0.0", net8)
        assertEquals("10.255.255.255", broad8)

        val (net30, broad30) = NetworkInterfaceInspector.calculateSubnetRange("172.16.5.10", 30)
        assertEquals("172.16.5.8", net30)
        assertEquals("172.16.5.11", broad30)
    }

    @Test
    fun testFormatMacAddress() {
        val macBytes = byteArrayOf(
            0x00.toByte(),
            0x1A.toByte(),
            0x2B.toByte(),
            0x3C.toByte(),
            0x4D.toByte(),
            0x5E.toByte()
        )
        assertEquals("00:1a:2b:3c:4d:5e", NetworkInterfaceInspector.formatMacAddress(macBytes))
        assertEquals("Unavailable", NetworkInterfaceInspector.formatMacAddress(null))
        assertEquals("Unavailable", NetworkInterfaceInspector.formatMacAddress(byteArrayOf()))
    }
}
