package com.tablodecori.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tablodecori.app.MainActivity
import com.tablodecori.app.data.db.StockItemEntity

object StockAlert {
    fun show(context:Context,item:StockItemEntity):Boolean {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("low_stock","موجودی کم انبار",NotificationManager.IMPORTANCE_DEFAULT))
        val title="موجودی ${item.materialName} کم است"
        val detail=listOf(item.variantKey.takeIf{it.isNotBlank()},"${StockQuantity.format(item.onHandMicros)} ${StockQuantity.unitLabel(item.unit)} موجود").filterNotNull().joinToString(" · ")
        val intent=Intent(context,MainActivity::class.java).apply { flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending=PendingIntent.getActivity(context,item.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,"low_stock").setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending).setAutoCancel(true).build()
        return try { NotificationManagerCompat.from(context).notify(item.id.hashCode(),notification);true } catch (_:SecurityException){false}
    }
}

object StockQuantity {
    fun parse(text:String):Long = java.math.BigDecimal(text.trim().map { c -> when(c){in '۰'..'۹'->'0'+(c-'۰');in '٠'..'٩'->'0'+(c-'٠');'٫'->'.';else->c} }.joinToString("").replace(",","").replace("٬",""))
        .multiply(java.math.BigDecimal(StockPlanner.SCALE)).setScale(0,java.math.RoundingMode.UNNECESSARY).longValueExact()
    fun format(micros:Long):String = java.math.BigDecimal(micros).divide(java.math.BigDecimal(StockPlanner.SCALE)).stripTrailingZeros().toPlainString()
    fun unitLabel(unit:String)=when(unit){"METER"->"متر";"SQM"->"مترمربع";else->"عدد"}
}
