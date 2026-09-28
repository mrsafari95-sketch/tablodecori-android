package com.tablodecori.app.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import com.tablodecori.app.data.PricedProduct
import com.tablodecori.app.data.outstandingToman
import com.tablodecori.app.data.expectedProfitToman
import com.tablodecori.app.data.displayDimensions
import com.tablodecori.app.data.sellerShippingToman
import java.io.FileOutputStream
import java.io.OutputStream

object Exporters {
    fun pricebookCsv(products: List<PricedProduct>): String = buildString {
        append('\uFEFF')
        appendLine("نام,نوع محصول,ترکیب ابعاد,تعداد تکه,هزینه بدون سود,سود,قیمت محصول")
        products.forEach { p ->
            val comp=p.product.pieces.joinToString(" + "){"${it.quantity}× ${it.widthCm}×${it.heightCm}"}
            appendLine(listOf(p.product.name,if(p.product.productType=="RELIEF")"تابلو برجسته" else "تابلو معمولی",comp,p.pricing.pieceCount,p.pricing.costBeforeProfitToman,p.pricing.profitToman,p.pricing.finalPriceToman).joinToString(",") { csv(it.toString()) })
        }
    }

    fun ordersCsv(orders: List<com.tablodecori.app.data.db.OrderWithCosts>): String = buildString {
        append('\uFEFF'); appendLine("تاریخ,گیرنده,منبع سفارش,اینستاگرام,تلفن,استان,شهر,محصول,ابعاد,رنگ قاب,تعداد تابلو,وضعیت,نوبت ارسال,کد رهگیری,هزینه تولید,روش ارسال,پس‌کرایه یا هزینه حمل,هزینه ارسال کارگاه,مبلغ توافقی محصول,بیعانه,سایر پرداخت‌ها,پرداخت محصول درب منزل,جمع پرداخت‌ها,مانده محصول,سود فعلی")
        orders.forEach { x -> val o=x.order; appendLine(listOf(PersianDate.fromEpoch(o.dateEpochMillis).label,o.customerName,com.tablodecori.app.data.OrderSource.label(o.orderSource),o.instagramId,o.phone,o.province,o.city,o.productNameSnapshot,o.displayDimensions(),o.frameColor,o.pieceCountSnapshot,o.orderStatus,if(o.plannedShipAtMillis>0L)PersianDate.fromEpoch(o.plannedShipAtMillis).label else "",o.trackingCode,o.productCostSnapshotToman,o.shippingPayer,o.shippingCostToman,o.sellerShippingToman(),o.quotedTotalToman,o.depositToman,o.otherPaidToman,o.codDueToman,o.receivedToman,o.outstandingToman(),o.actualProfitToman).joinToString(","){csv(it.toString())}) }
    }

    private fun csv(s:String)="\"${s.replace("\"","\"\"")}\""

    fun writePricebookPdf(out: OutputStream, products: List<PricedProduct>) {
        val doc=PdfDocument(); var pageNo=1; var page:PdfDocument.Page?=null; var y=0f
        val title=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=20f; typeface=Typeface.DEFAULT_BOLD; textAlign=Paint.Align.RIGHT }
        val body=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=11f; textAlign=Paint.Align.RIGHT }
        fun newPage(){ page?.let{doc.finishPage(it)}; page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo++).create()); y=54f; page!!.canvas.drawText("قیمت‌نامه tablodecori",550f,y,title); y+=32f }
        newPage()
        products.forEach { p ->
            if(y>790) newPage()
            page!!.canvas.drawText("${p.product.name}  |  ${money(p.pricing.finalPriceToman)}",550f,y,body); y+=18f
            val comp=p.product.pieces.joinToString(" + "){"${it.quantity}× ${it.widthCm}×${it.heightCm}"}
            page!!.canvas.drawText(comp,550f,y,body); y+=24f
        }
        page?.let{doc.finishPage(it)}; doc.writeTo(out); doc.close()
    }

    fun printPricebook(context: Context, products: List<PricedProduct>) {
        val pm=context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print("tablodecori-pricebook", object: PrintDocumentAdapter(){
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: android.os.Bundle?) {
                callback.onLayoutFinished(PrintDocumentInfo.Builder("tablodecori-pricebook.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out android.print.PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
                try { FileOutputStream(destination.fileDescriptor).use { writePricebookPdf(it,products) }; callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) } catch(e:Exception){ callback.onWriteFailed(e.message) }
            }
        },null)
    }
}
