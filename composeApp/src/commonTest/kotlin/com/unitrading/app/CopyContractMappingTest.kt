package com.unitrading.app

import kotlin.test.*

class CopyContractMappingTest {
    @Test fun preservesMonthAndYearAndExposesTheCorrectRelativeSizes() {
        assertEquals(CopyContractMapping("CLZ6", "MCLZ6", 100, 10),
            CopyContractMappings.resolve("CLZ6", CopyContractSize.MICRO, setOf("MCLZ6")))
        assertEquals(CopyContractMapping("MNQH2027", "NQH2027", 10, 100),
            CopyContractMappings.resolve("MNQH2027", CopyContractSize.STANDARD, setOf("NQH2027")))
        assertEquals(CopyContractMapping("NNQZ6", "MNQZ6", 1, 10),
            CopyContractMappings.resolve("NNQZ6", CopyContractSize.MICRO, setOf("MNQZ6")))
        assertEquals(CopyContractMapping("NESZ6", "ESZ6", 1, 100),
            CopyContractMappings.resolve("NESZ6", CopyContractSize.STANDARD, setOf("ESZ6")))
    }

    @Test fun neverGuessesUnknownProductsContinuousContractsOrAnotherMonth() {
        assertNull(CopyContractMappings.resolve("@CL", CopyContractSize.MICRO, setOf("MCLZ6")))
        assertNull(CopyContractMappings.resolve("SIZ6", CopyContractSize.MICRO, setOf("SILZ6")))
        assertNull(CopyContractMappings.resolve("CLZ6", CopyContractSize.MICRO, setOf("MCLF7")))
        assertNull(CopyContractMappings.resolve("CLZ6", CopyContractSize.MICRO, emptySet()))
    }

    @Test fun rejectsInvalidOrUnrepresentablePricesWithoutRounding() {
        assertTrue(CopyContractMappings.priceFits(5_500_250_000, 250_000))
        assertFalse(CopyContractMappings.priceFits(5_500_250_001, 250_000))
        assertFalse(CopyContractMappings.priceFits(0, 250_000))
        assertFalse(CopyContractMappings.priceFits(100, 0))
    }
}
