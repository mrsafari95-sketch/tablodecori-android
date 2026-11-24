package com.tablodecori.app.ui.screens

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
import com.tablodecori.app.data.StudioPricingViewModel
import com.tablodecori.app.data.StudioRow
import com.tablodecori.app.data.StudioState
import com.tablodecori.app.data.objects
import com.tablodecori.app.data.studioPrice
import com.tablodecori.app.ui.AppCard
import java.text.DateFormat
import java.util.Date

@Composable
internal fun StudioPricingScreen(vm: StudioPricingViewModel, suggestion: Long?) {
    val state by vm.state.collectAsState()
    var search by rememberSaveable { mutableStateOf("") }
    var connect by rememberSaveable { mutableStateOf(false) }
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cancel by remember { mutableStateOf(false) }
    val rows = remember(state.catalog) { state.rows }
    val filtered = remember(rows, search) {
        rows.filter { "${it.group} ${it.label}".contains(search.trim(), ignoreCase = true) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard {
                Text("قیمت‌های فروشگاه", style = MaterialTheme.typography.titleLarge)
                if (vm.publicSigning) {
                    Text("برای اتصال آنلاین، نسخهٔ اندرویدی با امضای خصوصی لازم است.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { connect = true }, enabled = !state.busy) {
                            Text(if (state.connected) "تغییر کلید اتصال" else "اتصال فروشگاه")
                        }
                        if (state.connected && !state.inProgress) TextButton(onClick = vm::disconnect, enabled = !state.busy) {
                            Text("قطع اتصال")
                        }
                    }
                }
                if (state.updatedAt > 0) {
                    Text("آخرین فهرست ذخیره‌شده · ${DateFormat.getDateTimeInstance().format(Date(state.updatedAt))}")
                }
                if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                if (state.message.isNotEmpty()) Text(state.message)
                if (state.connected && !state.inProgress) {
                    Button(onClick = vm::refresh, enabled = !state.busy) { Text("دریافت قیمت‌های سایت") }
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
                Button(onClick = vm::review, enabled = state.connected && !state.busy && !state.inProgress) {
                    Text("خواندن قیمت روز و پیش‌نمایش تغییرات")
                }
            }
        }
        if (rows.isNotEmpty()) item {
            OutlinedTextField(search, { search = it }, label = { Text("جست‌وجوی گروه یا ابعاد") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (suggestion != null && state.acceptsToman) {
                Text("قیمت محاسبه‌شده · ${suggestion} تومان. برای استفاده، ردیف مقصد را انتخاب کنید.")
            }
        }
        items(filtered, key = { it.key }) { row ->
            AppCard {
                Text("${row.group} · ${row.label}", fontWeight = FontWeight.Bold)
                Text("قیمت عادی · ${row.prices.ifBlank { "ثبت نشده" }} ${state.currency}")
                Text("${row.products} محصول · ${row.count} قیمت · ${row.sales} تخفیف · ${row.missing} بدون قیمت")
                TextButton(onClick = { editorKey = row.key }, enabled = !state.inProgress && !state.busy) { Text("ویرایش قیمت") }
            }
        }
    }
    if (connect) StudioConnectDialog(onDismiss = { connect = false }, onConnect = {
        connect = false
        vm.connect(it)
    })
    val editorRow = rows.find { it.key == editorKey }
    if (editorRow != null) StudioDraftDialog(vm, state, editorRow, suggestion) { editorKey = null }
    state.preview?.let { preview ->
        var opened by remember(preview) { mutableStateOf(true) }
        if (opened) AlertDialog(
            onDismissRequest = { opened = false },
            title = { Text("تأیید نهایی تغییر قیمت") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${preview.getInt("products")} محصول · ${preview.getInt("count")} قیمت")
                    Text(if (state.salePolicy == "keep") "تخفیف‌های معتبر حفظ می‌شوند." else "تخفیف‌ها و زمان‌بندی آن‌ها حذف می‌شوند.")
                    preview.objects("review").forEach { row ->
                        Text("${row.getString("group_name")} · ${row.getString("label")}")
                        Text("${row.getString("before").ifBlank { "بدون قیمت" }} ← ${row.getString("price")} ${state.currency} · ${row.getInt("count")} قیمت")
                    }
                }
            },
            confirmButton = { Button(onClick = vm::start, enabled = !state.busy) { Text("اعمال در فروشگاه") } },
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
