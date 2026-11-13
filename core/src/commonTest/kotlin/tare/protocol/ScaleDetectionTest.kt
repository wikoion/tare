package tare.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Payloads are captures (and two synthetic frames) published in etekcity_esf551_ble's test suite
// (MIT). See THIRD_PARTY_NOTICES.md.
class ScaleDetectionTest {
    private val esf551 = "0162291c004dd00002".hexToBytes() // model code 2
    private val efsA591s = "0145862801eacf0005".hexToBytes() // model code 5, not supported
    private val purifier = "018e31e5519140c623020202".hexToBytes() // a VeSync air purifier
    private val esf24 = "012601000607aa0b44ac04".hexToBytes()
    private val foreignQn = "09e900000003aa670003ff".hexToBytes()
    private val name = "Etekcity Smart Fitness Scale"

    @Test
    fun recognisesEsf551ByModelCode() {
        assertEquals(ScaleModel.ESF551, ScaleDetection.detect(name, null, esf551))
        assertEquals(ScaleModel.ESF551, ScaleDetection.detect(null, null, esf551))
    }

    @Test
    fun recognisesEsf24ByModelCodeOrName() {
        assertEquals(ScaleModel.ESF24, ScaleDetection.detect(null, esf24))
        assertEquals(ScaleModel.ESF24, ScaleDetection.detect("QN-Scale1", null))
    }

    @Test
    fun otherEtekcityScalesMightBeScalesButAreNotRecognised() {
        assertNull(ScaleDetection.detect(name, null, efsA591s))
        assertTrue(ScaleDetection.mightBeScale(name, null, efsA591s, advertisesScaleService = false))
    }

    @Test
    fun otherEtekcityProductsAreNotScales() {
        assertNull(ScaleDetection.detect("Core200S", null, purifier))
        assertFalse(ScaleDetection.mightBeScale("Core200S", null, purifier, advertisesScaleService = false))
    }

    @Test
    fun shortCatchAllManufacturerDataIsNotAScale() {
        val shortFfff = "100000000000000000".hexToBytes() // nine bytes, too short for a QN frame
        assertNull(ScaleDetection.detect("Snooz", shortFfff))
        assertFalse(ScaleDetection.mightBeScale("Snooz", shortFfff, null, advertisesScaleService = false))
        assertTrue(ScaleDetection.mightBeScale(null, foreignQn, null, advertisesScaleService = false))
    }
}
