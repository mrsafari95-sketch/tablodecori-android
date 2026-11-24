package com.tablodecori.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
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
    var showReferences by rememberSaveable { mutableStateOf(true) }
    var search by rememberSaveable { mutableStateOf("") }
    var connect by rememberSaveable { mutableStateOf(false) }
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cancel by remember { mutableStateOf(false) }
    val rows = remember(state.catalog) { state.rows }
    val hasReferences = rows.any { it.scope != null }
    val filtered = remember(rows, search, showReferences) {
        rows.filter { row ->
            (!hasReferences || (row.scope != null) == showReferences) &&
                "${row.group} ${row.label} ${row.sampleTitle} ${row.description}".contains(search.trim(), ignoreCase = true)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard {
                Text("محصولات سایت", style = MaterialTheme.typography.titleLarge)
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
                if (state.updatedAt > 0) {
                    Text("آخرین فهرست ذخیره‌شده · ${DateFormat.getDateTimeInstance().format(Date(state.updatedAt))}")
                }
                if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                if (state.message.isNotEmpty()) Text(state.message)
                if (vm.hasOwnerRates && !state.ownerRatesApplied && !state.inProgress) {
                    OutlinedButton(onClick = vm::applyOwnerRates, enabled = !state.busy) {
                        Text("اعمال تعرفه‌های بکاپ ارسالی")
                    }
                }
                if (state.connected && !state.inProgress) {
                    Button(onClick = vm::refresh, enabled = !state.busy && !sendingOffline && (!vm.hasOwnerRates || state.ownerRatesApplied)) {
                        Text("دریافت محصولات و ساخت مراجع")
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
        state.job?.let { job ->
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
        if (state.linkedProducts.isNotEmpty()) item {
            AppCard {
                Text("${state.linkedProducts.size} ردیف متصل به محصول مرجع", fontWeight = FontWeight.SemiBold)
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
        if (rows.isNotEmpty()) item {
            if (hasReferences) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = showReferences, onClick = { showReferences = true }, label = { Text("مراجع قیمت") })
                    FilterChip(selected = !showReferences, onClick = { showReferences = false }, label = { Text("قیمت‌گذاری دستی") })
                }
            }
            OutlinedTextField(search, { search = it }, label = { Text("جست‌وجوی گروه یا ابعاد") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (suggestion != null && state.acceptsToman) {
                Text("قیمت محاسبه‌شده · ${suggestion} تومان. برای استفاده، ردیف مقصد را انتخاب کنید.")
            }
        }
        items(filtered, key = { it.key }) { row ->
            AppCard {
                StudioRowHeading(row)
                Text("قیمت عادی · ${row.prices.ifBlank { "ثبت نشده" }} ${state.currency}")
                Text("${row.products} محصول · ${row.count} قیمت · ${row.sales} تخفیف · ${row.missing} بدون قیمت")
                if (row.scope != null) {
                    val linkedId = state.linkedProducts[row.key]
                    if (row.pieces.isEmpty() && linkedId == null) {
                        Text("ترکیب تکه‌ها از توضیح سایت قابل استخراج نبود.", color = MaterialTheme.colorScheme.error)
                    }
                    if (linkedId != null) {
                        val model = local[linkedId]
                        Text(if (model == null) "محصول مرجع روی گوشی در دسترس نیست" else
                            "${model.product.name} · ${model.pricing.finalPriceToman} تومان")
                        if (state.links[row.key]?.optBoolean("confirm_recipe") == true)
                            Text("مرجع ساخته شد. ترکیب و مبلغ را بررسی و اتصال را تأیید کنید.")
                        if (model != null && linkedId.startsWith(SITE_REFERENCE_PREFIX)) {
                            TextButton(onClick = { editModel = linkedId }, enabled = !state.inProgress && !state.busy) {
                                Text("ویرایش ترکیب و هزینهٔ مرجع")
                            }
                        }
                    }
                    TextButton(onClick = { referenceKey = row.key }, enabled = !state.inProgress && !state.busy) {
                        Text(if (linkedId == null) "وصل کردن محصول مرجع" else "ویرایش اتصال مرجع")
                    }
                } else {
                    TextButton(onClick = { editorKey = row.key }, enabled = !state.inProgress && !state.busy) {
                        Text("ویرایش قیمت")
                    }
                }
            }
        }
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
    var amount by rememberSaveable(row.revision) { mutableStateOf(previous?.optString("price").orEmpty()) }
    var all by rememberSaveable(row.revision) { mutableStateOf(row.grouped && previous != null && previousIds == null) }
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
private fun StudioThumbnail(address: String, title: String) {
    val image by produceState<Bitmap?>(initialValue = null, key1 = address) {
        value = null
        if (address.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                studioThumbnails.get(address) ?: loadStudioThumbnail(address)?.also { studioThumbnails.put(address, it) }
            }
        }
    }
    Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap == null) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        } else {
            Image(bitmap.asImageBitmap(), contentDescription = title,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
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
