package com.tablodecori.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.tablodecori.app.data.db.SentOrderEntity
import com.tablodecori.app.util.PersianDate
import com.tablodecori.app.util.money
import java.io.File

/** Customer-facing text deliberately omits internal production cost and profit. */
object OrderShareText {
    fun format(order: SentOrderEntity): String = buildString {
        appendLine("🖼️ سفارش ${order.internalNumber}")
        appendLine("━━━━━━━━━━━━")
        appendLine("📦 ست: ${order.productNameSnapshot}")
        appendLine("📐 ابعاد ست: ${order.displayDimensions()}")
        if (order.frameColor.isNotBlank()) appendLine("🎨 رنگ قاب: ${order.frameColor}")
        appendLine("📅 تاریخ ثبت: ${PersianDate.fromEpoch(order.dateEpochMillis).label}")
        if (order.plannedShipAtMillis > 0L) appendLine("🗓️ نوبت ارسال: ${PersianDate.fromEpoch(order.plannedShipAtMillis).label}")
        if (order.trackingCode.isNotBlank()) appendLine("🔗 کد رهگیری: ${order.trackingCode}")
        appendLine()
        appendLine("👤 اطلاعات گیرنده")
        appendLine("نام: ${order.customerName}")
        if (order.instagramId.isNotBlank()) appendLine("♣️ اینستاگرام: ${order.instagramId}")
        if (order.phone.isNotBlank()) appendLine("📞 تماس: ${order.phone}")
        val address = listOf(order.province, order.city, order.addressDetails).filter { it.isNotBlank() }.joinToString("، ")
        if (address.isNotBlank()) appendLine("📍 آدرس: $address")
        if (order.postalCode.isNotBlank()) appendLine("کد پستی: ${order.postalCode}")
        appendLine()
        appendLine("🚚 اطلاعات ارسال")
        appendLine(if (order.shippingPayer == "RECIPIENT") "روش پرداخت: پس‌کرایه؛ پرداخت به شرکت حمل توسط مشتری" else "روش پرداخت: هزینه ارسال با فروشنده")
        if (order.shippingCostToman > 0L) appendLine("هزینه ارسال: ${money(order.shippingCostToman)}")
        appendLine()
        appendLine("💰 اطلاعات پرداخت سفارش")
        appendLine("مبلغ محصول: ${money(order.quotedTotalToman)}")
        appendLine("📥 بیعانه: ${money(order.depositToman)}")
        if (order.otherPaidToman > 0L) appendLine("📥 سایر دریافتی‌ها: ${money(order.otherPaidToman)}")
        if (order.codDueToman > 0L) appendLine("🏠 مبلغ محصول درب منزل: ${money(order.codDueToman)}")
        if (order.codCollectedToman > 0L) appendLine("✅ وصول‌شده درب منزل: ${money(order.codCollectedToman)}")
        appendLine("❌ مانده محصول: ${money(order.outstandingToman())}")
        if (order.note.isNotBlank()) {
            appendLine()
            appendLine("📝 یادداشت: ${order.note}")
        }
    }.trimEnd()
}

object OrderSharing {
    fun share(context: Context, order: SentOrderEntity) {
        val text = OrderShareText.format(order)
        val photo = order.photoFileName.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.jpg")) }
            ?.let { File(context.filesDir, "order_photos/$it") }
            ?.takeIf { it.isFile }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            if (photo != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "عکس ست تابلو", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else type = "text/plain"
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک سفارش"))
    }
}
