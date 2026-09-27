package com.tablodecori.app.data

import com.tablodecori.app.data.db.SentOrderEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderSharingTest {
    @Test fun manualDimensionsAndRecipientShippingAppearWithoutInternalCost() {
        val order = SentOrderEntity(
            id="id", internalNumber="TD-1", dateEpochMillis=1_700_000_000_000L,
            customerName="مشتری", instagramId="", phone="", province="گیلان", city="رودسر",
            productId="product", productNameSnapshot="ست نمونه", compositionSnapshot="۱ عدد ۴۰×۶۰",
            pieceCountSnapshot=1, productCostSnapshotToman=500_000L, shippingCostToman=100_000L,
            receivedToman=200_000L, actualProfitToman=-300_000L, note="", createdAt=1L,
            quotedTotalToman=900_000L, depositToman=200_000L, codDueToman=700_000L,
            shippingPayer="RECIPIENT", dimensionsText="۲ عدد ۳۰×۴۰"
        )
        val text = OrderShareText.format(order)
        assertTrue(text.contains("📐 ابعاد ست: ۲ عدد ۳۰×۴۰"))
        assertTrue(text.contains("پس‌کرایه"))
        assertTrue(text.contains("📥 بیعانه:"))
        assertFalse(text.contains("هزینه تولید"))
        assertFalse(text.contains("سود"))
    }
}
