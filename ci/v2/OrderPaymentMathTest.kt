package com.tablodecori.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OrderPaymentMathTest {
    @Test fun cashOnDeliveryPromiseIsNotReceived() {
        assertEquals(300_000L, OrderPaymentMath.received(1_000_000L,300_000L,0L,700_000L,0L))
    }

    @Test fun collectedCashOnDeliveryIsReceived() {
        assertEquals(1_000_000L, OrderPaymentMath.received(1_000_000L,300_000L,0L,700_000L,700_000L))
    }

    @Test fun overCollectionIsRejected() {
        try {
            OrderPaymentMath.received(1_000_000L,300_000L,0L,700_000L,700_001L)
            fail("Cash collected cannot exceed cash due")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun overpaymentIsRejected() {
        try {
            OrderPaymentMath.received(1_000_000L,800_000L,300_000L,0L,0L)
            fail("Total received cannot exceed quote")
        } catch (_: IllegalArgumentException) { }
    }
}
