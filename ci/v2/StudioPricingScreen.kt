package com.tablodecori.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.io.File
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.data.PricedProduct
import com.tablodecori.app.data.SITE_REFERENCE_PREFIX
import com.tablodecori.app.data.StudioPricingViewModel
import com.tablodecori.app.data.StudioOfflineQueue
import com.tablodecori.app.data.StudioRow
import com.tablodecori.app.data.StudioState
import com.tablodecori.app.data.objects
import com.tablodecori.app.data.studioCards
import com.tablodecori.app.data.studioSearchText
import com.tablodecori.app.data.studioPrice
import com.tablodecori.app.ui.AppCard
import java.text.DateFormat
import java.util.Date

@Composable
internal fun StudioPricingScreen(vm: StudioPricingViewModel, workshop: MainViewModel, suggestion: Long?) {
    val state by vm.state.collectAsState()
    val queued by vm.offlineQueue.collectAsState()
    val sendingOffline = StudioOfflineQueue.active(queued)
    val products by vm.localProducts.collectAsState()
    val materials by workshop.materials.collectAsState()
    var editModel by rememberSaveable { mutableStateOf<String?>(null) }
    val local = remember(products) { products.filter { it.product.active }.associateBy { it.product.id } }
    var referenceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openCard by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSection by rememberSaveable { mutableStateOf<String?>(null) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var connect by rememberSaveable { mutableStateOf(false) }
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cancel by remember { mutableStateOf(false) }
    val rows = remember(state.catalog) { state.rows }
    val referenceRows = remember(rows) { rows.filter { it.scope != null } }
    val bySize = remember(referenceRows) { referenceRows.groupBy { it.scope!!.getString("size_key") } }
    val linkedProducts = remember(state.links) { state.linkedProducts }
    val cards = remember(state.catalog, referenceRows) { studioCards(state.catalog, referenceRows) }
    val sections = listOf("group" to "گروه‌بندی‌شده", "suggested" to "دسته‌های پیشنهادی", "review" to "نیازمند بررسی")
    val section = selectedSection ?: if (cards.any { it.kind == "suggested" }) "suggested" else cards.firstOrNull()?.kind
    val filtered = remember(cards, section, search) {
        val query = studioSearchText(search)
        cards.filter { it.kind == section }.mapNotNull { card ->
            val all = query.isEmpty() || studioSearchText(card.name).contains(query, ignoreCase = true)
            val matches = if (all) card.rows else card.rows.filter { row ->
                studioSearchText("${row.label} ${row.sampleTitle} ${row.description}").contains(query, ignoreCase = true)
            }
            if (!all && matches.isEmpty()) null else card to matches
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("محصولات سایت", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (state.catalog != null) TextButton(onClick = { showTools = !showTools }) {
                        Text(if (showTools) "بستن ابزارها" else "ابزارهای قیمت")
                    }
                }
                if (state.catalog == null || showTools || !state.connected) {
                    if (vm.publicSigning) {
                        Text("برای اتصال آنلاین، نسخهٔ اندرویدی با امضای خصوصی لازم است.")
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { connect = true }, enabled = !state.busy && !sendingOffline) {
                                Text(if (state.connected) "تغییر کلید اتصال" else "اتصال فروشگاه")
                            }
                            if (state.connected && !state.inProgress) TextButton(onClick = vm::disconnect, enabled = !state.busy && !sendingOffline) {
                                Text("قطع اتصال")
                            }
                        }
                    }
                }
                if (state.updatedAt > 0 && showTools) {
                    Text("آخرین فهرست ذخیره‌شده · ${DateFormat.getDateTimeInstance().format(Date(state.updatedAt))}")
                }
                if (state.busy && state.scanTotal > 0) {
                    LinearProgressIndicator(progress = { (state.scanned.toFloat() / state.scanTotal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                } else if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (state.message.isNotEmpty()) Text(state.message)
                if (vm.hasOwnerRates && !state.ownerRatesApplied && !state.inProgress) {
                    OutlinedButton(onClick = vm::applyOwnerRates, enabled = !state.busy) {
                        Text("اعمال تعرفه‌های بکاپ ارسالی")
                    }
                }
                if (state.connected && !state.inProgress && (state.catalog == null || showTools || state.canResumeScan)) {
                    Button(onClick = vm::refresh, enabled = !state.busy && !sendingOffline && (!vm.hasOwnerRates || state.ownerRatesApplied)) {
                        Text(if (state.canResumeScan) "ادامهٔ دریافت محصولات" else "دریافت محصولات و ساخت مراجع")
                    }
                }
            }
        }
        queued?.let { queue ->
            item {
                val phase = queue.optString("phase")
                val title = when (phase) {
                    "queued" -> "تغییرات آفلاین در صف ارسال"
                    "sending" -> "در حال بررسی و ارسال تغییرات آفلاین"
                    "applied" -> "تغییرات آفلاین به فروشگاه رسید"
                    "attention" -> "تغییرات آفلاین نیازمند بررسی"
                    else -> "ارسال آفلاین لغو شد"
                }
                AppCard {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    if (phase == "queued") {
                        Text("${queue.getJSONObject("request").getJSONArray("rows").length()} ردیف · ارسال با اینترنت")
                        TextButton(onClick = vm::cancelOffline, enabled = !state.busy) { Text("لغو ارسال") }
                    } else {
                        queue.optString("message").takeIf { it.isNotBlank() }?.let { Text(it) }
                    }
                }
            }
        }
        state.job?.takeIf { showTools || it.optString("state") == "running" || it.optInt("conflicts") > 0 }?.let { job ->
            item {
                AppCard {
                    val status = when (job.optString("state")) {
                        "running" -> "در حال اجرا"
                        "completed" -> "پایان‌یافته"
                        "cancelled" -> "متوقف‌شده"
                        else -> "نامشخص"
                    }
                    Text("آخرین عملیات · ${status}", fontWeight = FontWeight.Bold)
                    Text("بررسی‌شده ${job.optInt("cursor")} از ${job.optInt("total")} محصول")
                    Text("${job.optInt("updated")} قیمت تغییر کرد · ${job.optInt("unchanged")} بدون تغییر · ${job.optInt("conflicts")} مغایرت")
                    val errors = job.optJSONArray("errors")
                    for (index in 0 until (errors?.length() ?: 0)) Text(errors!!.getString(index))
                    if (job.optString("state") == "running") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::resume, enabled = !state.busy) { Text("ادامهٔ اعمال") }
                        TextButton(onClick = { cancel = true }, enabled = !state.busy) { Text("توقف") }
                    }
                }
            }
        }
        if (state.pending != null && state.job?.optString("state") != "running") item {
            AppCard {
                Text("درخواست تأییدشده در انتظار بررسی وضعیت است.")
                Button(onClick = vm::resume, enabled = !state.busy) { Text("بررسی و ادامه") }
            }
        }
        if (state.drafts.isNotEmpty()) item {
            AppCard {
                Text("پیش‌نویس‌های قیمت · ${state.drafts.size} ردیف", fontWeight = FontWeight.Bold)
                for (draft in state.drafts) {
                    val key = draft.getString("key")
                    val current = rows.find { it.key == key }
                    val stale = current?.revision != draft.getString("revision")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${draft.getString("group")} · ${draft.getString("label")} · ${draft.getString("price")} ${state.currency}")
                            if (stale) Text("ردیف سایت تغییر کرده و باید دوباره انتخاب شود.", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { editorKey = key }, enabled = current != null && !state.busy && !state.inProgress) {
                            Text("ویرایش")
                        }
                        TextButton(onClick = { vm.dropDraft(key) }, enabled = !state.busy && !state.inProgress) { Text("حذف") }
                    }
                }
                Text("روش برخورد با تخفیف")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.salePolicy == "keep", onClick = { vm.salePolicy("keep") },
                        label = { Text("حفظ تخفیف معتبر") }, enabled = !state.busy && !state.inProgress)
                    FilterChip(selected = state.salePolicy == "exact", onClick = { vm.salePolicy("exact") },
                        label = { Text("قیمت نهایی یکسان") }, enabled = !state.busy && !state.inProgress)
                }
                Text(if (state.salePolicy == "keep") "تخفیفی که برابر یا بیشتر از قیمت جدید شود حذف خواهد شد."
                    else "قیمتِ تخفیف و زمان‌بندی تخفیفِ موارد انتخاب‌شده حذف می‌شود.")
                Button(onClick = { vm.review() }, enabled = state.connected && !state.busy && !state.inProgress && !sendingOffline) {
                    Text("خواندن قیمت روز و پیش‌نمایش تغییرات")
                }
                TextButton(onClick = { vm.previewOffline(fromReferences = false) },
                    enabled = state.connected && !state.busy && !state.inProgress && !sendingOffline) {
                    Text("ثبت آفلاین پیش‌نویس‌ها")
                }
            }
        }
        if (showTools && state.linkedProducts.isNotEmpty()) item {
            AppCard {
                Text("قیمت‌های محاسبه‌شده از مراجع متصل", fontWeight = FontWeight.SemiBold)
                val foundKeys = rows.mapTo(mutableSetOf()) { it.key }
                state.links.filterKeys { it !in foundKeys && it in state.linkedProducts }.forEach { (key, link) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نیازمند بررسی · ${link.optString("title")} · ${link.optString("label")}", Modifier.weight(1f))
                        TextButton(onClick = { vm.removeReference(key) }, enabled = !state.inProgress && !state.busy) {
                            Text("جدا کردن")
                        }
                    }
                }
                Button(onClick = { vm.review(fromReferences = true) },
                    enabled = state.connected && !state.busy && !state.inProgress && !sendingOffline && local.isNotEmpty()) {
                    Text("پیش‌نمایش قیمت مراجع")
                }
                TextButton(onClick = { vm.previewOffline(fromReferences = true) },
                    enabled = state.connected && !state.busy && !state.inProgress && !sendingOffline && local.isNotEmpty()) {
                    Text("ثبت آفلاین قیمت مراجع")
                }
            }
        }
        if (state.catalog != null) item {
            Text("${bySize.size} اندازهٔ مرجع · ${state.catalog?.optInt("scanned") ?: 0} محصول", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sections.forEach { (kind, name) ->
                    FilterChip(selected = section == kind, onClick = { selectedSection = kind; openCard = null },
                        label = { Text("$name (${cards.count { it.kind == kind }})") })
                }
            }
            OutlinedTextField(search, { search = it }, label = { Text("جست‌وجوی دسته یا اندازه") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (state.catalog != null && filtered.isEmpty()) item {
            Text(if (search.isBlank()) "در این بخش دسته‌ای ثبت نشده است." else "دسته یا اندازه‌ای با این عبارت پیدا نشد.")
        }
        items(filtered, key = { it.first.key }) { (card, matches) ->
            val expanded = openCard == card.key
            val pictures = card.images.ifEmpty {
                card.rows.filter { it.thumbnail.isNotEmpty() }.map { it.thumbnail to it.sampleTitle }.distinctBy { it.first }
            }
            AppCard {
                Row(Modifier.fillMaxWidth().clickable { openCard = if (expanded) null else card.key },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (card.kind == "suggested") "${card.rows.size} اندازه با جدول قیمت یکسان" else card.name,
                            fontWeight = FontWeight.SemiBold)
                        Text("${card.products} محصول · ${card.rows.size} اندازه", style = MaterialTheme.typography.bodySmall)
                    }
                    pictures.take(3).forEach { (image, title) -> StudioThumbnail(image, title, 32) }
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "بستن دسته" else "بازکردن دسته")
                }
                if (expanded) {
                    if (card.rows.isEmpty()) Text("برای این دسته اندازهٔ قابل قیمت‌گذاری پیدا نشد. مشخصات محصول را در استودیو بررسی کنید.")
                    matches.forEach { row ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudioThumbnail(row.thumbnail, row.sampleTitle, 32)
                            Column(Modifier.weight(1f).clickable { detailsKey = row.key }) {
                                Text(row.label, fontWeight = FontWeight.SemiBold)
                                val price = row.prices.toLongOrNull()?.let { java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("fa-IR")).format(it) }
                                    ?: row.prices.ifBlank { "ثبت نشده" }
                                Text("$price ${state.currency}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { editorKey = row.key }, enabled = !state.inProgress && !state.busy) { Text("ویرایش قیمت") }
                            IconButton(onClick = { detailsKey = row.key }) { Icon(Icons.Rounded.MoreVert, "جزئیات مرجع") }
                        }
                    }
                }
            }
        }
    }
    rows.find { it.key == detailsKey }?.let { row ->
        val linkedId = linkedProducts[row.key]
        AlertDialog(onDismissRequest = { detailsKey = null }, title = { Text("جزئیات مرجع") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioRowHeading(row)
                Text("${row.products} محصول · ${row.count} قیمت")
                val model = linkedId?.let(local::get)
                if (model != null) Text("${model.product.name} · ${model.pricing.finalPriceToman} تومان")
                if (row.pieces.isEmpty() && model == null) Text("ترکیب تکه‌ها در توضیح این اندازه مشخص نشده است.")
                if (state.links[row.key]?.optBoolean("confirm_recipe") == true) Text("ترکیب و مبلغ را بررسی و اتصال را تأیید کنید.")
                if (model != null && linkedId?.startsWith(SITE_REFERENCE_PREFIX) == true) {
                    TextButton(onClick = { detailsKey = null; editModel = linkedId }, enabled = !state.inProgress && !state.busy) { Text("ویرایش ترکیب و هزینهٔ مرجع") }
                }
                TextButton(onClick = { detailsKey = null; referenceKey = row.key }, enabled = !state.inProgress && !state.busy) {
                    Text(if (linkedId == null) "اتصال محصول مرجع" else "ویرایش اتصال مرجع")
                }
            }
        }, confirmButton = { TextButton(onClick = { detailsKey = null }) { Text("بستن") } })
    }
    if (connect) StudioConnectDialog(onDismiss = { connect = false }, onConnect = {
        connect = false
        vm.connect(it)
    })
    rows.find { it.key == referenceKey }?.let { row ->
        val linkedId = state.linkedProducts[row.key]
        val available = local.values.filter { it.product.id == linkedId || !it.product.id.startsWith(SITE_REFERENCE_PREFIX) }
        StudioReferenceDialog(row, state, available, onDismiss = { referenceKey = null },
            onLink = { productId -> vm.saveReference(row, productId); referenceKey = null },
            onUnlink = { vm.removeReference(row.key); referenceKey = null })
    }
    editModel?.let(local::get)?.product?.let { model ->
        ProductDialog(workshop, materials, model, { editModel = null }) { id, name, pieces, ids, profit,
            mode, formula, packaging, type, design, photo, remove ->
            workshop.saveProduct(id, name, pieces, ids, manualProfit = profit, profitMode = mode,
                profitFormula = formula, packagingSizeKey = packaging, productType = type,
                designMaterialsCostToman = design, selectedPhotoUri = photo, removePhoto = remove)
            editModel = null
        }
    }
    val editorRow = rows.find { it.key == editorKey }
    if (editorRow != null) StudioDraftDialog(vm, state, editorRow, suggestion) { editorKey = null }
    state.preview?.let { preview ->
        var opened by remember(preview) { mutableStateOf(true) }
        val offline = preview.optBoolean("offline_job")
        if (opened) AlertDialog(
            onDismissRequest = { opened = false },
            title = { Text(if (offline) "تأیید ارسال آفلاین قیمت" else "تأیید نهایی تغییر قیمت") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (offline) Text("مبلغ‌ها با اولین اتصال ارسال می‌شوند. اگر قیمت یا محدوده در سایت تغییر کرده باشد، ارسال متوقف می‌شود.")
                    else Text("${preview.getInt("products")} محصول · ${preview.getInt("count")} قیمت")
                    Text(if (preview.optBoolean("reference_job") || state.salePolicy == "keep")
                        "تخفیف‌های معتبر حفظ می‌شوند." else "تخفیف‌ها و زمان‌بندی آن‌ها حذف می‌شوند.")
                    preview.objects("review").forEach { row ->
                        Text("${row.getString("group_name")} · ${row.getString("label")}")
                        val scopeLabel = if (offline) "${row.getInt("products")} محصول" else "${row.getInt("count")} قیمت"
                        Text("${row.getString("before").ifBlank { "بدون قیمت" }} ← ${row.getString("price")} ${state.currency} · $scopeLabel")
                    }
                }
            },
            confirmButton = { Button(onClick = vm::start, enabled = !state.busy) {
                Text(if (offline) "ذخیره در صف ارسال" else "اعمال در فروشگاه")
            } },
            dismissButton = { TextButton(onClick = { opened = false }) { Text("بازگشت") } }
        )
    }
    if (cancel) AlertDialog(
        onDismissRequest = { cancel = false },
        title = { Text("توقف اعمال قیمت‌ها") },
        text = { Text("قیمت‌هایی که تا این لحظه اعمال شده‌اند در فروشگاه باقی می‌مانند.") },
        confirmButton = { Button(onClick = { cancel = false; vm.cancel() }) { Text("توقف عملیات") } },
        dismissButton = { TextButton(onClick = { cancel = false }) { Text("ادامه") } }
    )
}

@Composable
private fun StudioConnectDialog(onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اتصال فروشگاه تابلو دکوری") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("از پیشخوان ووکامرس، بخش «اتصال اندروید به استودیو»، کلید اتصال را بردارید.")
                OutlinedTextField(code, { code = it.take(82) }, label = { Text("کلید اتصال") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = { Button(onClick = { onConnect(code) }, enabled = code.isNotBlank()) { Text("اتصال") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun StudioDraftDialog(
    vm: StudioPricingViewModel, state: StudioState, row: StudioRow, suggestion: Long?, onDismiss: () -> Unit
) {
    val previous = state.drafts.find { it.getString("key") == row.key && it.getString("revision") == row.revision }
    val previousIds = previous?.optJSONArray("product_ids")
    var amount by rememberSaveable(row.revision) { mutableStateOf(previous?.optString("price") ?: row.prices.takeIf { it.toBigDecimalOrNull() != null }.orEmpty()) }
    var all by rememberSaveable(row.revision) { mutableStateOf(row.grouped && previousIds == null && (row.scope != null || previous != null)) }
    var selected by rememberSaveable(row.revision) {
        mutableStateOf(ArrayList((0 until (previousIds?.length() ?: 0)).map { previousIds!!.getLong(it) }))
    }
    val members = state.members[row.key]?.takeIf { it.optString("revision") == row.revision }
    val page = members?.optInt("page") ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${row.group} · ${row.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioRowHeading(row)
                OutlinedTextField(amount, { amount = it.take(26) }, label = { Text("قیمت جدید (${state.currency})") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (suggestion != null && state.acceptsToman) {
                    TextButton(onClick = { amount = suggestion.toString() }) { Text("استفاده از قیمت محاسبه‌شده") }
                }
                if (row.grouped) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = all, onCheckedChange = { all = it })
                    Text("همهٔ ${row.products} محصول این ردیف")
                }
                if (!all) {
                    Text("${selected.size} محصول انتخاب شده")
                    for (product in members?.objects("members").orEmpty()) {
                        val id = product.getLong("id")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in selected, onCheckedChange = { checked ->
                                selected = ArrayList(if (checked) selected + id else selected - id)
                            })
                            Text(product.getString("title") + " · #" + id)
                        }
                    }
                    if (page < (members?.optInt("pages") ?: 1)) {
                        TextButton(onClick = { vm.loadMembers(row, page + 1) }, enabled = state.connected && !state.busy) {
                            Text(if (page == 0) "دریافت فهرست محصولات" else "محصولات بیشتر")
                        }
                    }
                    if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.saveDraft(row, amount, if (all) null else selected.toSet())
                onDismiss()
            }, enabled = !state.busy && studioPrice(amount, state.catalog?.optInt("decimals") ?: 0) != null &&
                (all || selected.size in 1..250)) { Text("ذخیره پیش‌نویس") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun StudioRowHeading(row: StudioRow) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (row.scope != null) StudioThumbnail(row.thumbnail, row.sampleTitle)
        Column(Modifier.weight(1f)) {
            Text("${row.group} · ${row.label}", fontWeight = FontWeight.Bold)
            if (row.sampleTitle.isNotBlank()) Text(row.sampleTitle, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (row.description.isNotBlank()) Text(row.description, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StudioReferenceDialog(
    row: StudioRow, state: StudioState, products: List<PricedProduct>,
    onDismiss: () -> Unit, onLink: (String) -> Unit, onUnlink: () -> Unit
) {
    val linked = state.linkedProducts[row.key]
    var selected by rememberSaveable(row.key) { mutableStateOf(linked) }
    var search by rememberSaveable { mutableStateOf("") }
    val matches = remember(products, search) { products.filter { it.product.name.contains(search.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محصول مرجع قیمت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioRowHeading(row)
                Text("قیمت سایت · ${row.prices.ifBlank { "ثبت نشده" }} ${state.currency}")
                Text("محدودهٔ اتصال · همان اندازه در ${row.products} محصول")
                OutlinedTextField(search, { search = it }, label = { Text("جست‌وجوی محصول ساخته‌شده در گوشی") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (matches.isEmpty()) Text("محصولی پیدا نشد. مدل مرجع و اندازهٔ تکه‌ها را در بخش محصولات گوشی تعریف کنید.")
                matches.sortedByDescending { it.product.id == linked }.forEach { item ->
                    val composition = item.product.pieces.joinToString("، ") {
                        "${it.quantity} عدد ${it.widthCm}×${it.heightCm}"
                    }
                    FilterChip(selected = selected == item.product.id, onClick = { selected = item.product.id },
                        label = {
                            Column {
                                Text(item.product.name)
                                Text(composition, style = MaterialTheme.typography.bodySmall)
                                Text("${item.pricing.finalPriceToman} تومان", style = MaterialTheme.typography.bodySmall)
                            }
                        }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(onClick = { selected?.let(onLink) }, enabled = products.any { it.product.id == selected }) {
                Text("ذخیره اتصال")
            }
        },
        dismissButton = {
            Row {
                if (linked != null) TextButton(onClick = onUnlink) { Text("جدا کردن مرجع") }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        }
    )
}

private val studioThumbnails = LruCache<String, Bitmap>(80)

@Composable
private fun StudioThumbnail(address: String, title: String, sizeDp: Int = 48) {
    val directory = File(LocalContext.current.cacheDir, "studio-thumbnails")
    val image by produceState<Bitmap?>(initialValue = null, key1 = address) {
        value = null
        if (address.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                studioThumbnails.get(address) ?: cachedStudioThumbnail(directory, address)?.also { studioThumbnails.put(address, it) }
            }
        }
    }
    Box(Modifier.size(sizeDp.dp).clip(MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap == null) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        } else {
            Image(bitmap.asImageBitmap(), contentDescription = title,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun cachedStudioThumbnail(directory: File, address: String): Bitmap? {
    val key = MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).joinToString("") { "%02x".format(it) }
    val file = File(directory, "$key.png")
    val cached = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
    if (cached != null && System.currentTimeMillis() - file.lastModified() < 86_400_000L) return cached
    val image = loadStudioThumbnail(address) ?: return cached
    runCatching {
        directory.mkdirs()
        val temp = File.createTempFile("photo-", ".tmp", directory)
        try {
            val wrote = temp.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (wrote && temp.renameTo(file)) {
                val files = directory.listFiles { item -> item.extension == "png" }.orEmpty().sortedBy { it.lastModified() }
                var bytes = files.sumOf { it.length() }
                for (old in files) {
                    if (bytes <= 12L * 1024 * 1024) break
                    val size = old.length()
                    if (old.delete()) bytes -= size
                }
            }
        } finally {
            temp.delete()
        }
    }
    return image
}

private fun loadStudioThumbnail(address: String): Bitmap? = runCatching {
    val url = URL(address)
    if (url.protocol != "https" || url.host !in setOf("tablodecori.ir", "www.tablodecori.ir") ||
        url.userInfo != null || url.port !in listOf(-1, 443)) return null
    val connection = url.openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        if (connection.responseCode != 200) return null
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var read = input.read(buffer)
            while (read != -1) {
                if (output.size() + read > 512 * 1024) return null
                output.write(buffer, 0, read)
                read = input.read(buffer)
            }
            output.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
        val sample = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / 144).coerceAtLeast(1))
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } finally {
        connection.disconnect()
    }
}.getOrNull()
