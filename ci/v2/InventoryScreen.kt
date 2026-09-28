package com.tablodecori.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.data.MaterialCatalog
import com.tablodecori.app.data.StockPlanner
import com.tablodecori.app.data.StockQuantity
import com.tablodecori.app.data.db.StockItemEntity
import com.tablodecori.app.util.fa

private data class StockView(val materialId:String,val name:String,val variant:String,val unit:String,val saved:StockItemEntity?) {
    val id:String get()="$materialId|$variant"
}

@Composable fun InventoryScreen(vm:MainViewModel){
    val stock by vm.stockItems.collectAsState()
    val materials by vm.materials.collectAsState()
    val settings by vm.settings.collectAsState()
    val photoSizes by vm.sizePrices("photo_lab").collectAsState(initial=emptyList())
    val foamSizes by vm.sizePrices("pack_foam").collectAsState(initial=emptyList())
    val cartonSizes by vm.sizePrices("pack_carton").collectAsState(initial=emptyList())
    val context=LocalContext.current
    val threshold=settings?.lowStockPercent?:10
    val saved=stock.filterNot{MaterialCatalog.isLegacy(it.materialId)}.associateBy{it.id}
    val legacyStockCount=stock.count{MaterialCatalog.isLegacy(it.materialId)}
    val active=materials.filter{it.enabled&&!it.deleted}.associateBy{it.id}
    fun view(id:String,variant:String=""):StockView? {
        val material=active[id]?:return null
        val unit=StockPlanner.unitFor(material)?:return null
        return StockView(id,material.name,variant,unit,saved["$id|$variant"])
    }
    fun needsAttention(item:StockItemEntity)=item.tracked && (item.onHandMicros<0L || (item.targetMicros>0L && item.onHandMicros*100L<=item.targetMicros*threshold))
    val photoKeys=(photoSizes.filter{it.enabled}.map{StockPlanner.sizeKey(it.widthCm,it.heightCm)}+saved.values.filter{it.materialId=="backboard_3mm"}.map{it.variantKey}).distinct().sorted()
    val packageKeys=(foamSizes+cartonSizes).filter{it.enabled}.map{StockPlanner.sizeKey(it.widthCm,it.heightCm)}
        .plus(saved.values.filter{it.materialId in setOf("pack_foam","pack_carton")}.map{it.variantKey}).distinct().sorted()
    val colorKeys=(settings?.frameColorOptions.orEmpty().split(',', '،').map{it.trim()}.filter{it.isNotBlank()}+
        saved.values.filter{it.materialId=="frame_pvc"}.map{it.variantKey}).map(StockPlanner::colorKey).distinct()
    val chassis=photoKeys.mapNotNull{view("backboard_3mm",it)}
    val packaging=packageKeys.flatMap{key->listOfNotNull(view("pack_carton",key),view("pack_foam",key))}+listOfNotNull(view("pack_tape"))
    val glass=listOfNotNull(view("glass"))
    val frame=colorKeys.mapNotNull{view("frame_pvc",it)}
    val special=setOf("backboard_3mm","pack_carton","pack_foam","pack_tape","glass","frame_pvc")
    val other=(MaterialCatalog.mainMaterials(materials)+materials.filter{it.id.startsWith("pack_")}).distinctBy{it.id}
        .filter{it.id !in special && it.enabled && StockPlanner.unitFor(it)!=null}.mapNotNull{view(it.id)}
    val groups=listOf("شاسی ۳ میل" to chassis,"لوازم بسته‌بندی" to packaging,"شیشه" to glass,"فریم PVC بر اساس رنگ" to frame,"سایر اقلام" to other)
    val candidates=groups.flatMap{it.second}.distinctBy{it.id}
    val configured=saved.values.count{it.tracked}
    val attention=saved.values.count(::needsAttention)
    var filter by remember{mutableStateOf("همه")}
    var onlyLow by remember{mutableStateOf(false)}
    var search by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf<StockView?>(null)}
    var adding by remember{mutableStateOf(false)}
    var hideCandidate by remember{mutableStateOf<StockView?>(null)}
    var addColor by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)vm.refreshStockAlerts();vm.notify(if(granted)"اعلان موجودی فعال شد." else "اعلان موجودی به اجازه گوشی نیاز دارد.")}

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text("موجودی کارگاه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("اقلام مهم کارگاه را برای پایش انتخاب کنید. موجودی هر اندازهٔ شاسی و هر رنگ فریم جداگانه ثبت می‌شود.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item{Button(onClick={adding=true},modifier=Modifier.fillMaxWidth()){Text("+ افزودن قلم به پایش انبار")}}
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("${fa(configured)} قلم زیر پایش",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(if(attention>0)"${fa(attention)} قلم نیازمند بررسی یا خرید" else "موجودی اقلام پایش‌شده کافی است.",color=if(attention>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text("هشدار هنگام رسیدن موجودی به ${fa(threshold)}٪ مقدار مطلوب یا کمتر · قابل تغییر در تنظیمات",style=MaterialTheme.typography.bodySmall)
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                TextButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("فعال‌کردن اعلان کمبود")}
        } } }
        if(legacyStockCount>0) item { Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=RoundedCornerShape(12.dp)){Text("${fa(legacyStockCount)} قلم با روش قدیمی انبار ثبت شده است. موجودی شاسی قدیمی را نمی‌توان به تعداد هر اندازه تبدیل کرد؛ لطفاً تعداد واقعی هر اندازه را یک‌بار ثبت کنید.",modifier=Modifier.padding(12.dp))} }
        item { OutlinedTextField(search,{search=it},label={Text("جستجوی اندازه، رنگ یا متریال")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("همه","شاسی","بسته‌بندی","شیشه","فریم","سایر").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)})}
        } }
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Text("فقط اقلام کم‌موجود")
            Switch(onlyLow,{onlyLow=it})
        } }
        val names=mapOf("شاسی" to 0,"بسته‌بندی" to 1,"شیشه" to 2,"فریم" to 3,"سایر" to 4)
        var visibleCount=0
        groups.forEachIndexed{index,(title,rows)->
            if(filter=="همه" || names[filter]==index){
                val shown=rows.filter{r->
                    r.saved?.tracked==true &&
                    (search.isBlank() || r.name.contains(search,true) || r.variant.contains(search,true)) &&
                    (!onlyLow || r.saved?.let(::needsAttention)==true)
                }
                if(shown.isNotEmpty()){
                    visibleCount+=shown.size
                    item{Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("${fa(shown.size)} قلم",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    items(shown,key={it.id}){row->StockItemCard(row,threshold,onEdit={editing=row},onHide={hideCandidate=row})}
                }
            }
        }
        if(visibleCount==0) item{Text(if(configured==0)"هنوز قلمی برای پایش انتخاب نشده است. از دکمه افزودن، موارد مهم کارگاه را انتخاب کنید." else "قلمی با این فیلتر پیدا نشد.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item { Text("واحد انبار: شاسی بر اساس تعداد هر اندازه، شیشه بر حسب مترمربع و فریم بر حسب مترِ هر رنگ. قیمت‌گذاری شاسی همچنان بر اساس مترمربع است. چاپ عکس و دستمزد جزو موجودی انبار نیستند.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    editing?.let { row -> StockEditorDialog(row,onDismiss={editing=null}){current,target->
        if(row.saved==null) vm.addStock(row.materialId,row.variant,current,target) else vm.saveStock(row.id,current,target)
        editing=null
    } }
    hideCandidate?.let { row -> AlertDialog(onDismissRequest={hideCandidate=null},title={Text("حذف از پایش انبار")},
        text={Text("${row.name} ${row.variant.replace("x","×")} از فهرست و هشدارها برداشته می‌شود. تعداد ثبت‌شده و سوابق سفارش حفظ می‌شوند و هر زمان می‌توانید دوباره آن را اضافه کنید.")},
        confirmButton={Button(onClick={vm.hideStock(row.id);hideCandidate=null}){Text("حذف از پایش")}},
        dismissButton={TextButton(onClick={hideCandidate=null}){Text("انصراف")}}) }
    if(adding){
        var find by remember{mutableStateOf("")}
        val available=candidates.filter{it.saved?.tracked!=true && (find.isBlank() || it.name.contains(find,true) || it.variant.contains(find,true))}
        AlertDialog(onDismissRequest={adding=false},title={Text("افزودن قلم به پایش")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(find,{find=it},label={Text("جستجوی قلم، ابعاد یا رنگ")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Text("فقط اقلام فیزیکی انتخاب‌شده نمایش داده و برایشان هشدار صادر می‌شود.",style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                items(available,key={it.id}){row->TextButton(onClick={vm.trackStock(row.materialId,row.variant);adding=false},modifier=Modifier.fillMaxWidth()){
                    Text("${row.name}${if(row.variant.isBlank())"" else " · "+row.variant.replace("x","×")}",modifier=Modifier.fillMaxWidth())
                }}
                if(available.isEmpty()) item{Text("قلم دیگری با این جستجو پیدا نشد.")}
            }
        }},confirmButton={TextButton(onClick={adding=false;addColor=true}){Text("+ رنگ فریم جدید")}},dismissButton={TextButton(onClick={adding=false}){Text("بستن")}})
    }
    if(addColor){
        var color by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={addColor=false},title={Text("رنگ فریم جدید")},text={OutlinedTextField(color,{color=it},label={Text("نام رنگ")},singleLine=true)},
            confirmButton={Button(onClick={val key=StockPlanner.colorKey(color);vm.trackStock("frame_pvc",key);addColor=false},enabled=color.isNotBlank()){Text("افزودن")}},
            dismissButton={TextButton(onClick={addColor=false}){Text("انصراف")}})
    }
}

@Composable private fun StockItemCard(row:StockView,threshold:Int,onEdit:()->Unit,onHide:()->Unit){
    val item=row.saved
    val status=when{
        item==null->"موجودی ثبت نشده"
        item.onHandMicros<0L->"مصرف بیش از موجودی ثبت‌شده"
        item.targetMicros<=0L->"مقدار مطلوب تعیین نشده"
        item.onHandMicros==0L->"ناموجود"
        item.onHandMicros*100L<=item.targetMicros*threshold->"رو به اتمام"
        else->"کافی"
    }
    val urgent=item!=null&&(item.onHandMicros<0L || (item.targetMicros>0L&&item.onHandMicros*100L<=item.targetMicros*threshold))
    val badgeColor=if(urgent)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(row.variant.ifBlank{row.name}.replace("x"," × "),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);if(row.variant.isNotBlank())Text(row.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Surface(color=badgeColor,shape=RoundedCornerShape(12.dp)){Text(status,modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)}
        }
        Text(if(item==null)"هنوز شمارش نشده" else "موجودی فعلی: ${StockQuantity.format(item.onHandMicros)} ${StockQuantity.unitLabel(row.unit)}",fontWeight=FontWeight.Bold,color=if(urgent)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if(item!=null&&item.targetMicros>0L) Text("مقدار مطلوب: ${StockQuantity.format(item.targetMicros)} ${StockQuantity.unitLabel(row.unit)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()){Text(if(item==null)"ثبت موجودی این قلم" else "ویرایش موجودی")}
        TextButton(onClick=onHide,modifier=Modifier.align(Alignment.End)){Text("حذف از پایش",color=MaterialTheme.colorScheme.error)}
    }}
}

@Composable private fun StockEditorDialog(row:StockView,onDismiss:()->Unit,onSave:(Long,Long)->Unit){
    var current by remember(row.id){mutableStateOf(row.saved?.let{StockQuantity.format(it.onHandMicros)}?.takeIf{!it.startsWith("-")}.orEmpty())}
    var target by remember(row.id){mutableStateOf(row.saved?.let{StockQuantity.format(it.targetMicros)}?.takeIf{it!="0"}.orEmpty())}
    var error by remember(row.id){mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("${row.name} ${row.variant}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(if(row.unit=="PIECE")"تعداد واقعی را با عدد صحیح وارد کنید." else "مقدار واقعی را پس از اندازه‌گیری وارد کنید.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(current,{current=it;error=""},label={Text("موجودی فعلی (${StockQuantity.unitLabel(row.unit)})")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        OutlinedTextField(target,{target=it;error=""},label={Text("موجودی مطلوب (مبنای هشدار)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(onClick={runCatching{val onHand=StockQuantity.parse(current);val desired=StockQuantity.parse(target);require(onHand>=0L&&desired>0L){"موجودی فعلی باید صفر یا بیشتر و موجودی مطلوب بزرگ‌تر از صفر باشد."};if(row.unit=="PIECE")require(onHand%StockPlanner.SCALE==0L&&desired%StockPlanner.SCALE==0L){"برای اقلام شمارشی، عدد صحیح وارد کنید."};onSave(onHand,desired)}.onFailure{error=it.message?:"مقدار نامعتبر است."}}){Text("ذخیره")}},
        dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
