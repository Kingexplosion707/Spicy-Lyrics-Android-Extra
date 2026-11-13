package com.tx24.spicyplayer.aod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AodFingerprintTest {
    @Test
    fun `reads the named sensor location`() {
        val dump = "props: FingerprintSensorPropertiesInternal{id=0, sensorType=3, sensorLocationX=540, sensorLocationY=2160, sensorRadius=130}"
        assertEquals(Triple(540f, 2160f, 130f), AodFingerprint.parseLocation(dump))
    }

    @Test
    fun `reads a sensor location from the list`() {
        val dump = "mSensorLocations=[SensorLocationInternal{display=, x=672, y=2550, r=150}]"
        assertEquals(Triple(672f, 2550f, 150f), AodFingerprint.parseLocation(dump))
    }

    @Test
    fun `nothing without a location`() {
        assertNull(AodFingerprint.parseLocation("Fingerprint Manager State: no sensors"))
    }
}
