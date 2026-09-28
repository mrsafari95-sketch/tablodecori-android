package com.tablodecori.app.data

import com.tablodecori.app.data.db.SentOrderEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderSharingTest {
    @Test fun manualDimensionsAndRecipientShippingAppearWithoutInternalCost() {
        val order = SentOrderEntity(
            id="id", internalNumber="TD-1", dateEpochMillis=1_700_000_000_000L,
            customerName="مشتری", instagramId="", phone="09120000000", province="گیلان", city="رودسر",
            productId="product", productNameSnapshot="ست نمونه", compositionSnapshot="۱ عدد ۴۰×۶۰",
            pieceCountSnapshot=1, productCostSnapshotToman=500_000L, shippingCostToman=100_000L,
            receivedToman=900_000L, actualProfitToman=400_000L, note="", createdAt=1L, postalCode="1234567890",
            quotedTotalToman=900_000L, depositToman=200_000L, codDueToman=700_000L, codCollectedToman=700_000L,
            shippingPayer="RECIPIENT", dimensionsText="۲ عدد ۳۰×۴۰", orderSource="BASALAM"
        )
        val text = OrderShareText.format(order)
        assertTrue(text.contains("📐 ابعاد ست: ۲ عدد ۳۰×۴۰"))
        assertTrue(text.contains("پس‌کرایه"))
        assertTrue(text.contains("📥 بیعانه:"))
        assertTrue(text.contains("🔗 منبع سفارش: باسلام"))
        assertTrue(text.contains("پرداخت درب منزل (واریز آنی)"))
        assertTrue(text.contains("👤 نام: مشتری"))
        assertTrue(text.contains("📞 شماره تماس: 09120000000"))
        assertTrue(text.contains("📮 کد پستی: 1234567890"))
        assertFalse(text.contains("هزینه تولید"))
        assertFalse(text.contains("سود"))
        assertEquals(0L, order.outstandingToman())
        assertEquals(400_000L, order.expectedProfitToman())
        assertEquals(300_000L, order.copy(shippingPayer="SENDER").expectedProfitToman())
    }
}
