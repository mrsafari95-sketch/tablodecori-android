package com.tablodecori.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioPricingTest {
    @Test fun pricesKeepExactStoreUnits() {
        assertEquals("1600000", studioPrice("۱٬۶۰۰٬۰۰۰", 0))
        assertEquals("1600.05", studioPrice("١٦٠٠٫٠٥", 2))
        assertEquals("200", studioPrice("200.000", 0))
        assertEquals("999999999999", studioPrice("999999999999", 0))
    }

    @Test fun malformedOrUnsupportedPricesAreRejected() {
        listOf("0", "-100", "1e6", "۱۰۰ تومان", "1000000000000", "12.001").forEach {
            assertNull(it, studioPrice(it, 2))
        }
    }
}
