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
import com.tablodecori.app.data.OrderShareText
import com.tablodecori.app.data.OrderSource
import com.tablodecori.app.data.OrderSharing
import com.tablodecori.app.data.displayDimensions
import com.tablodecori.app.data.expectedProfitToman
import com.tablodecori.app.data.outstandingToman
import com.tablodecori.app.data.sellerShippingToman
import com.tablodecori.app.data.db.OrderWithCosts
import com.tablodecori.app.data.db.SentOrderEntity
import com.tablodecori.app.ui.*
import com.tablodecori.app.util.*
import kotlinx.coroutines.launch
import java.util.Calendar

private fun copy(c:Context,text:String){(c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("tablodecori",text))}

@Composable fun OrdersScreen(vm:MainViewModel){
    val orders by vm.orders.collectAsState()
    val products by vm.pricedProducts.collectAsState()
    val settings by vm.settings.collectAsState()
    val context=LocalContext.current
    var selectedMonth by remember{mutableStateOf<String?>(null)}
    var add by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<OrderWithCosts?>(null)}
    var search by rememberSaveable{mutableStateOf("")}
    var statusFilter by rememberSaveable{mutableStateOf("ALL")}
    if(add || editing!=null){
        val selected=editing?.order
        OrderEditorScreen(products,settings,selected,onDismiss={add=false;editing=null}){input->
            if(selected==null) vm.saveOrder(input) else vm.updateOrder(selected.id,input)
        }
        return
    }
    val months=orders.map{PersianDate.fromEpoch(it.order.dateEpochMillis)}.distinctBy{it.monthKey}.sortedByDescending{it.monthKey}
    val monthFiltered=selectedMonth?.let{k->orders.filter{PersianDate.fromEpoch(it.order.dateEpochMillis).monthKey==k}}?:orders
    val today=PersianDate.fromEpoch(System.currentTimeMillis())
    fun sameDay(millis:Long):Boolean=PersianDate.fromEpoch(millis).let{it.year==today.year&&it.month==today.month&&it.day==today.day}
    val statusFiltered=monthFiltered.filter{x->when(statusFilter){
        "PREPARING"->x.order.orderStatus=="PREPARING"
        "READY"->x.order.orderStatus=="READY"
        "SENT"->x.order.orderStatus=="SENT"
        "DUE"->x.order.orderStatus!="SENT"&&x.order.plannedShipAtMillis>0L&&sameDay(x.order.plannedShipAtMillis)
        "UNPAID"->x.order.outstandingToman()>0L
        else->true
    }}
    val query=search.trim()
    val filtered=if(query.isBlank()) statusFiltered else statusFiltered.filter{x->
        val o=x.order
        listOf(o.customerName,o.instagramId,o.phone,o.province,o.city,o.addressDetails,o.postalCode,o.productNameSnapshot,o.internalNumber,o.frameColor,OrderSource.label(o.orderSource)).any{it.contains(query,ignoreCase=true)}
    }
    val s=summary(filtered)
    val csvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        if(uri!=null) context.contentResolver.openOutputStream(uri)?.use{it.write(Exporters.ordersCsv(filtered).toByteArray(Charsets.UTF_8))}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("سفارش‌های ارسالی","Snapshot مالی؛ تغییر قیمت آینده روی گذشته اثر ندارد"){Button(onClick={add=true},enabled=products.isNotEmpty()){Text("+ ثبت ارسال")}}}
        item{OutlinedTextField(search,{search=it},label={Text("جستجو در سفارش‌ها")},placeholder={Text("نام، آیدی اینستاگرام یا شماره تماس")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("ALL" to "همه","PREPARING" to "در آماده‌سازی","READY" to "آمادهٔ ارسال","DUE" to "ارسال امروز","UNPAID" to "مانده‌دار","SENT" to "ارسال‌شده").forEach{(key,label)->FilterChip(selected=statusFilter==key,onClick={statusFilter=key},label={Text(label)})}
        }}
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
        item{AppCard{Text("نمودار دریافتی و سود سفارش‌ها",fontWeight=FontWeight.Bold);DailyChart(filtered)}}
        items(filtered,key={it.order.id}){x->
            val o=x.order
            AppCard{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column{Text(o.customerName,fontWeight=FontWeight.Bold);Text("${PersianDate.fromEpoch(o.dateEpochMillis).label} · ${o.province} ${o.city}",style=MaterialTheme.typography.bodySmall)}
                    Text(if(o.actualProfitToman>=0)"سود ${money(o.actualProfitToman)}" else "ضرر ${money(-o.actualProfitToman)}",color=if(o.actualProfitToman>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)
                }
                Text("${o.productNameSnapshot} · ${o.displayDimensions()}",style=MaterialTheme.typography.bodySmall)
                Text("منبع سفارش: ${OrderSource.label(o.orderSource)}",style=MaterialTheme.typography.bodySmall)
                if(o.photoFileName.isNotBlank()) OrderPhotoThumbnail(o.photoFileName)
                if(o.frameColor.isNotBlank()) Text("رنگ قاب: ${o.frameColor}",style=MaterialTheme.typography.bodySmall)
                Text("وضعیت: ${when(o.orderStatus){"READY"->"آمادهٔ ارسال";"SENT"->"ارسال‌شده";else->"در آماده‌سازی"}}${if(o.plannedShipAtMillis>0L)" · نوبت ارسال: ${PersianDate.fromEpoch(o.plannedShipAtMillis).label}" else ""}",style=MaterialTheme.typography.bodySmall)
                if(o.trackingCode.isNotBlank()) Text("کد رهگیری: ${o.trackingCode}",style=MaterialTheme.typography.bodySmall)
                Text(if(o.shippingPayer=="RECIPIENT")"ارسال: پس‌کرایه، پرداخت مشتری به شرکت حمل${if(o.shippingCostToman>0L) " · ${money(o.shippingCostToman)}" else ""}" else "ارسال: هزینه با فروشنده · ${money(o.shippingCostToman)}",style=MaterialTheme.typography.bodySmall)
                if(o.outstandingToman()>0) Text("مبلغ سفارش: ${money(o.quotedTotalToman)} · جمع پرداخت‌ها: ${money(o.receivedToman)} · مانده: ${money(o.outstandingToman())}",style=MaterialTheme.typography.bodySmall)
                if(o.addressDetails.isNotBlank()||o.postalCode.isNotBlank()) Text("آدرس: ${o.province} - ${o.city}${if(o.addressDetails.isNotBlank()) " - "+o.addressDetails else ""}${if(o.postalCode.isNotBlank()) " · کد پستی: "+o.postalCode else ""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(o.note.isNotBlank()){
                    Text("یادداشت: "+o.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                    if(o.instagramId.isNotBlank())TextButton(onClick={copy(context,o.instagramId)}){Text("کپی اینستاگرام")}
                    if(o.phone.isNotBlank())TextButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${o.phone}")))}){Text("تماس")}
                    TextButton(onClick={editing=x}){Text("ویرایش سفارش")}
                }
                OutlinedButton(onClick={copy(context,OrderShareText.format(o));vm.notify("اطلاعات کامل سفارش کپی شد.")},modifier=Modifier.fillMaxWidth()){Text("کپی کامل اطلاعات سفارش")}
                Button(onClick={runCatching{OrderSharing.share(context,o)}.onSuccess{hasPhoto->vm.notify(if(hasPhoto)"عکس و متن کامل آماده ارسال است؛ متن برای پیام‌رسان‌های بدون پشتیبانی از کپشن نیز کپی شد." else "عکس سفارش پیدا نشد؛ متن کامل برای ارسال و کپی آماده است.")}.onFailure{vm.notify("اشتراک‌گذاری ناموفق بود: ${it.message.orEmpty()}")}},modifier=Modifier.fillMaxWidth()){
                    Text("📤 ارسال عکس و متن کامل به پیام‌رسان",maxLines=2)
                }
                var details by remember{mutableStateOf(false)}
                var confirmDelete by remember{mutableStateOf(false)}
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    TextButton(onClick={details=true}){Text("جزئیات هزینه")}
                    TextButton(onClick={confirmDelete=true}){Text("حذف سفارش",color=MaterialTheme.colorScheme.error)}
                }
                if(confirmDelete) AlertDialog(
                    onDismissRequest={confirmDelete=false},
                    title={Text("حذف سفارش")},
                    text={Text("با حذف این سفارش، اطلاعات مشتری، عکس و سابقهٔ مالی آن از برنامه برداشته می‌شود. ادامه می‌دهید؟")},
                    confirmButton={Button(onClick={confirmDelete=false;vm.deleteOrder(o.id)}){Text("بله")}},
                    dismissButton={TextButton(onClick={confirmDelete=false}){Text("خیر")}}
                )
                if(details) AlertDialog(onDismissRequest={details=false},title={Text(o.internalNumber)},text={
                    Column(Modifier.verticalScroll(rememberScrollState())){
                        x.costs.forEach{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(it.name);Text(money(it.amountToman))}}
                        HorizontalDivider();Text("هزینه تولید: ${money(o.productCostSnapshotToman)}");Text("هزینه ارسال کارگاه: ${money(o.sellerShippingToman())}");if(o.shippingPayer=="RECIPIENT")Text("پس‌کرایه مشتری: ${money(o.shippingCostToman)}");Text("مبلغ توافق‌شده: ${money(o.quotedTotalToman)}");Text("بیعانه: ${money(o.depositToman)}");Text("پرداخت درب منزل: ${money(o.codDueToman)}");Text("جمع پرداخت‌ها: ${money(o.receivedToman)}");Text("مانده: ${money(o.outstandingToman())}");Text("سود فعلی: ${money(o.actualProfitToman)}")
                    }
                },confirmButton={Button(onClick={details=false}){Text("بستن")}})
            }
        }
    }
}

private fun summary(list:List<OrderWithCosts>):MonthlySummary{
    val profit=list.sumOf{maxOf(0L,it.order.actualProfitToman)}
    val loss=list.sumOf{maxOf(0L,-it.order.actualProfitToman)}
    return MonthlySummary(
        orderCount=list.size,pieceCount=list.sumOf{it.order.pieceCountSnapshot},
        salesToman=list.sumOf{it.order.quotedTotalToman},
        costToman=list.sumOf{it.order.productCostSnapshotToman},
        shippingToman=list.sumOf{it.order.sellerShippingToman()},
        profitToman=profit,lossToman=loss,netToman=profit-loss,
        receivedToman=list.sumOf{it.order.receivedToman},
        outstandingToman=list.sumOf{it.order.outstandingToman()},
        expectedProfitToman=list.sumOf{it.order.expectedProfitToman()},
    )
}
@Composable private fun SummaryGrid(s:MonthlySummary){Column(verticalArrangement=Arrangement.spacedBy(7.dp)){listOf("سفارش" to fa(s.orderCount),"تابلو" to fa(s.pieceCount),"فروش توافقی" to money(s.salesToman),"پرداخت ثبت‌شده" to money(s.receivedToman),"مانده" to money(s.outstandingToman),"هزینه کل" to money(s.costToman),"ارسال" to money(s.shippingToman),"سود فعلی" to money(s.profitToman),"ضرر فعلی" to money(s.lossToman),"خالص فعلی" to money(s.netToman)).chunked(2).forEach{r->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){r.forEach{(l,v)->Card(Modifier.weight(1f)){Column(Modifier.padding(10.dp)){Text(v,fontWeight=FontWeight.Bold);Text(l,style=MaterialTheme.typography.bodySmall)}}}}}}}
@Composable private fun DailyChart(list:List<OrderWithCosts>){if(list.isEmpty()){Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center){Text("پس از ثبت سفارش، نمودار اینجا نمایش داده می‌شود.")};return};val by=list.groupBy{PersianDate.fromEpoch(it.order.dateEpochMillis).day}.toSortedMap();val vals=by.entries.toList().takeLast(12);val max=(vals.maxOfOrNull{maxOf(it.value.sumOf{x->x.order.receivedToman},it.value.sumOf{x->maxOf(0L,x.order.actualProfitToman)})}?:1).coerceAtLeast(1L).toFloat();val salesColor=MaterialTheme.colorScheme.primary.copy(alpha=.35f);val profitColor=MaterialTheme.colorScheme.primary;Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top=12.dp)){val w=size.width/(vals.size.coerceAtLeast(1));vals.forEachIndexed{i,e->val sales=e.value.sumOf{it.order.receivedToman};val profit=e.value.sumOf{maxOf(0L,it.order.actualProfitToman)};val x=i*w;drawLine(salesColor,Offset(x+w*.28f,size.height),Offset(x+w*.28f,size.height-size.height*(sales/max)),strokeWidth=w*.22f);drawLine(profitColor,Offset(x+w*.62f,size.height),Offset(x+w*.62f,size.height-size.height*(profit/max)),strokeWidth=w*.22f)}}}
