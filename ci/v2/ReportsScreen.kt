package com.tablodecori.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.data.ReportsMath
import com.tablodecori.app.data.db.ExpenseEntity
import com.tablodecori.app.data.db.SentOrderEntity
import com.tablodecori.app.ui.AppCard
import com.tablodecori.app.util.PersianDate
import com.tablodecori.app.util.fa
import com.tablodecori.app.util.money
import java.io.File

private fun expenseCategory(key:String)=when(key){"MATERIAL"->"خرید متریال";"PAYROLL"->"حقوق و دستمزد";else->"سایر هزینه‌ها"}
private val chartColors=listOf(Color(0xFF155E4B),Color(0xFFB78322),Color(0xFF426E9B),Color(0xFF9160A9),Color(0xFFD36A5A),Color(0xFF9B8060))

@Composable fun ReportsScreen(vm:MainViewModel){
    val orders by vm.orders.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val settings by vm.settings.collectAsState()
    val context=LocalContext.current
    val now=PersianDate.fromEpoch(System.currentTimeMillis())
    var month by remember{mutableIntStateOf(now.year*100+now.month)}
    var page by remember{mutableStateOf("FINANCE")}
    var search by remember{mutableStateOf("")}
    var category by remember{mutableStateOf("ALL")}
    var edit by remember{mutableStateOf<ExpenseEntity?>(null)}
    var create by remember{mutableStateOf(false)}
    var confirmDelete by remember{mutableStateOf<ExpenseEntity?>(null)}
    fun changeMonth(delta:Int){val serial=(month/100)*12+(month%100-1)+delta;month=Math.floorDiv(serial,12)*100+Math.floorMod(serial,12)+1}
    val selectedOrders=orders.map{it.order}.filter{ReportsMath.monthKey(it.dateEpochMillis)==month}
    val selectedExpenses=expenses.filter{ReportsMath.monthKey(it.dateEpochMillis)==month}
    val finance=ReportsMath.finance(orders.map{it.order},expenses,month,settings?.openingCashToman?:0L)
    val shownExpenses=selectedExpenses.filter{(category=="ALL"||it.category==category)&&(search.isBlank()||listOf(it.title,it.payeeName,it.note).any{s->s.contains(search,true)})}
    val shownOrders=selectedOrders.filter{search.isBlank()||listOf(it.productNameSnapshot,it.compositionSnapshot,it.province,it.frameColor,it.customerName).any{s->s.contains(search,true)}}
    val overview=buildString{
        appendLine("📊 گزارش ${JalaliCalendar.months[month%100-1]} ${fa(month/100)}")
        appendLine("💰 فروش توافقی: ${money(finance.sales)}")
        appendLine("📥 دریافتی سفارش‌ها: ${money(finance.receipts)}")
        appendLine("🧾 خرید متریال: ${money(finance.materialPurchases)}")
        appendLine("👷 حقوق پرداختی: ${money(finance.payroll)}")
        appendLine("💸 سایر پرداخت‌ها: ${money(finance.otherSpending)}")
        appendLine("🏦 انتقالی از ماه‌های قبل: ${money(finance.carriedFromEarlier)}")
        appendLine("📦 مانده نقدی ثبت‌شده برای ماه بعد: ${money(finance.closingCash)}")
        appendLine("📈 حاشیه سفارش‌ها بر مبنای هزینه ساخت: ${money(finance.orderMargin)}")
    }

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("گزارش‌های کارگاه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(page=="FINANCE",onClick={page="FINANCE"},label={Text("مالی و هزینه‌ها")})
            FilterChip(page=="SALES",onClick={page="SALES"},label={Text("تحلیل فروش")})
        }}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={changeMonth(-1)}){Text("ماه قبل")}
            Text("${JalaliCalendar.months[month%100-1]} ${fa(month/100)}",fontWeight=FontWeight.Bold)
            TextButton(onClick={changeMonth(1)}){Text("ماه بعد")}
        }}
        item{OutlinedTextField(search,{search=it},label={Text(if(page=="FINANCE")"جستجو در هزینه‌ها" else "جستجو در فروش‌ها")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        if(page=="FINANCE"){
            item{AppCard{
                Text("جریان نقدی ماه",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                Metric("فروش توافقی",finance.sales);Metric("دریافتی ثبت‌شده",finance.receipts);Metric("مطالبات مانده",finance.outstanding)
                HorizontalDivider()
                Metric("خرید متریال",finance.materialPurchases);Metric("حقوق پرداختی",finance.payroll);Metric("سایر هزینه‌ها",finance.otherSpending)
                HorizontalDivider()
                Metric("مانده انتقالی از ماه‌های قبل",finance.carriedFromEarlier)
                Metric("مانده نقدی ثبت‌شده برای ماه بعد",finance.closingCash,true)
                Text("بر اساس پرداخت‌ها و دریافتی‌های ثبت‌شده در برنامه؛ مانده آغازین از تنظیمات قابل اصلاح است.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
            item{AppCard{
                Text("سود عملیاتی سفارش‌ها",fontWeight=FontWeight.Bold)
                Metric("حاشیه سفارش‌های این ماه",finance.orderMargin,true)
                Text("هزینه ساخت در هر سفارش قبلاً محاسبه شده است. خرید متریال و حقوق از این عدد دوباره کسر نمی‌شوند؛ این دو در جریان نقدی بالا دیده می‌شوند.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
            item{AppCard{Text("دریافتی و پرداخت روزانه",fontWeight=FontWeight.Bold);CashChart(selectedOrders,selectedExpenses)}}
            item{Button(onClick={create=true},modifier=Modifier.fillMaxWidth()){Text("+ ثبت هزینه یا پرداخت حقوق")}}
            item{OutlinedButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("گزارش ماه",overview));vm.notify("خلاصه گزارش کپی شد.")},modifier=Modifier.fillMaxWidth()){Text("کپی گزارش ماه")}}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("ALL" to "همه","MATERIAL" to "متریال","PAYROLL" to "حقوق","OTHER" to "سایر").forEach{(key,label)->FilterChip(category==key,onClick={category=key},label={Text(label)})}
            }}
            if(shownExpenses.isEmpty())item{Text("هزینه‌ای با این فیلتر ثبت نشده است.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(shownExpenses,key={it.id}){e->AppCard{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(e.title,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold);Text(money(e.amountToman),color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)}
                Text("${expenseCategory(e.category)} · ${PersianDate.fromEpoch(e.dateEpochMillis).label}${if(e.payeeName.isBlank())"" else " · "+e.payeeName}",style=MaterialTheme.typography.bodySmall)
                if(e.note.isNotBlank())Text(e.note,style=MaterialTheme.typography.bodySmall)
                ReceiptPreview(e.receiptFileName)
                Row{TextButton(onClick={edit=e}){Text("ویرایش")};TextButton(onClick={confirmDelete=e}){Text("حذف",color=MaterialTheme.colorScheme.error)}}
            }}
        }else{
            item{AppCard{Text("نمای کلی فروش",fontWeight=FontWeight.Bold);Metric("سفارش",shownOrders.size.toLong());Metric("تابلو",shownOrders.sumOf{it.pieceCountSnapshot}.toLong());Metric("فروش توافقی",shownOrders.sumOf{it.quotedTotalToman});Metric("مانده وصول",shownOrders.sumOf{it.quotedTotalToman-it.receivedToman})}}
            item{RankingCard("ست‌های پرفروش",ReportsMath.ranking(shownOrders){it.productNameSnapshot})}
            item{RankingCard("ترکیب ابعاد پرفروش",ReportsMath.ranking(shownOrders){it.dimensionsText.ifBlank{it.compositionSnapshot}})}
            item{RankingCard("استان‌های خریدار",ReportsMath.ranking(shownOrders){it.province})}
            item{RankingCard("رنگ‌های قاب",ReportsMath.ranking(shownOrders){it.frameColor})}
            item{RankingCard("منابع سفارش",ReportsMath.ranking(shownOrders){com.tablodecori.app.data.OrderSource.label(it.orderSource)},pie=true)}
            item{OutlinedButton(onClick={
                val text=buildString { appendLine("📊 تحلیل فروش ${JalaliCalendar.months[month%100-1]} ${fa(month/100)}")
                    listOf("ست‌ها" to ReportsMath.ranking(shownOrders){it.productNameSnapshot},"ابعاد" to ReportsMath.ranking(shownOrders){it.dimensionsText.ifBlank{it.compositionSnapshot}},"استان‌ها" to ReportsMath.ranking(shownOrders){it.province},"رنگ قاب" to ReportsMath.ranking(shownOrders){it.frameColor},"منابع" to ReportsMath.ranking(shownOrders){com.tablodecori.app.data.OrderSource.label(it.orderSource)}).forEach{(label,rows)->appendLine();appendLine("🔹 $label");rows.forEach{(name,count)->appendLine("$name: ${fa(count)} سفارش")}}
                }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("تحلیل فروش",text));vm.notify("آمار فروش کپی شد.")
            },modifier=Modifier.fillMaxWidth()){Text("کپی آمار فروش")}}
        }
    }
    if(create||edit!=null) ExpenseDialog(edit,onDismiss={create=false;edit=null}){id,date,kind,title,payee,amount,note,receipt->
        vm.saveExpense(id,date,kind,title,payee,amount,note,receipt);create=false;edit=null
    }
    confirmDelete?.let{e->AlertDialog(onDismissRequest={confirmDelete=null},title={Text("حذف هزینه")},text={Text("این هزینه و عکس فیش آن حذف می‌شود.")},confirmButton={Button(onClick={vm.deleteExpense(e.id);confirmDelete=null}){Text("حذف")}},dismissButton={TextButton(onClick={confirmDelete=null}){Text("انصراف")}})}
}

@Composable private fun Metric(label:String,value:Long,highlight:Boolean=false){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,modifier=Modifier.weight(1f));Text(if(label in setOf("سفارش","تابلو"))fa(value) else money(value),fontWeight=if(highlight)FontWeight.ExtraBold else FontWeight.Medium,color=if(highlight)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}}

@Composable private fun CashChart(orders:List<SentOrderEntity>,expenses:List<ExpenseEntity>){
    val incoming=orders.groupBy{PersianDate.fromEpoch(it.dateEpochMillis).day}.mapValues{(_,v)->v.sumOf{it.receivedToman}}
    val outgoing=expenses.groupBy{PersianDate.fromEpoch(it.dateEpochMillis).day}.mapValues{(_,v)->v.sumOf{it.amountToman}}
    if(incoming.isEmpty()&&outgoing.isEmpty()){Text("برای این ماه تراکنشی ثبت نشده است.");return}
    val max=maxOf(incoming.values.maxOrNull()?:0L,outgoing.values.maxOrNull()?:0L,1L).toFloat()
    val green=MaterialTheme.colorScheme.primary;val gold=Color(0xFFB78322)
    Canvas(Modifier.fillMaxWidth().height(150.dp)){val width=size.width/31f;for(day in 1..31){val x=(day-1)*width;val a=(incoming[day]?:0L)/max;val b=(outgoing[day]?:0L)/max;drawRect(green,Offset(x,size.height-a*size.height),Size(width*.42f,a*size.height));drawRect(gold,Offset(x+width*.46f,size.height-b*size.height),Size(width*.42f,b*size.height))}}
    Text("سبز: دریافتی · طلایی: پرداختی · از راست به چپ روزهای ۱ تا ۳۱",style=MaterialTheme.typography.bodySmall)
}

@Composable private fun RankingCard(title:String,rows:List<Pair<String,Int>>,pie:Boolean=false){AppCard{
    Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
    if(rows.isEmpty()){Text("برای این ماه داده‌ای ثبت نشده است.");return@AppCard}
    if(pie){val total=rows.sumOf{it.second}.toFloat().coerceAtLeast(1f);Canvas(Modifier.size(150.dp).align(Alignment.CenterHorizontally)){var start=-90f;rows.forEachIndexed{i,row->val sweep=row.second/total*360f;drawArc(chartColors[i%chartColors.size],start,sweep,true);start+=sweep}}}
    val top=rows.first().second.toFloat().coerceAtLeast(1f)
    val totalCount=rows.sumOf{it.second}.coerceAtLeast(1)
    rows.take(8).forEachIndexed{i,(label,count)->Column{
        val percent=count*100/totalCount
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f),maxLines=2);Text("${fa(count)} سفارش · ${fa(percent)}٪",color=chartColors[i%chartColors.size])}
        LinearProgressIndicator(progress={count/top},modifier=Modifier.fillMaxWidth(),color=chartColors[i%chartColors.size])
    }}
}}

@Composable private fun ReceiptPreview(name:String){
    if(name.isBlank())return
    val context=LocalContext.current
    val file=remember(name){name.takeIf{it.matches(Regex("[a-f0-9-]{36}\\.jpg"))}?.let{File(context.filesDir,"order_photos/$it")}?.takeIf{it.isFile}}
    val image=remember(name){file?.let{BitmapFactory.decodeFile(it.path)?.asImageBitmap()}}
    if(file!=null){
        image?.let{Image(it,"عکس فیش",Modifier.fillMaxWidth().height(115.dp),contentScale=ContentScale.Fit)}
        TextButton(onClick={runCatching{val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file);context.startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,"image/jpeg");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}}){Text("نمایش فیش")}
    }
}

@Composable private fun ExpenseDialog(initial:ExpenseEntity?,onDismiss:()->Unit,onSave:(String?,Long,String,String,String,Long,String,String?)->Unit){
    var date by remember(initial?.id){mutableLongStateOf(initial?.dateEpochMillis?:System.currentTimeMillis())}
    var category by remember(initial?.id){mutableStateOf(initial?.category?:"MATERIAL")}
    var title by remember(initial?.id){mutableStateOf(initial?.title.orEmpty())}
    var payee by remember(initial?.id){mutableStateOf(initial?.payeeName.orEmpty())}
    var amount by remember(initial?.id){mutableStateOf(initial?.amountToman?.toString().orEmpty())}
    var note by remember(initial?.id){mutableStateOf(initial?.note.orEmpty())}
    var receipt by remember(initial?.id){mutableStateOf<String?>(null)}
    var pickDate by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->receipt=uri?.toString()}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"ثبت هزینه" else "ویرایش هزینه")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        TextButton(onClick={pickDate=true}){Text("📅 ${PersianDate.fromEpoch(date).label}")}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("MATERIAL","PAYROLL","OTHER").forEach{kind->FilterChip(category==kind,onClick={category=kind},label={Text(expenseCategory(kind))})}}
        OutlinedTextField(title,{title=it},label={Text(if(category=="PAYROLL")"بابت حقوق چه ماهی؟" else "موضوع هزینه یا فاکتور")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(payee,{payee=it},label={Text(if(category=="PAYROLL")"نام کارگر یا کارمند" else "نام فروشنده / دریافت‌کننده")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("مبلغ پرداخت‌شده (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(note,{note=it},label={Text("یادداشت اختیاری")},modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={picker.launch("image/*")},modifier=Modifier.fillMaxWidth()){Text(if(receipt!=null||initial?.receiptFileName?.isNotBlank()==true)"تغییر عکس فیش یا فاکتور" else "+ پیوست عکس فیش یا فاکتور")}
        if(receipt!=null)Text("عکس جدید انتخاب شد.",style=MaterialTheme.typography.bodySmall)
        else initial?.let{ReceiptPreview(it.receiptFileName)}
    }},confirmButton={Button(onClick={onSave(initial?.id,date,category,title,payee,amount.toLong(),note,receipt)},enabled=title.isNotBlank()&&amount.toLongOrNull()?.let{it>0L}==true){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
    if(pickDate)PersianCalendarPicker(date,onDismiss={pickDate=false}){date=it;pickDate=false}
}
