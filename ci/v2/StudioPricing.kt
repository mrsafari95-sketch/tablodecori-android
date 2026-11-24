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
import com.tablodecori.app.TablodecoriApp
import com.tablodecori.app.pricing.PieceInput
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
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
    val sampleTitle: String, val thumbnail: String, val description: String, val pieces: List<PieceInput>
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
        sample?.objects("pieces")?.map { PieceInput(it.getInt("width"), it.getInt("height"), it.getInt("quantity")) }.orEmpty()
    )
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
    val busy: Boolean = false,
    val message: String = ""
) {
    val rows get() = catalog?.let { (it.objects("references") + it.objects("rows")).map(::StudioRow) }.orEmpty()
    val linkedProducts get() = links.filterValues { !it.optBoolean("disabled") }.mapValues { it.value.getString("local_id") }
    val currency get() = catalog?.optString("currency").orEmpty()
    val acceptsToman get() = catalog?.optString("currency_code") in setOf("IRT", "TOMAN") || currency.trim() == "تومان"
    val inProgress get() = job?.optString("state") == "running" || pending != null
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

private class StudioFailure(message: String, val status: Int = 0) : IOException(message)

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
        check(preferences.edit().putString("device", encoded).commit())
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
        check(preferences.edit().remove("device").commit())
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
                    ?: "درخواست فروشگاه انجام نشد (خطای ${code}).", code
            )
            return response ?: throw StudioFailure("پاسخ فروشگاه معتبر نیست.")
        } finally {
            connection.disconnect()
        }
    }
}

internal class StudioPricingViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = StudioStorage(application)
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
                mutable.value = withContext(Dispatchers.IO) { storage.load() }
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
            } catch (_: IOException) {
                mutable.update { it.copy(message = "ارتباط یا ذخیره‌سازی انجام نشد. می‌توانید ادامهٔ کار را دوباره امتحان کنید.") }
            } catch (_: Exception) {
                mutable.update { it.copy(message = "پردازش اطلاعات فروشگاه انجام نشد.") }
            } finally {
                mutable.update { it.copy(busy = false) }
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
        update(account.copy(job = job, connected = true, preview = null, owner = owner,
            message = "اتصال به استودیوی محصولات برقرار شد."))
    }

    fun disconnect() = work {
        if (mutable.value.inProgress) return@work
        storage.disconnect()
        update(mutable.value.copy(connected = false, preview = null, message = "کلید از این گوشی پاک شد."))
    }

    private suspend fun scan(client: StudioApi): JSONObject {
        var page = client.call("scan", JSONObject().put("reset", true))
        while (!page.getBoolean("done")) {
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(message = "در حال خواندن قیمت‌ها · ${page.getInt("scanned")} محصول") }
            page = client.call("scan", JSONObject().put("token", page.getString("token")))
        }
        return page
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
        val link = JSONObject().put("local_id", productId).put("scope", scope)
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
        val ownersByDimensions = links.filterValues { !it.optBoolean("disabled") }.values
            .groupBy { it.getJSONObject("scope").getString("size_key") }
            .mapValues { (_, linked) -> linked.flatMap { link ->
                val ids = link.getJSONObject("scope").getJSONArray("product_ids")
                List(ids.length()) { ids.getLong(it) }
            }.toMutableSet() }
            .toMutableMap()
        val pendingSeeds = mutableListOf<SiteReferenceSeed>()
        for (row in catalog.objects("references").map(::StudioRow)) {
            val scope = row.scope ?: continue
            if (row.key in links || row.pieces.isEmpty()) continue
            val ids = scope.getJSONArray("product_ids")
            val members = List(ids.length()) { ids.getLong(it) }
            val claimed = ownersByDimensions.getOrPut(scope.getString("size_key")) { mutableSetOf() }
            if (members.any { it in claimed }) continue
            val id = SITE_REFERENCE_PREFIX + row.key
            val title = listOf(row.sampleTitle.ifBlank { row.group }, row.label).joinToString(" · ")
            pendingSeeds += SiteReferenceSeed(id, title, row.pieces)
            links[row.key] = JSONObject().put("local_id", id).put("scope", scope).put("label", row.label)
                .put("title", row.sampleTitle).put("confirm_recipe", true)
            claimed.addAll(members)
        }
        repository.ensureSiteReferences(pendingSeeds)
        return links
    }

    fun refresh() = work {
        val client = api()
        val job = client.call("status").takeIf { it.has("id") }
        if (job?.optString("state") == "running") {
            update(mutable.value.copy(job = job, preview = null, message = "یک عملیات قیمت در حال اجراست."))
        } else {
            val old = mutable.value
            val catalog = restoreReferences(client, scan(client), old.links)
            val revisions = (catalog.objects("rows") + catalog.objects("references")).associate { it.getString("key") to it.getString("revision") }
            val members = old.members.filter { (key, value) -> value.optString("revision") == revisions[key] }
            val links = createReferences(catalog, old)
            val created = links.size - old.links.size
            update(old.copy(catalog = catalog, members = members, links = links, job = job, preview = null,
                updatedAt = System.currentTimeMillis(), message = "فهرست قیمت فروشگاه دریافت شد · ${created} مرجع ساخته شد."))
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

    fun start() = work {
        val current = mutable.value
        val approved = current.preview ?: return@work
        val request = JSONObject(approved.getJSONObject("request").toString())
            .put("receipt", approved.getString("receipt"))
        update(current.copy(pending = request, preview = null, job = null))
        continueJob(api())
    }

    fun resume() = work { continueJob(api()) }

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
        val job = mutable.value.job ?: return@work
        finish(api().call("cancel", JSONObject().put("id", job.getString("id"))))
    }
}

private fun JSONObject?.orEmptyObjects(key: String): List<JSONObject> = this?.objects(key).orEmpty()
