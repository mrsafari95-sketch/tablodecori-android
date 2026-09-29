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
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tablodecori.app.MainActivity
import com.tablodecori.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** A date reminder is persistent across device restarts. WorkManager may run it slightly late. */
object ShippingReminder {
    private const val tag = "order-shipping-reminder"
    private fun workName(id: String) = "ship-$id"

    fun schedule(context: Context, orderId: String, selectedDateMillis: Long, status: String) {
        val manager = WorkManager.getInstance(context)
        if (selectedDateMillis <= 0L || status == "SENT") {
            manager.cancelUniqueWork(workName(orderId))
            return
        }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selectedDateMillis }
        val local = Calendar.getInstance().apply {
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = (local.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ShippingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("orderId", orderId).build())
            .addTag(tag)
            .build()
        manager.enqueueUniqueWork(workName(orderId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, orderId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(orderId))
    }

    suspend fun refreshAll(context: Context, orders: List<com.tablodecori.app.data.db.SentOrderEntity>) {
        withContext(Dispatchers.IO) { WorkManager.getInstance(context).cancelAllWorkByTag(tag).result.get() }
        orders.forEach { schedule(context, it.id, it.plannedShipAtMillis, it.orderStatus) }
    }
}

class ShippingReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("orderId") ?: return Result.success()
        val db = AppDatabase.create(applicationContext)
        val order = try { db.orderDao().getOrder(id) } finally { db.close() } ?: return Result.success()
        if (order.plannedShipAtMillis <= 0L || order.orderStatus == "SENT") return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("shipping_due", "یادآوری ارسال سفارش", NotificationManager.IMPORTANCE_DEFAULT))
        val plannedUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = order.plannedShipAtMillis }
        val today = Calendar.getInstance()
        val sameDay = today.get(Calendar.YEAR)==plannedUtc.get(Calendar.YEAR) && today.get(Calendar.MONTH)==plannedUtc.get(Calendar.MONTH) && today.get(Calendar.DAY_OF_MONTH)==plannedUtc.get(Calendar.DAY_OF_MONTH)
        val launch = Intent(applicationContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(applicationContext, id.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "shipping_due")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if(sameDay)"امروز نوبت ارسال سفارش است" else "موعد ارسال سفارش رسیده است")
            .setContentText("${order.customerName} · ${order.productNameSnapshot}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("سفارش ${order.customerName} آمادهٔ بررسی و ارسال است. ${order.productNameSnapshot}"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification) }
        catch (_: SecurityException) { /* Permission can be revoked after scheduling. */ }
        return Result.success()
    }
}
