package com.unitrading.app

import kotlin.test.*

class CopyGroupConfigurationTest {
    private val clToMicro = CopyContractMapping("CLZ6", "MCLZ6", 100, 10)
    private val microToStandard = CopyContractMapping("MESZ6", "ESZ6", 10, 100)

    @Test fun calculationsPreserveTheTwoDifferentMeaningsOfMultiplier() {
        val exposure = assertNotNull(copyQuantity(1, 100, clToMicro, CopyQuantityBasis.MATCH_EXPOSURE))
        assertEquals(10L, exposure.wholeContracts); assertTrue(exposure.exact)
        val contracts = assertNotNull(copyQuantity(1, 100, clToMicro, CopyQuantityBasis.MATCH_CONTRACTS))
        assertEquals(1L, contracts.wholeContracts); assertTrue(contracts.exact)
    }

    @Test fun anUnrepresentableFractionIsReturnedAsSuchWithoutAHiddenRoundingDecision() {
        val fraction = assertNotNull(copyQuantity(1, 100, microToStandard, CopyQuantityBasis.MATCH_EXPOSURE))
        assertEquals(0L, fraction.wholeContracts); assertFalse(fraction.exact)
        assertEquals(1L, assertNotNull(copyQuantity(10, 100, microToStandard, CopyQuantityBasis.MATCH_EXPOSURE)).wholeContracts)
    }

    @Test fun selectedRoundingNeverBypassesTheFollowerCap() {
        val quantity = assertNotNull(copyQuantity(1, 150, clToMicro, CopyQuantityBasis.MATCH_CONTRACTS))
        assertEquals(2L, quantity.roundedUpWithinCap(2))
        assertNull(quantity.roundedUpWithinCap(1))
        assertEquals(1L, assertNotNull(copyQuantity(1, 50, microToStandard, CopyQuantityBasis.MATCH_CONTRACTS)).roundedUpWithinCap(1))
    }

    @Test fun followersHaveSeparateSettingsAndAccountIdentityIncludesLogin() {
        val leader = CopyAccountKey("login-one", "42")
        val a = CopyFollowerConfiguration(CopyAccountKey("login-two", "44"), 100, CopyContractSize.MICRO, 10)
        val b = CopyFollowerConfiguration(CopyAccountKey("login-one", "43"), 150, CopyContractSize.STANDARD, 2)
        assertTrue(CopyGroupConfiguration(leader, listOf(a, b)).valid())
        assertFalse(CopyGroupConfiguration(leader, listOf(a, a)).valid())
        assertFalse(CopyGroupConfiguration(leader, listOf(a.copy(account = leader))).valid())
        assertFalse(CopyGroupConfiguration(leader, listOf(a.copy(account = CopyAccountKey("login-two", "42")))).valid())
        assertFalse(CopyGroupConfiguration(leader, listOf(b.copy(multiplierHundredths = 0))).valid())
    }
}
