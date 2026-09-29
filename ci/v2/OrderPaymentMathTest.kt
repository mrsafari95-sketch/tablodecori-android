package com.tablodecori.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OrderPaymentMathTest {
    @Test fun cashOnDeliveryCountsImmediately() {
        assertEquals(1_000_000L, OrderPaymentMath.received(1_000_000L,300_000L,0L,700_000L))
    }

    @Test fun partPaymentLeavesOnlyUnassignedBalance() {
        assertEquals(800_000L, OrderPaymentMath.received(1_000_000L,300_000L,0L,500_000L))
    }

    @Test fun overCollectionIsRejected() {
        try {
            OrderPaymentMath.received(1_000_000L,300_000L,0L,700_001L)
            fail("Total payments cannot exceed quote")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun overpaymentIsRejected() {
        try {
            OrderPaymentMath.received(1_000_000L,800_000L,300_000L,0L)
            fail("Total received cannot exceed quote")
        } catch (_: IllegalArgumentException) { }
    }
}
