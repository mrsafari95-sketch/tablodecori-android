package com.tablodecori.app.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tablodecori.app.TablodecoriApp
import com.tablodecori.app.pricing.PieceInput
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class StudioRow(
    val key: String, val revision: String, val group: String, val label: String,
    val prices: String, val products: Int, val count: Int, val sales: Int, val missing: Int,
    val grouped: Boolean, val scope: JSONObject?,
    val sampleTitle: String, val thumbnail: String, val description: String, val pieces: List<PieceInput>,
    val referenceExcluded: Boolean = false
)

internal fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.let { array -> List(array.length()) { array.getJSONObject(it) } }.orEmpty()

internal fun StudioRow(json: JSONObject): StudioRow {
    val sample = json.optJSONObject("sample")
    val prices = json.getJSONArray("prices")
    return StudioRow(
        json.getString("key"), json.getString("revision"), json.getString("group_name"),
        json.getString("label"), List(prices.length()) { prices.getString(it) }.joinToString("، "),
        json.getInt("products"), json.getInt("count"), json.getInt("sales"), json.getInt("missing"),
        json.getString("group_id").isNotEmpty() || json.has("scope"), json.optJSONObject("scope"),
        sample?.optString("title").orEmpty(), sample?.optString("image").orEmpty(),
        sample?.optString("description").orEmpty(),
        sample?.objects("pieces")?.map { PieceInput(it.getInt("width"), it.getInt("height"), it.getInt("quantity")) }.orEmpty(),
        json.optBoolean("reference_excluded")
    )
}

internal data class StudioCard(
    val key: String, val kind: String, val name: String, val products: Int,
    val rows: List<StudioRow>, val images: List<Pair<String, String>>
)

internal fun studioCards(catalog: JSONObject?, references: List<StudioRow>): List<StudioCard> {
    val byKey = references.associateBy { it.key }
    val seen = mutableSetOf<String>()
    val cards = catalog?.objects("cards").orEmpty().map { card ->
        val keys = card.getJSONArray("rows")
        val rows = List(keys.length()) { byKey[keys.getString(it)] }.filterNotNull()
        seen.addAll(rows.map { it.key })
        StudioCard(card.getString("key"), card.getString("kind"), card.getString("name"), card.getInt("products"),
            rows, card.objects("images").map { it.optString("image") to it.optString("title") })
    }
    // Cached catalogs from before the plugin exported cards, and previously linked scopes.
    val extra = references.filter { it.key !in seen }.groupBy { row ->
        val scope = requireNotNull(row.scope)
        scope.optString("group_id").ifEmpty { scope.getJSONArray("product_ids").toString() }
    }.map { (id, rows) ->
        val grouped = rows.first().scope!!.optString("group_id").isNotEmpty()
        val members = rows.flatMap { row ->
            val ids = row.scope!!.getJSONArray("product_ids")
            List(ids.length()) { ids.getLong(it) }
        }.distinct().size
        StudioCard("cached:$id", if (grouped && cards.isEmpty()) "group" else "review",
            if (grouped) rows.first().group else "مراجع ذخیره‌شده", members, rows, emptyList())
    }
    return cards + extra
}

internal data class StudioState(
    val catalog: JSONObject? = null,
    val drafts: List<JSONObject> = emptyList(),
    val job: JSONObject? = null,
    val pending: JSONObject? = null,
    val preview: JSONObject? = null,
    val members: Map<String, JSONObject> = emptyMap(),
    val links: Map<String, JSONObject> = emptyMap(),
    val salePolicy: String = "keep",
    val owner: String = "",
    val updatedAt: Long = 0,
    val ownerRatesApplied: Boolean = false,
    val connected: Boolean = false,
    val canResumeScan: Boolean = false,
    val scanned: Int = 0,
    val scanTotal: Int = 0,
    val busy: Boolean = false,
    val message: String = ""
) {
    val rows get() = catalog?.let { (it.objects("references") + it.objects("rows")).map(::StudioRow) }.orEmpty()
    val linkedProducts get() = links.filterValues { !it.optBoolean("disabled") }.mapValues { it.value.getString("local_id") }
    val currency get() = catalog?.optString("currency").orEmpty()
    val acceptsToman get() = catalog?.optString("currency_code") in setOf("IRT", "TOMAN") || currency.trim() == "تومان"
    val inProgress get() = job?.optString("state") == "running" || pending != null
}

private val studioDimensionSeparator = Regex("""(?<=\d)\s*(?:[×xX*]|در)\s*(?=\d)""")

internal fun studioSearchText(raw: String): String {
    val normalized = raw.trim().map { glyph ->
        when (glyph) {
            in '۰'..'۹' -> '0' + (glyph - '۰')
            in '٠'..'٩' -> '0' + (glyph - '٠')
            'ي' -> 'ی'
            'ك' -> 'ک'
            '\u200c' -> ' '
            else -> glyph
        }
    }.joinToString("")
    return studioDimensionSeparator.replace(normalized, "×")
}

internal fun studioPrice(raw: String, decimals: Int): String? {
    val value = buildString {
        for (glyph in raw.trim()) {
            when (glyph) {
                in '۰'..'۹' -> append('0' + (glyph - '۰'))
                in '٠'..'٩' -> append('0' + (glyph - '٠'))
                '٫' -> append('.')
                '٬', ',' -> Unit
                else -> append(glyph)
            }
        }
    }
    if (!Regex("[0-9]{1,12}(?:\\.[0-9]{1,8})?").matches(value)) return null
    val amount = value.toBigDecimal().stripTrailingZeros()
    return amount.takeIf { it.signum() > 0 && it.scale() <= decimals && it <= "999999999999".toBigDecimal() }
        ?.toPlainString()
}

private class StudioFailure(message: String, val status: Int = 0, val serverCode: String = "") : IOException(message)

@Suppress("DEPRECATION")
internal fun publiclySignedStudioBuild(context: Context): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    val manager = context.packageManager
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
        manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
    } else {
        manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
    }
    return signatures.isNullOrEmpty() || signatures.any { certificate ->
        digest.digest(certificate.toByteArray()).joinToString("") { "%02x".format(it) } ==
            "c4697509e1237ec43a799eaeca5e924401b5766c75ced7c79ba4675828d0d29e"
    }
}

private class StudioStorage(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "studio-pricing.json"))
    private val preferences = context.getSharedPreferences("studio_pricing_connection", Context.MODE_PRIVATE)
    private val alias = "tablodecori.studio.device"
    private val keys get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun load(): StudioState {
        val json = try { JSONObject(file.readFully().toString(Charsets.UTF_8)) }
        catch (_: java.io.FileNotFoundException) { JSONObject() }
        val members = json.optJSONObject("members") ?: JSONObject()
        val links = json.optJSONObject("links") ?: JSONObject()
        return StudioState(
            catalog = json.optJSONObject("catalog"),
            drafts = json.objects("drafts"),
            job = json.optJSONObject("job"),
            pending = json.optJSONObject("pending"),
            members = members.keys().asSequence().associateWith { members.getJSONObject(it) },
            links = links.keys().asSequence().associateWith { links.getJSONObject(it) },
            salePolicy = json.optString("sale_policy", "keep"),
            owner = json.optString("owner"),
            updatedAt = json.optLong("updated_at"),
            ownerRatesApplied = json.optBoolean("owner_rates_applied"),
            canResumeScan = scanToken() != null,
            connected = preferences.contains("device")
        )
    }

    fun save(state: StudioState) {
        val json = JSONObject().put("catalog", state.catalog).put("drafts", JSONArray(state.drafts))
            .put("job", state.job).put("pending", state.pending)
            .put("members", JSONObject(state.members)).put("updated_at", state.updatedAt)
            .put("sale_policy", state.salePolicy).put("owner", state.owner).put("links", JSONObject(state.links))
            .put("owner_rates_applied", state.ownerRatesApplied)
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    fun scanToken(): String? = preferences.getString("scan_token", null)

    fun setScanToken(token: String?) {
        if (token != scanToken() && !preferences.edit().putString("scan_token", token).commit())
            throw StudioFailure("ذخیرهٔ نقطهٔ ادامه روی گوشی انجام نشد.")
    }

    fun saveDevice(device: String) {
        val key = keys.getKey(alias, null) as? SecretKey ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(device.toByteArray()), Base64.NO_WRAP)
        check(preferences.edit().putString("device", encoded).remove("scan_token").commit())
    }

    fun device(): String {
        val stored = preferences.getString("device", null)
            ?: throw StudioFailure("ابتدا کلید اتصال فروشگاه را وارد کنید.")
        try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keys.getKey(alias, null),
                GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            throw StudioFailure("کلید ذخیره‌شده قابل خواندن نیست. کلید اتصال را دوباره وارد کنید.")
        }
    }

    fun disconnect() {
        check(preferences.edit().remove("device").remove("scan_token").commit())
    }
}

private class StudioApi(private val device: String) {
    fun call(operation: String, body: JSONObject = JSONObject()): JSONObject {
        val payload = JSONObject(body.toString()).put("operation", operation).toString().toByteArray(Charsets.UTF_8)
        val connection = URL("https://tablodecori.ir/wp-json/tdps/v1/mobile-pricing")
            .openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12000
            connection.readTimeout = 25000
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-TDPS-Device", device)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val input = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = input?.use { stream ->
                val buffer = ByteArray(8192)
                val bytes = java.io.ByteArrayOutputStream()
                var length = stream.read(buffer)
                while (length != -1) {
                    if (bytes.size() + length > 4 * 1024 * 1024) throw StudioFailure("پاسخ فروشگاه بیش از حد بزرگ است.")
                    bytes.write(buffer, 0, length)
                    length = stream.read(buffer)
                }
                runCatching { JSONObject(bytes.toString("UTF-8")) }.getOrNull()
            }
            if (code == 404) throw StudioFailure("مسیر اتصال پیدا نشد. نسخهٔ استودیوی محصولات را بررسی کنید.", code)
            if (code !in 200..299) throw StudioFailure(
                response?.optString("message")?.take(350)?.takeIf(String::isNotBlank)
                    ?: "درخواست فروشگاه انجام نشد (خطای ${code}).", code, response?.optString("code").orEmpty()
            )
            return response ?: throw StudioFailure("پاسخ فروشگاه معتبر نیست.")
        } finally {
            connection.disconnect()
        }
    }
}

internal class StudioPricingViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = StudioStorage(application)
    private val context = application.applicationContext
    val offlineQueue = StudioOfflineQueue.state
    private val repository = (application as TablodecoriApp).repository
    val hasOwnerRates = application.assets.list("")?.contains("tdps-owner-pricing.json") == true
    val localProducts = repository.allPricedProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val publicSigning = publiclySignedStudioBuild(application)
    private val mutable = MutableStateFlow(StudioState(busy = true))
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                mutable.value = withContext(Dispatchers.IO) {
                    StudioOfflineQueue.restore(context)
                    storage.load()
                }
            } catch (_: Exception) {
                mutable.value = StudioState(message = "خواندن اطلاعات ذخیره‌شدهٔ فروشگاه انجام نشد.")
            }
        }
    }

    private fun work(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: StudioFailure) {
                mutable.update { it.copy(message = error.message.orEmpty()) }
            } catch (_: java.net.SocketTimeoutException) {
                mutable.update { it.copy(message = "فروشگاه در زمان انتظار پاسخ نداد.") }
            } catch (_: java.net.UnknownHostException) {
                mutable.update { it.copy(message = "اتصال به آدرس فروشگاه برقرار نشد. اینترنت گوشی را بررسی کنید.") }
            } catch (_: IOException) {
                mutable.update { it.copy(message = "ارتباط یا ذخیره‌سازی انجام نشد. می‌توانید ادامهٔ کار را دوباره امتحان کنید.") }
            } catch (_: Exception) {
                mutable.update { it.copy(message = "پردازش اطلاعات فروشگاه انجام نشد.") }
            } finally {
                mutable.update { it.copy(busy = false, scanned = 0, scanTotal = 0) }
            }
        }
    }

    private fun update(state: StudioState) {
        storage.save(state)
        mutable.value = state
    }

    private fun requirePrivateSigning() {
        if (publicSigning) throw StudioFailure("کلید امضای این نسخه عمومی است. برای اتصال به فروشگاه نسخهٔ امضاشده با کلید خصوصی لازم است.")
    }

    private fun api(): StudioApi {
        requirePrivateSigning()
        return StudioApi(storage.device())
    }

    fun connect(input: String) = work {
        requirePrivateSigning()
        requireNoOfflineJob()
        val device = input.trim()
        if (!Regex("[1-9][0-9]{0,15}\\.[a-f0-9]{64}").matches(device)) {
            throw StudioFailure("قالب کلید اتصال معتبر نیست.")
        }
        val job = StudioApi(device).call("status").takeIf { it.has("id") }
        val current = mutable.value
        val owner = device.substringBefore('.')
        if (current.inProgress && current.owner != owner) {
            throw StudioFailure("برای ادامهٔ عملیات نیمه‌تمام، کلید همان حساب وردپرس را وارد کنید.")
        }
        storage.saveDevice(device)
        val account = if (current.owner == owner) current else StudioState(busy = true, ownerRatesApplied = current.ownerRatesApplied)
        update(account.copy(job = job, connected = true, canResumeScan = false, preview = null, owner = owner,
            message = "اتصال به استودیوی محصولات برقرار شد."))
    }

    fun disconnect() = work {
        requireNoOfflineJob()
        if (mutable.value.inProgress) return@work
        storage.disconnect()
        update(mutable.value.copy(connected = false, canResumeScan = false, preview = null, message = "کلید از این گوشی پاک شد."))
    }

    private suspend fun scan(client: StudioApi, resumable: Boolean = false): JSONObject {
        val token = if (resumable) storage.scanToken() else null
        if (!resumable) {
            storage.setScanToken(null)
            mutable.update { it.copy(canResumeScan = false) }
        }
        mutable.update { it.copy(message = if (token == null) "در حال دریافت فهرست فروشگاه" else "در حال ادامهٔ دریافت از فروشگاه") }
        val first = if (token == null) JSONObject().put("reset", true) else JSONObject().put("token", token).put("resume", true)
        var page = try {
            client.call("scan", first)
        } catch (error: StudioFailure) {
            if (token == null || error.serverCode != "tdps_scan_expired") throw error
            storage.setScanToken(null)
            mutable.update { it.copy(canResumeScan = false, message = "فهرست قبلی منقضی شده است. دریافت تازه شروع شد.") }
            client.call("scan", JSONObject().put("reset", true))
        }
        while (true) {
            val durable = resumable && page.optInt("scan_protocol") == 2
            val next = page.getString("token")
            if (durable) storage.setScanToken(next)
            if (page.getBoolean("done")) {
                mutable.update { it.copy(canResumeScan = durable, scanned = 0, scanTotal = 0,
                    message = "فهرست دریافت شد. در حال بررسی اتصال مراجع") }
                return page
            }
            currentCoroutineContext().ensureActive()
            val count = page.getInt("scanned")
            val total = page.optInt("total_products")
            val message = if (total > 0) "دریافت و بررسی ${count} از ${total} محصول" else "در حال دریافت قیمت و ابعاد · ${count} محصول"
            mutable.update { it.copy(canResumeScan = durable, scanned = count, scanTotal = total, message = message) }
            page = client.call("scan", JSONObject().put("token", next))
        }
    }

    private fun restoreReferences(client: StudioApi, catalog: JSONObject, links: Map<String, JSONObject>): JSONObject {
        val references = catalog.objects("references").associateBy { it.getString("key") }.toMutableMap()
        val missing = links.filterValues { !it.optBoolean("disabled") }.filterKeys { it !in references }
        for (batch in missing.values.toList().chunked(10)) {
            val scopes = batch.map { it.getJSONObject("scope") }
            val response = client.call("references", JSONObject().put("token", catalog.getString("token"))
                .put("scopes", JSONArray(scopes)))
            response.objects("references").forEach { references[it.getString("key")] = it }
        }
        return catalog.put("references", JSONArray(references.values.toList()))
    }

    fun saveReference(row: StudioRow, productId: String) = work {
        val current = mutable.value
        if (current.inProgress) return@work
        val scope = row.scope ?: return@work
        if (localProducts.value.none { it.product.id == productId && it.product.active })
            throw StudioFailure("محصول مرجع فعال پیدا نشد.")
        val link = JSONObject().put("store_recipe", current.links[row.key]?.optBoolean("store_recipe") == true)
            .put("local_id", productId).put("scope", scope)
            .put("label", row.label).put("title", row.sampleTitle)
        update(current.copy(links = current.links + (row.key to link), preview = null,
            message = "اتصال مرجع ذخیره شد."))
    }

    fun removeReference(key: String) = work {
        val current = mutable.value
        val existing = current.links[key] ?: return@work
        if (!current.inProgress) {
            val disabled = JSONObject(existing.toString()).put("disabled", true)
            update(current.copy(links = current.links + (key to disabled), preview = null))
        }
    }

    fun applyOwnerRates() = work {
        if (!hasOwnerRates || mutable.value.inProgress) return@work
        val raw = getApplication<TablodecoriApp>().assets.open("tdps-owner-pricing.json")
            .bufferedReader().use { it.readText() }
        repository.importOwnerPricing(raw)
        update(mutable.value.copy(ownerRatesApplied = true, preview = null,
            message = "تعرفه‌های متریال، سوابق تغییر قیمت و سود بکاپ اعمال شد."))
    }

    private suspend fun createReferences(catalog: JSONObject, current: StudioState): Map<String, JSONObject> {
        val links = current.links.toMutableMap()
        val seeds = mutableListOf<SiteReferenceSeed>()
        val seedKeys = mutableMapOf<String, MutableList<String>>()
        val canUseToman = current.copy(catalog = catalog).acceptsToman
        if (!catalog.optBoolean("reference_rules")) return links
        for (entry in catalog.objects("references")) {
            val row = StudioRow(entry)
            val scope = row.scope ?: continue
            val old = links[row.key]
            if (entry.optBoolean("reference_excluded")) continue
            if (row.pieces.isEmpty() || old?.optBoolean("disabled") == true) continue
            val localId = old?.optString("local_id") ?: (SITE_REFERENCE_PREFIX + row.key)
            if (!localId.startsWith(SITE_REFERENCE_PREFIX) || old?.optBoolean("store_recipe") == true) continue
            val pieces = studioShape(row.pieces).entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
            val dimension = pieces.joinToString(" + ") { (wh, quantity) -> "$quantity عدد ${wh.first}×${wh.second}" }
            val title = "مرجع ${row.label} · $dimension · ${row.sampleTitle.ifBlank { row.group }}"
            val price = row.prices.toLongOrNull()?.takeIf { canUseToman && row.missing == 0 }
            val keepEdited = old != null && !old.optBoolean("confirm_recipe")
            seeds += SiteReferenceSeed(localId, title, row.pieces, price, keepEdited)
            seedKeys.getOrPut(localId) { mutableListOf() }.add(row.key)
            links[row.key] = JSONObject().put("local_id", localId).put("scope", scope).put("label", row.label)
                .put("title", row.sampleTitle).put("confirm_recipe", true).put("store_recipe", true)
        }
        val results = repository.ensureSiteReferences(seeds.distinctBy { it.id })
        for ((id, profit) in results) {
            seedKeys.getValue(id).forEach { key -> links.getValue(key).put("profit_unavailable", profit == null) }
        }
        return links
    }

    fun refresh() = work {
        requireNoOfflineJob()
        val client = api()
        val job = client.call("status").takeIf { it.has("id") }
        if (job?.optString("state") == "running") {
            update(mutable.value.copy(job = job, preview = null, message = "یک عملیات قیمت در حال اجراست."))
        } else {
            val old = mutable.value
            val catalog = restoreReferences(client, scan(client, resumable = true), old.links)
            val revisions = (catalog.objects("rows") + catalog.objects("references")).associate { it.getString("key") to it.getString("revision") }
            val members = old.members.filter { (key, value) -> value.optString("revision") == revisions[key] }
            mutable.update { it.copy(message = "در حال ساخت و ذخیرهٔ مراجع روی گوشی") }
            val links = createReferences(catalog, old)
            val sizes = catalog.objects("references").mapNotNull { it.optJSONObject("scope")?.optString("size_key") }.distinct().size
            update(old.copy(catalog = catalog, members = members, links = links, job = job, preview = null,
                canResumeScan = false, updatedAt = System.currentTimeMillis(),
                message = "فهرست ذخیره شد · ${catalog.optInt("scanned")} محصول · ${sizes} اندازه" +
                    if (links.values.any { it.optBoolean("profit_unavailable") }) " · سود بعضی مراجع نیازمند ورود دستی است." else ""))
            storage.setScanToken(null)
        }
    }

    fun loadMembers(row: StudioRow, page: Int) = work {
        val old = mutable.value
        val token = old.catalog?.getString("token") ?: throw StudioFailure("ابتدا فهرست قیمت‌ها را دریافت کنید.")
        val request = JSONObject().put("token", token).put("key", row.key).put("page", page)
        if (row.scope != null) request.put("scope", row.scope)
        val members = api().call("members", request)
        val previous = old.members[row.key]?.takeIf { it.optString("revision") == row.revision }.orEmptyObjects("members")
        val combined = (previous + members.objects("members")).distinctBy { it.getLong("id") }
        members.put("members", JSONArray(combined)).put("revision", row.revision)
        update(old.copy(members = old.members + (row.key to members)))
    }

    fun saveDraft(row: StudioRow, raw: String, selected: Set<Long>?) = work {
        val current = mutable.value
        if (current.inProgress) return@work
        val amount = studioPrice(raw, current.catalog?.optInt("decimals") ?: 0)
            ?: throw StudioFailure("مبلغ با واحد و اعشار فروشگاه سازگار نیست.")
        if ((!row.grouped && selected.isNullOrEmpty()) || selected?.size == 0 || (selected?.size ?: 0) > 250) {
            throw StudioFailure("برای این تغییر، ۱ تا ۲۵۰ محصول انتخاب کنید.")
        }
        val draft = JSONObject().put("key", row.key).put("revision", row.revision)
            .put("label", row.label).put("group", row.group).put("price", amount)
        if (selected != null) draft.put("product_ids", JSONArray(selected.sorted()))
        if (row.scope != null) draft.put("scope", row.scope)
        val drafts = current.drafts.filterNot { it.getString("key") == row.key }
        if (drafts.size >= 100) throw StudioFailure("هر بار حداکثر ۱۰۰ ردیف قابل بررسی است.")
        update(current.copy(drafts = drafts + draft, preview = null, message = "پیش‌نویس روی گوشی ذخیره شد."))
    }

    fun dropDraft(key: String) = work {
        val current = mutable.value
        if (!current.inProgress) update(current.copy(drafts = current.drafts.filterNot { it.getString("key") == key }, preview = null))
    }

    fun salePolicy(policy: String) = work {
        if (!mutable.value.inProgress) update(mutable.value.copy(salePolicy = policy, preview = null))
    }

    fun review(fromReferences: Boolean = false) = work {
        requireNoOfflineJob()
        val current = mutable.value
        if (current.inProgress || (!fromReferences && current.drafts.isEmpty())) return@work
        val client = api()
        val catalog = restoreReferences(client, scan(client), current.links)
        val revisions = (catalog.objects("rows") + catalog.objects("references")).associate { it.getString("key") to it.getString("revision") }
        val members = current.members.filter { (key, value) -> value.optString("revision") == revisions[key] }
        val updated = mutable.value.copy(catalog = catalog, members = members, updatedAt = System.currentTimeMillis(), preview = null)
        update(updated)
        val proposed = if (fromReferences) referencePrices(updated) else current.drafts
        val request = JSONObject().put("token", catalog.getString("token"))
            .put("request_id", UUID.randomUUID().toString())
            .put("sale_policy", if (fromReferences) "keep" else current.salePolicy)
            .put("reference_job", fromReferences).put("rows", JSONArray(proposed))
        val review = client.call("preview", request)
        mutable.value = updated.copy(preview = review.put("request", request).put("reference_job", fromReferences))
    }

    private fun referencePrices(state: StudioState): List<JSONObject> {
        if (!state.acceptsToman) throw StudioFailure("واحد قیمت مرجع تومان است. واحد قیمت فروشگاه را بررسی کنید.")
        val rows = state.catalog?.objects("references").orEmpty().associateBy { it.getString("key") }
        val local = localProducts.value.filter { it.product.active }.associateBy { it.product.id }
        val proposals = state.links.filterValues { !it.optBoolean("disabled") && !it.optBoolean("confirm_recipe") }.mapNotNull { (key, link) ->
            val row = rows[key] ?: throw StudioFailure("دسته یا اندازهٔ یکی از مراجع تغییر کرده است. اتصال «${link.optString("title")}» را بررسی کنید.")
            if (row.optBoolean("reference_excluded")) return@mapNotNull null
            val amount = local[link.getString("local_id")]?.pricing?.finalPriceToman?.toString()
                ?: throw StudioFailure("یکی از محصولات مرجع غیرفعال یا حذف شده است.")
            val price = studioPrice(amount, state.catalog?.optInt("decimals") ?: 0)
                ?: throw StudioFailure("مبلغ یکی از محصولات مرجع معتبر نیست.")
            val latest = row.getJSONArray("prices")
            if (latest.length() == 1 && latest.getString(0) == price && row.optInt("missing") == 0) null else
                JSONObject().put("key", key).put("revision", row.getString("revision"))
                    .put("price", price).put("scope", row.getJSONObject("scope"))
        }
        if (proposals.isEmpty()) throw StudioFailure("قیمت متفاوتی در مراجع تأییدشده پیدا نشد. مراجع تازه را در فهرست بررسی کنید.")
        if (proposals.size > 100) throw StudioFailure("حداکثر ۱۰۰ ردیف در هر عملیات قابل بررسی است.")
        return proposals
    }

    private fun requireNoOfflineJob() {
        if (StudioOfflineQueue.active(offlineQueue.value)) throw StudioFailure("ارسال تغییرات آفلاین در حال پیگیری است.")
    }

    fun previewOffline(fromReferences: Boolean) = work {
        requirePrivateSigning()
        requireNoOfflineJob()
        val current = mutable.value
        if (current.inProgress || !current.connected) return@work
        val catalog = current.catalog ?: throw StudioFailure("برای شروع، یک‌بار فهرست فروشگاه را دریافت کنید.")
        val changes = if (fromReferences) referencePrices(current) else current.drafts
        if (changes.isEmpty()) return@work
        val rows = current.rows.associateBy { it.key }
        val review = changes.map { change ->
            val row = rows[change.getString("key")]
                ?.takeIf { it.revision == change.getString("revision") }
                ?: throw StudioFailure("فهرست ذخیره‌شده با یکی از پیش‌نویس‌ها سازگار نیست.")
            val selected = change.optJSONArray("product_ids")
            JSONObject().put("group_name", listOf(row.group, row.sampleTitle).filter { it.isNotBlank() }.joinToString(" · "))
                .put("label", row.label).put("before", row.prices).put("price", change.getString("price"))
                .put("products", selected?.length() ?: row.products)
        }
        val request = JSONObject().put("request_id", UUID.randomUUID().toString())
            .put("sale_policy", if (fromReferences) "keep" else current.salePolicy)
            .put("reference_job", fromReferences).put("rows", JSONArray(changes))
        mutable.value = current.copy(preview = JSONObject().put("offline_job", true)
            .put("reference_job", fromReferences).put("request", request).put("review", JSONArray(review))
            .put("currency_code", catalog.getString("currency_code")).put("decimals", catalog.getInt("decimals")))
    }

    fun cancelOffline() = work {
        val cancelled = StudioOfflineQueue.cancel(context) ?: return@work
        val request = cancelled.getJSONObject("request")
        if (!request.optBoolean("reference_job")) {
            val current = mutable.value
            val savedKeys = current.drafts.mapTo(mutableSetOf()) { it.getString("key") }
            update(current.copy(drafts = current.drafts + request.objects("rows").filter { it.getString("key") !in savedKeys }))
        }
    }

    fun start() = work {
        requireNoOfflineJob()
        val current = mutable.value
        val approved = current.preview ?: return@work
        if (approved.optBoolean("offline_job")) {
            val request = approved.getJSONObject("request")
            StudioOfflineQueue.enqueue(context, JSONObject().put("owner", current.owner)
                .put("currency_code", approved.getString("currency_code")).put("decimals", approved.getInt("decimals"))
                .put("request", request))
            val queuedKeys = request.objects("rows").mapTo(mutableSetOf()) { it.getString("key") }
            val drafts = if (approved.optBoolean("reference_job")) current.drafts else
                current.drafts.filterNot { it.getString("key") in queuedKeys }
            update(current.copy(preview = null, drafts = drafts, message = "تغییرات برای ارسال پس از اتصال ذخیره شد."))
            return@work
        }
        val request = JSONObject(approved.getJSONObject("request").toString())
            .put("receipt", approved.getString("receipt"))
        update(current.copy(pending = request, preview = null, job = null))
        continueJob(api())
    }

    fun resume() = work {
        requireNoOfflineJob()
        continueJob(api())
    }

    private suspend fun continueJob(client: StudioApi) {
        var current = mutable.value
        var job = client.call("status").takeIf { it.has("id") }
        val pending = current.pending
        if (pending != null && job?.optString("request_id") != pending.getString("request_id")) {
            try {
                job = client.call("start", pending)
            } catch (error: StudioFailure) {
                if (error.status == 400 || error.status == 409) {
                    update(current.copy(pending = null, job = job, preview = null))
                }
                throw error
            }
        }
        if (job == null || (pending == null && job.optString("id") != current.job?.optString("id"))) {
            update(current.copy(job = job, pending = null, preview = null))
            throw StudioFailure("عملیات قبلی قابل ادامه نیست. وضعیت تازهٔ فروشگاه دریافت شد.")
        }
        current = mutable.value.copy(job = job, pending = null, preview = null)
        update(current)
        var runningJob: JSONObject = job
        while (runningJob.getString("state") == "running") {
            currentCoroutineContext().ensureActive()
            runningJob = client.call("step", JSONObject().put("id", runningJob.getString("id")))
            update(mutable.value.copy(job = runningJob, message = "در حال اعمال قیمت‌ها"))
        }
        finish(runningJob)
    }

    private fun finish(job: JSONObject) {
        val current = mutable.value
        val completed = job.getString("state") == "completed"
        val drafts = if (completed && !job.optBoolean("reference_job") && job.optInt("conflicts") == 0) {
            // Clear only drafts that belong to this job. A refreshed status may describe an older job.
            val keys = job.optJSONArray("mobile_keys")
            if (keys == null) current.drafts else current.drafts.filterNot { draft ->
                (0 until keys.length()).any { keys.getString(it) == draft.getString("key") }
            }
        } else current.drafts
        update(current.copy(job = job, pending = null, drafts = drafts, message =
            if (completed) "عملیات پایان یافت. فهرست را دوباره دریافت کنید." else "عملیات متوقف شد. تغییرهای انجام‌شده در گزارش نمایش داده می‌شوند."))
    }

    fun cancel() = work {
        requireNoOfflineJob()
        val job = mutable.value.job ?: return@work
        finish(api().call("cancel", JSONObject().put("id", job.getString("id"))))
    }
}

private fun JSONObject?.orEmptyObjects(key: String): List<JSONObject> = this?.objects(key).orEmpty()

/** Stores the user's confirmed offline amount separately from editable draft and catalogue state. */
internal object StudioOfflineQueue {
    private const val workName = "studio-confirmed-offline-pricing"
    private val lock = Any()
    private val snapshot = MutableStateFlow<JSONObject?>(null)
    val state = snapshot.asStateFlow()

    fun active(value: JSONObject?) = value?.optString("phase") in setOf("queued", "sending")

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "studio-offline-pricing.json"))

    fun restore(context: Context, schedulePending: Boolean = true) {
        synchronized(lock) {
            val stored = runCatching { JSONObject(file(context).readFully().toString(Charsets.UTF_8)) }.getOrNull()
            snapshot.value = stored
            if (schedulePending && active(stored)) schedule(context)
        }
    }

    private fun save(context: Context, value: JSONObject) {
        val file = file(context)
        val stream = file.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        snapshot.value = value
    }

    private fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<StudioOfflineWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueue(context: Context, value: JSONObject) = synchronized(lock) {
        check(!active(snapshot.value))
        save(context, value.put("phase", "queued"))
        schedule(context)
    }

    fun cancel(context: Context) = synchronized(lock) {
        val value = snapshot.value ?: return@synchronized null
        if (value.optString("phase") != "queued") return@synchronized null
        save(context, JSONObject(value.toString()).put("phase", "cancelled"))
        WorkManager.getInstance(context).cancelUniqueWork(workName)
        value
    }

    fun update(context: Context, id: String, phase: String, message: String = ""): Boolean = synchronized(lock) {
        val value = snapshot.value ?: return@synchronized false
        if (!active(value) || value.getJSONObject("request").getString("request_id") != id) return@synchronized false
        save(context, JSONObject(value.toString()).put("phase", phase).put("message", message))
        true
    }
}

class StudioOfflineWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        StudioOfflineQueue.restore(context, schedulePending = false)
        val queued = StudioOfflineQueue.state.value?.takeIf(StudioOfflineQueue::active) ?: return@withContext Result.success()
        val request = JSONObject(queued.getJSONObject("request").toString())
        val id = request.getString("request_id")
        if (!StudioOfflineQueue.update(context, id, "sending")) return@withContext Result.success()
        try {
            if (publiclySignedStudioBuild(context)) throw StudioFailure("برای ارسال آفلاین، نسخهٔ دارای امضای خصوصی لازم است.")
            val device = StudioStorage(context).device()
            if (device.substringBefore('.') != queued.getString("owner"))
                throw StudioFailure("حساب فروشگاه تغییر کرده است. تغییرات را دوباره بررسی کنید.")
            val client = StudioApi(device)
            var job = client.call("status")
            if (job.optString("request_id") != id) {
                if (job.optString("state") == "running") {
                    if (runAttemptCount < 6) return@withContext Result.retry()
                    throw StudioFailure("یک عملیات قیمت دیگر در فروشگاه در جریان است.")
                }
                var catalog = client.call("scan", JSONObject().put("reset", true))
                while (!catalog.getBoolean("done")) {
                    currentCoroutineContext().ensureActive()
                    catalog = client.call("scan", JSONObject().put("token", catalog.getString("token")))
                }
                if (catalog.getString("currency_code") != queued.getString("currency_code") ||
                    catalog.getInt("decimals") != queued.getInt("decimals"))
                    throw StudioFailure("واحد یا دقت مبلغ فروشگاه تغییر کرده است.")
                request.put("token", catalog.getString("token"))
                val preview = client.call("preview", request)
                currentCoroutineContext().ensureActive()
                job = client.call("start", request.put("receipt", preview.getString("receipt")))
            }
            while (job.optString("state") == "running") {
                currentCoroutineContext().ensureActive()
                job = client.call("step", JSONObject().put("id", job.getString("id")))
            }
            val done = job.optString("state") == "completed" && job.optInt("conflicts") == 0
            StudioOfflineQueue.update(context, id, if (done) "applied" else "attention",
                "${job.optInt("updated")} قیمت اعمال شد · ${job.optInt("conflicts")} مورد نیازمند بررسی")
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: StudioFailure) {
            val retryable = error.status in 500..599 || error.status == 429 ||
                (error.status == 409 && error.serverCode == "tdps_pricing")
            if (retryable && runAttemptCount < 6) Result.retry()
            else {
                StudioOfflineQueue.update(context, id, "attention", error.message.orEmpty())
                Result.success()
            }
        } catch (_: IOException) {
            if (runAttemptCount < 6) Result.retry()
            else {
                StudioOfflineQueue.update(context, id, "attention", "ارسال کامل نشد. وضعیت فروشگاه را بررسی کنید.")
                Result.success()
            }
        } catch (_: Exception) {
            StudioOfflineQueue.update(context, id, "attention", "محدوده و قیمت تازه را در پیش‌نمایش بررسی کنید.")
            Result.success()
        }
    }
}
