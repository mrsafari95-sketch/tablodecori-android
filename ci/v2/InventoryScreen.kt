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
    val active=materials.filter{it.enabled&&!it.deleted}.associateBy{it.id}
    fun view(id:String,variant:String=""):StockView? {
        val material=active[id]?:return null
        val unit=StockPlanner.unitFor(material)?:return null
        return StockView(id,material.name,variant,unit,saved["$id|$variant"])
    }
    fun needsAttention(item:StockItemEntity)=item.onHandMicros<0L || (item.targetMicros>0L && item.onHandMicros*100L<=item.targetMicros*threshold)
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
    val configured=saved.values.count{it.targetMicros>0L}
    val attention=saved.values.count(::needsAttention)
    var filter by remember{mutableStateOf("همه")}
    var onlyLow by remember{mutableStateOf(false)}
    var search by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf<StockView?>(null)}
    var addColor by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)vm.refreshStockAlerts();vm.notify(if(granted)"اعلان موجودی فعال شد." else "اعلان موجودی به اجازه گوشی نیاز دارد.")}

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text("موجودی کارگاه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("هر ابعاد و هر رنگ، موجودی جداگانه دارد. برای شروع، تعداد واقعی هر قلم و مقدار مطلوب آن را ثبت کنید.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("$configured قلم شمارش‌شده",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(if(attention>0)"$attention قلم نیازمند بررسی یا خرید" else "موجودی ثبت‌شده در وضعیت عادی است",color=if(attention>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text("هشدار وقتی موجودی به $threshold٪ مقدار مطلوب برسد · تغییر از تنظیمات",style=MaterialTheme.typography.bodySmall)
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                TextButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("فعال‌کردن اعلان کمبود")}
        } } }
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
                    (search.isBlank() || r.name.contains(search,true) || r.variant.contains(search,true)) &&
                    (!onlyLow || r.saved?.let(::needsAttention)==true)
                }
                if(shown.isNotEmpty()){
                    visibleCount+=shown.size
                    item{Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("${shown.size} قلم",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    items(shown,key={it.id}){row->StockItemCard(row,threshold){editing=row}}
                }
            }
        }
        if(visibleCount==0) item{Text("قلمی با این فیلتر پیدا نشد.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(filter=="فریم" || filter=="همه") item{OutlinedButton(onClick={addColor=true},modifier=Modifier.fillMaxWidth()){Text("افزودن رنگ فریم دیگر")}}
        item { Text("شاسی: تعداد هر ابعاد عکس  ·  شیشه: مترمربع  ·  فریم: متر هر رنگ. قیمت شاسی همچنان بر اساس مترمربع است. چاپ عکس و دستمزد در انبار محاسبه نمی‌شوند.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    editing?.let { row -> StockEditorDialog(row,onDismiss={editing=null}){current,target->
        if(row.saved==null) vm.addStock(row.materialId,row.variant,current,target) else vm.saveStock(row.id,current,target)
        editing=null
    } }
    if(addColor){
        var color by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={addColor=false},title={Text("رنگ فریم جدید")},text={OutlinedTextField(color,{color=it},label={Text("نام رنگ")},singleLine=true)},
            confirmButton={Button(onClick={val key=StockPlanner.colorKey(color);editing=view("frame_pvc",key);addColor=false},enabled=color.isNotBlank()){Text("ادامه")}},
            dismissButton={TextButton(onClick={addColor=false}){Text("انصراف")}})
    }
}

@Composable private fun StockItemCard(row:StockView,threshold:Int,onEdit:()->Unit){
    val item=row.saved
    val status=when{
        item==null->"موجودی ثبت نشده"
        item.onHandMicros<0L->"موجودی را بررسی کنید"
        item.targetMicros<=0L->"مقدار مطلوب تعیین نشده"
        item.onHandMicros==0L->"ناموجود"
        item.onHandMicros*100L<=item.targetMicros*threshold->"رو به اتمام؛ سفارش خرید"
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
    }}
}

@Composable private fun StockEditorDialog(row:StockView,onDismiss:()->Unit,onSave:(Long,Long)->Unit){
    var current by remember(row.id){mutableStateOf(row.saved?.let{StockQuantity.format(it.onHandMicros)}?.takeIf{!it.startsWith("-")}.orEmpty())}
    var target by remember(row.id){mutableStateOf(row.saved?.let{StockQuantity.format(it.targetMicros)}?.takeIf{it!="0"}.orEmpty())}
    var error by remember(row.id){mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("${row.name} ${row.variant}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(if(row.unit=="PIECE")"تعداد واقعی را با عدد صحیح وارد کنید." else "مقدار واقعی را پس از اندازه‌گیری وارد کنید.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(current,{current=it;error=""},label={Text("موجودی فعلی (${StockQuantity.unitLabel(row.unit)})")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        OutlinedTextField(target,{target=it;error=""},label={Text("موجودی مطلوب برای هشدار")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(onClick={runCatching{val onHand=StockQuantity.parse(current);val desired=StockQuantity.parse(target);require(onHand>=0L&&desired>0L){"موجودی فعلی باید صفر یا بیشتر و موجودی مطلوب بزرگ‌تر از صفر باشد."};if(row.unit=="PIECE")require(onHand%StockPlanner.SCALE==0L&&desired%StockPlanner.SCALE==0L){"برای اقلام شمارشی، عدد صحیح وارد کنید."};onSave(onHand,desired)}.onFailure{error=it.message?:"مقدار نامعتبر است."}}){Text("ذخیره")}},
        dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
