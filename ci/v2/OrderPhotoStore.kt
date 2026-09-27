package com.tablodecori.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Keeps order photos inside the app so a gallery deletion cannot break an order. */
class OrderPhotoStore(private val context: Context) {
    private val directory = File(context.filesDir, "order_photos")
    private val safeName = Regex("[a-f0-9-]{36}\\.jpg")

    fun file(name: String): File? = name.takeIf { safeName.matches(it) }?.let { File(directory, it) }

    suspend fun importSelected(uriText: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriText)
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1600) {
                    val scale = 1600f / longest
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (context.contentResolver.openInputStream(uri) ?: error("عکس انتخاب‌شده قابل خواندن نیست.")).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "فرمت عکس پشتیبانی نمی‌شود." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("عکس انتخاب‌شده قابل خواندن نیست.")
        }
        directory.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        val temp = File.createTempFile("order-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 84, output)) { "ذخیره عکس ناموفق بود." }
                output.fd.sync()
            }
            require(temp.renameTo(File(directory, name))) { "ذخیره عکس ناموفق بود." }
            name
        } finally {
            temp.delete()
        }
    }

    fun delete(name: String) { file(name)?.delete() }

    fun exportBase64(name: String): String? = file(name)?.takeIf { it.isFile }
        ?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }

    fun restoreBytes(name: String, bytes: ByteArray) {
        val target = file(name) ?: error("نام فایل عکس در بکاپ نامعتبر است.")
        require(bytes.size <= 8_000_000) { "حجم عکس در بکاپ بیش از حد مجاز است." }
        directory.mkdirs()
        val temp = File.createTempFile("restore-", ".tmp", directory)
        try {
            temp.writeBytes(bytes)
            temp.copyTo(target, overwrite = true)
        } finally { temp.delete() }
    }
}
