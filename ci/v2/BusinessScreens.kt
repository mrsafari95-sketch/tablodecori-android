package com.tablodecori.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.TablodecoriApp
import com.tablodecori.app.data.MonthlySummary
import com.tablodecori.app.data.PricedProduct
import com.tablodecori.app.data.db.OrderWithCosts
import com.tablodecori.app.data.db.SentOrderEntity
import com.tablodecori.app.ui.*
import com.tablodecori.app.util.*
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable fun PricebookScreen(vm:MainViewModel){
    val all by vm.pricedProducts.collectAsState(); val context=LocalContext.current;var q by remember{mutableStateOf("")};var sort by remember{mutableStateOf("name")}
    val list=all.filter{it.product.active&&(q.isBlank()||it.product.name.contains(q))}.let{when(sort){"price"->it.sortedBy{p->p.pricing.finalPriceToman};"pieces"->it.sortedBy{p->p.pricing.pieceCount};else->it.sortedBy{p->p.product.name}}}
    val csvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)runCatching{context.contentResolver.openOutputStream(uri)?.use{it.write(Exporters.pricebookCsv(list).toByteArray(Charsets.UTF_8))}}.onSuccess{vm.notify("فایل CSV ذخیره شد.")}.onFailure{vm.notify("ذخیره فایل ناموفق بود.")}}
    val pdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->if(uri!=null)runCatching{context.contentResolver.openOutputStream(uri)?.use{Exporters.writePricebookPdf(it,list)}}.onSuccess{vm.notify("فایل PDF ذخیره شد.")}.onFailure{vm.notify("ذخیره فایل ناموفق بود.")}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("قیمت‌نامه","قیمت زنده همه محصولات فعال")}
        item{OutlinedTextField(q,{q=it},label={Text("جستجو")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(sort=="name",{sort="name"},{Text("نام")});FilterChip(sort=="pieces",{sort="pieces"},{Text("تعداد")});FilterChip(sort=="price",{sort="price"},{Text("قیمت")})}}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={csvLauncher.launch("tablodecori-pricebook.csv")}){Text("CSV")};Button(onClick={pdfLauncher.launch("tablodecori-pricebook.pdf")}){Text("PDF")};OutlinedButton(onClick={Exporters.printPricebook(context,list)}){Text("چاپ")};OutlinedButton(onClick={sharePricebook(context,list)}){Text("اشتراک")}}}
        if(list.isEmpty()) item{AppCard{Text("محصول فعالی برای قیمت‌نامه وجود ندارد.")}}
        items(list,key={it.product.id}){p->AppCard{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(p.product.name,fontWeight=FontWeight.Bold);PieceChips(p.product.pieces)};MoneyText(p.pricing.finalPriceToman)};Text("هزینه: ${money(p.pricing.costBeforeProfitToman)} · سود: ${money(p.pricing.profitToman)}",style=MaterialTheme.typography.bodySmall);TextButton(onClick={copy(context,money(p.pricing.finalPriceToman));vm.notify("قیمت کپی شد.")}){Text("کپی قیمت")}}}
    }
}

private fun sharePricebook(c:Context,list:List<PricedProduct>){val text=list.joinToString("\n"){"${it.product.name}: ${money(it.pricing.finalPriceToman)}"};c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"اشتراک قیمت‌نامه"))}
private fun copy(c:Context,text:String){(c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("tablodecori",text))}

@Composable fun OrdersScreen(vm:MainViewModel){
    val orders by vm.orders.collectAsState()
    val products by vm.pricedProducts.collectAsState()
    val settings by vm.settings.collectAsState()
    val context=LocalContext.current
    var selectedMonth by remember{mutableStateOf<String?>(null)}
    var add by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<OrderWithCosts?>(null)}
    val months=orders.map{PersianDate.fromEpoch(it.order.dateEpochMillis)}.distinctBy{it.monthKey}.sortedByDescending{it.monthKey}
    val filtered=selectedMonth?.let{k->orders.filter{PersianDate.fromEpoch(it.order.dateEpochMillis).monthKey==k}}?:orders
    val s=summary(filtered)
    val csvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        if(uri!=null) context.contentResolver.openOutputStream(uri)?.use{it.write(Exporters.ordersCsv(filtered).toByteArray(Charsets.UTF_8))}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("سفارش‌های ارسالی","Snapshot مالی؛ تغییر قیمت آینده روی گذشته اثر ندارد"){Button(onClick={add=true},enabled=products.isNotEmpty()){Text("+ ثبت ارسال")}}}
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){
                var exp by remember{mutableStateOf(false)}
                Box(Modifier.weight(1f)){
                    OutlinedButton(onClick={exp=true},modifier=Modifier.fillMaxWidth()){Text(selectedMonth?.let{k->months.find{it.monthKey==k}?.monthLabel}?:"همه ماه‌ها")}
                    DropdownMenu(exp,{exp=false}){
                        DropdownMenuItem({Text("همه ماه‌ها")},{selectedMonth=null;exp=false})
                        months.forEach{m->DropdownMenuItem({Text(m.monthLabel)},{selectedMonth=m.monthKey;exp=false})}
                    }
                }
                OutlinedButton(onClick={csvLauncher.launch("tablodecori-orders.csv")}){Text("CSV")}
            }
        }
        item{SummaryGrid(s)}
        item{AppCard{Text("نمودار فروش و سود",fontWeight=FontWeight.Bold);DailyChart(filtered)}}
        items(filtered,key={it.order.id}){x->
            val o=x.order
            AppCard{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column{Text(o.customerName,fontWeight=FontWeight.Bold);Text("${PersianDate.fromEpoch(o.dateEpochMillis).label} · ${o.province} ${o.city}",style=MaterialTheme.typography.bodySmall)}
                    Text(if(o.actualProfitToman>=0)"سود ${money(o.actualProfitToman)}" else "ضرر ${money(-o.actualProfitToman)}",color=if(o.actualProfitToman>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)
                }
                Text("${o.productNameSnapshot} · ${o.compositionSnapshot}",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState())){
                    if(o.instagramId.isNotBlank())TextButton(onClick={copy(context,o.instagramId)}){Text("کپی اینستاگرام")}
                    if(o.phone.isNotBlank())TextButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${o.phone}")))}){Text("تماس")}
                    TextButton(onClick={editing=x}){Text("ویرایش سفارش")}
                }
                if(o.note.isNotBlank()){
                    Text("یادداشت: "+o.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var details by remember{mutableStateOf(false)}
                TextButton(onClick={details=true}){Text("جزئیات هزینه")}
                if(details) AlertDialog(onDismissRequest={details=false},title={Text(o.internalNumber)},text={
                    Column(Modifier.verticalScroll(rememberScrollState())){
                        x.costs.forEach{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(it.name);Text(money(it.amountToman))}}
                        HorizontalDivider();Text("هزینه تولید: ${money(o.productCostSnapshotToman)}");Text("ارسال: ${money(o.shippingCostToman)}");Text("دریافتی: ${money(o.receivedToman)}")
                    }
                },confirmButton={Button(onClick={details=false}){Text("بستن")}})
            }
        }
    }
    if(add) OrderDialog(products,settings?.shippingDefaultToman?:0,null,{add=false}){pid,date,name,ig,phone,prov,city,ship,recv,note->
        vm.saveOrder(pid,date,name,ig,phone,prov,city,ship,recv,note);add=false
    }
    editing?.let{selected->
        OrderDialog(products,settings?.shippingDefaultToman?:0,selected.order,{editing=null}){_,date,name,ig,phone,prov,city,ship,recv,note->
            vm.updateOrder(selected.order.id,date,name,ig,phone,prov,city,ship,recv,note);editing=null
        }
    }
}

private fun summary(list:List<OrderWithCosts>):MonthlySummary{val sales=list.sumOf{it.order.receivedToman};val cost=list.sumOf{it.order.productCostSnapshotToman};val shipping=list.sumOf{it.order.shippingCostToman};val profit=list.sumOf{maxOf(0,it.order.actualProfitToman)};val loss=list.sumOf{maxOf(0,-it.order.actualProfitToman)};return MonthlySummary(list.size,list.sumOf{it.order.pieceCountSnapshot},sales,cost,shipping,profit,loss,profit-loss)}
@Composable private fun SummaryGrid(s:MonthlySummary){Column(verticalArrangement=Arrangement.spacedBy(7.dp)){listOf("سفارش" to fa(s.orderCount),"تابلو" to fa(s.pieceCount),"فروش کل" to money(s.salesToman),"هزینه کل" to money(s.costToman),"ارسال" to money(s.shippingToman),"سود" to money(s.profitToman),"ضرر" to money(s.lossToman),"سود خالص" to money(s.netToman)).chunked(2).forEach{r->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){r.forEach{(l,v)->Card(Modifier.weight(1f)){Column(Modifier.padding(10.dp)){Text(v,fontWeight=FontWeight.Bold);Text(l,style=MaterialTheme.typography.bodySmall)}}}}}}}
@Composable private fun DailyChart(list:List<OrderWithCosts>){if(list.isEmpty()){Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center){Text("پس از ثبت سفارش، نمودار اینجا نمایش داده می‌شود.")};return};val by=list.groupBy{PersianDate.fromEpoch(it.order.dateEpochMillis).day}.toSortedMap();val vals=by.entries.takeLast(12);val max=(vals.maxOfOrNull{maxOf(it.value.sumOf{x->x.order.receivedToman},it.value.sumOf{x->maxOf(0,x.order.actualProfitToman)})}?:1).toFloat();val salesColor=MaterialTheme.colorScheme.primary.copy(alpha=.35f);val profitColor=MaterialTheme.colorScheme.primary;Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top=12.dp)){val w=size.width/(vals.size.coerceAtLeast(1));vals.forEachIndexed{i,e->val sales=e.value.sumOf{it.order.receivedToman};val profit=e.value.sumOf{maxOf(0,it.order.actualProfitToman)};val x=i*w;drawLine(salesColor,Offset(x+w*.28f,size.height),Offset(x+w*.28f,size.height-size.height*(sales/max)),strokeWidth=w*.22f);drawLine(profitColor,Offset(x+w*.62f,size.height),Offset(x+w*.62f,size.height-size.height*(profit/max)),strokeWidth=w*.22f)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OrderDialog(products:List<PricedProduct>,shippingDefault:Long,initial:SentOrderEntity?,onDismiss:()->Unit,onSave:(String,Long,String,String,String,String,String,Long,Long,String)->Unit){
    val initialProductId=initial?.productId?.takeIf{id->products.any{it.product.id==id}}?:products.firstOrNull()?.product?.id.orEmpty()
    var pid by rememberSaveable(initial?.id){mutableStateOf(initialProductId)}
    var customer by rememberSaveable(initial?.id){mutableStateOf(initial?.customerName.orEmpty())}
    var ig by rememberSaveable(initial?.id){mutableStateOf(initial?.instagramId.orEmpty())}
    var phone by rememberSaveable(initial?.id){mutableStateOf(initial?.phone.orEmpty())}
    var prov by rememberSaveable(initial?.id){mutableStateOf(initial?.province.orEmpty())}
    var city by rememberSaveable(initial?.id){mutableStateOf(initial?.city.orEmpty())}
    var ship by rememberSaveable(initial?.id){mutableStateOf((initial?.shippingCostToman?:shippingDefault).toString())}
    var recv by rememberSaveable(initial?.id){mutableStateOf(initial?.receivedToman?.toString().orEmpty())}
    var note by rememberSaveable(initial?.id){mutableStateOf(initial?.note.orEmpty())}
    var date by rememberSaveable(initial?.id){mutableLongStateOf(initial?.dateEpochMillis?:System.currentTimeMillis())}
    var picker by rememberSaveable{mutableStateOf(false)}
    var exp by rememberSaveable{mutableStateOf(false)}
    var autoReceived by rememberSaveable(initial?.id){mutableStateOf(initial==null)}

    LaunchedEffect(pid,ship,autoReceived){
        if(autoReceived){
            products.find{it.product.id==pid}?.let{
                recv=(it.pricing.finalPriceToman+(ship.toLongOrNull()?:0)).toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(if(initial==null)"ثبت سفارش ارسالی" else "ویرایش سفارش")},
        text={
            val formScroll=rememberScrollState()
            Column(
                Modifier.fillMaxWidth().heightIn(max=590.dp).verticalScroll(formScroll),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ){
                Box{
                    OutlinedButton(onClick={exp=true},modifier=Modifier.fillMaxWidth(),enabled=initial==null){
                        Text(products.find{it.product.id==pid}?.product?.name?:initial?.productNameSnapshot?:"انتخاب محصول")
                    }
                    DropdownMenu(exp,{exp=false}){
                        products.forEach{p->DropdownMenuItem({Text(p.product.name)},{pid=p.product.id;autoReceived=true;exp=false})}
                    }
                }
                OutlinedButton(onClick={picker=true},modifier=Modifier.fillMaxWidth()){Text("تاریخ: "+PersianDate.fromEpoch(date).label)}
                OutlinedTextField(customer,{customer=it},label={Text("نام گیرنده")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(ig,{ig=it},label={Text("آیدی اینستاگرام")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(phone,{phone=it},label={Text("شماره تماس")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(prov,{prov=it},label={Text("استان")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(city,{city=it},label={Text("شهر")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(ship,{ship=it.filter(Char::isDigit);autoReceived=true},label={Text("هزینه ارسال واقعی")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(recv,{recv=it.filter(Char::isDigit);autoReceived=false},label={Text("مبلغ دریافتی")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(note,{note=it},label={Text("یادداشت")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
            }
        },
        confirmButton={Button(onClick={onSave(pid,date,customer,ig,phone,prov,city,ship.toLongOrNull()?:0,recv.toLongOrNull()?:0,note)},enabled=pid.isNotBlank()&&customer.isNotBlank()){Text(if(initial==null)"ثبت ارسال" else "ذخیره ویرایش")}},
        dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}}
    )
    if(picker){
        val state=rememberDatePickerState(initialSelectedDateMillis=date)
        DatePickerDialog(onDismissRequest={picker=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=it};picker=false}){Text("تأیید")}},dismissButton={TextButton(onClick={picker=false}){Text("انصراف")}}){DatePicker(state)}
    }
}

@Composable fun ReportsScreen(vm:MainViewModel){val history by vm.history.collectAsState();LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{SectionTitle("گزارش تغییر قیمت","اثر تغییر متریال روی محصولات")};if(history.isEmpty())item{AppCard{Text("هنوز قیمت متغیری تغییر نکرده است.")}} else items(history,key={it.id}){h->AppCard{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(h.materialName,fontWeight=FontWeight.Bold);Text(PersianDate.fromEpoch(h.changedAt).label,style=MaterialTheme.typography.bodySmall)};AssistChip(onClick={},label={Text("${fa(h.affectedProductCount)} محصول")})};Text(if(h.oldPriceToman!=h.newPriceToman)"${money(h.oldPriceToman)} ← ${money(h.newPriceToman)}" else "${percentFromBasisPoints(h.oldRateBasisPoints)} ← ${percentFromBasisPoints(h.newRateBasisPoints)}")}}}}

@Composable fun SettingsScreen(vm:MainViewModel){
    val settings by vm.settings.collectAsState();val rules by vm.profitRules.collectAsState();val context=LocalContext.current;val app=context.applicationContext as TablodecoriApp;val scope=rememberCoroutineScope();var rounding by remember(settings){mutableStateOf((settings?.roundingStepToman?:10000).toString())};var shipping by remember(settings){mutableStateOf((settings?.shippingDefaultToman?:0).toString())};var dark by remember(settings){mutableStateOf(settings?.darkMode?:false)};var restoreRaw by remember{mutableStateOf<String?>(null)}
    val backupLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch{try{val raw=app.backupManager.exportJson();context.contentResolver.openOutputStream(uri)?.use{it.write(raw.toByteArray())};vm.notify("پشتیبان JSON ذخیره شد.")}catch(t:Throwable){vm.notify(t.message?:"خطا در پشتیبان‌گیری")}}}
    val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{runCatching{context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("فایل خوانده نشد")}.onSuccess{restoreRaw=it}.onFailure{vm.notify("خواندن فایل ناموفق بود.")}}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{SectionTitle("تنظیمات","قواعد قیمت‌گذاری و نگهداری اطلاعات")};item{AppCard{Text("هویت برنامه",fontWeight=FontWeight.Bold);Text("tablodecori | مدیریت کارگاه");Text("رابط فارسی، RTL و Offline First",style=MaterialTheme.typography.bodySmall)}};item{AppCard{Text("تنظیمات عمومی",fontWeight=FontWeight.Bold);OutlinedTextField(rounding,{rounding=it},label={Text("گرد کردن قیمت نهایی")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(6.dp));OutlinedTextField(shipping,{shipping=it},label={Text("ارسال پیش‌فرض")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("حالت تیره");Switch(dark,{dark=it})};Button(onClick={vm.saveSettings(rounding.toLongOrNull()?:10000,shipping.toLongOrNull()?:0,dark)},modifier=Modifier.fillMaxWidth()){Text("ذخیره تنظیمات")}}};item{AppCard{Text("سود بر اساس تعداد تکه",fontWeight=FontWeight.Bold);rules.sortedBy{it.pieceCount}.forEach{r->var value by remember(r.fixedToman){mutableStateOf(r.fixedToman.toString())};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Text("${fa(r.pieceCount)} تکه",modifier=Modifier.width(64.dp));OutlinedTextField(value,{value=it},modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);TextButton(onClick={vm.saveProfit(r.pieceCount,value.toLongOrNull()?:0)}){Text("ثبت")}}}}};item{AppCard{Text("پشتیبان‌گیری",fontWeight=FontWeight.Bold);Text("پشتیبان شامل متریال‌ها، محصولات، سفارش‌ها، Snapshot هزینه، تاریخچه و تنظیمات است.",style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={backupLauncher.launch("tablodecori-backup.json")}){Text("Backup JSON")};OutlinedButton(onClick={restoreLauncher.launch(arrayOf("application/json","text/plain"))}){Text("Restore")}}}};item{AppCard{Text("فرض‌های نسخه ۱",fontWeight=FontWeight.Bold);Text("• تورم و هزینه پیش‌بینی‌نشده به‌صورت درصدی و به ترتیب روی هزینه جاری قبل از سود اعمال می‌شوند.\n• بسته‌بندی هوشمند بر پایه تعداد، بزرگ‌ترین تابلو، مساحت و تعداد بسته تقریبی است.\n• هزینه ارسال فقط هنگام سفارش ثبت می‌شود و وارد قیمت پایه محصول نمی‌شود.\n• پول با Long و محاسبات نسبتی با BigDecimal انجام می‌شود.",style=MaterialTheme.typography.bodySmall)}}}
    restoreRaw?.let{raw->AlertDialog(onDismissRequest={restoreRaw=null},title={Text("بازیابی پشتیبان")},text={Text("اطلاعات فعلی با محتوای فایل جایگزین می‌شود. فایل قبل از اعمال اعتبارسنجی خواهد شد. ادامه می‌دهید؟")},confirmButton={Button(onClick={scope.launch{try{app.backupManager.restoreJson(raw);vm.notify("بازیابی انجام شد.")}catch(t:Throwable){vm.notify(t.message?:"فایل نامعتبر است.")}};restoreRaw=null}){Text("بازیابی")}},dismissButton={TextButton(onClick={restoreRaw=null}){Text("انصراف")}})}
}
