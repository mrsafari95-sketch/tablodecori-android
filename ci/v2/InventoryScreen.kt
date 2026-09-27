package com.tablodecori.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.tablodecori.app.data.StockPlanner
import com.tablodecori.app.data.StockQuantity
import com.tablodecori.app.data.db.StockItemEntity

@Composable fun InventoryScreen(vm:MainViewModel){
    val stock by vm.stockItems.collectAsState()
    val materials by vm.materials.collectAsState()
    val settings by vm.settings.collectAsState()
    val context=LocalContext.current
    val options=materials.filter { it.enabled && !it.deleted && StockPlanner.unitFor(it)!=null }
    val percent=settings?.lowStockPercent?:10
    val low=stock.count { it.targetMicros>0L && it.onHandMicros*100L<=it.targetMicros*percent }
    var onlyLow by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<StockItemEntity?>(null)}
    var adding by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->vm.notify(if(granted)"اعلان موجودی فعال شد." else "اعلان موجودی به اجازه گوشی نیاز دارد.")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text("کنترل انبار",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("مصرف سفارش‌های جدید خودکار کم می‌شود؛ تغییر یا حذف سفارش موجودی را اصلاح می‌کند. چاپ عکس در انبار محاسبه نمی‌شود.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${stock.size} قلم · $low قلم کم‌موجود",fontWeight=FontWeight.Bold)
            Text("هشدار وقتی موجودی به $percent٪ موجودی مطلوب یا کمتر برسد.")
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                OutlinedButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("فعال‌کردن اعلان انبار")}
        } } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={adding=true},modifier=Modifier.weight(1f)){Text("افزودن قلم انبار")}
            FilterChip(selected=onlyLow,onClick={onlyLow=!onlyLow},label={Text("کم‌موجود")})
        } }
        val shown=if(onlyLow)stock.filter { it.targetMicros>0L && it.onHandMicros*100L<=it.targetMicros*percent } else stock
        if(shown.isEmpty()) item { Text(if(onlyLow)"قلم کم‌موجودی دیده نمی‌شود." else "هنوز موجودی ثبت نشده است. اقلام را اضافه کنید یا پس از ثبت سفارش، اقلام مصرفی اینجا ظاهر می‌شوند.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(shown,key={it.id}){item->
            val status=when { item.targetMicros<=0L->"موجودی مطلوب تنظیم نشده";item.onHandMicros<=0L->"ناموجود";item.onHandMicros*100L<=item.targetMicros*percent->"کم‌موجود";else->"موجودی مناسب" }
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text(item.materialName+(if(item.variantKey.isNotBlank())" · ${item.variantKey}" else ""),fontWeight=FontWeight.Bold)
                Text("موجود: ${StockQuantity.format(item.onHandMicros)} ${StockQuantity.unitLabel(item.unit)}")
                Text("موجودی مطلوب: ${StockQuantity.format(item.targetMicros)} ${StockQuantity.unitLabel(item.unit)}")
                Text(status,color=if(status=="موجودی مناسب")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                TextButton(onClick={editing=item}){Text("اصلاح موجودی")}
            } }
        }
    }
    if(adding){
        var selectedId by remember(options){mutableStateOf(options.firstOrNull()?.id.orEmpty())}
        var expanded by remember{mutableStateOf(false)}
        var variant by remember{mutableStateOf("")}
        var current by remember{mutableStateOf("")}
        var target by remember{mutableStateOf("")}
        val selected=options.firstOrNull{it.id==selectedId}
        val variantLabel=when(selectedId){"backboard_3mm","pack_carton","pack_foam"->"ابعاد، مانند 50x70";"frame_pvc"->"رنگ فریم، مانند طلایی";else->""}
        AlertDialog(onDismissRequest={adding=false},title={Text("افزودن موجودی")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Box { OutlinedButton(onClick={expanded=true}){Text(selected?.name?:"انتخاب متریال")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){options.forEach{m->DropdownMenuItem(text={Text(m.name)},onClick={selectedId=m.id;variant="";expanded=false})}} }
            if(variantLabel.isNotBlank()) OutlinedTextField(variant,{variant=it},label={Text(variantLabel)},singleLine=true)
            OutlinedTextField(current,{current=it},label={Text("موجودی فعلی (${StockQuantity.unitLabel(selected?.let { StockPlanner.unitFor(it) }?:"PIECE")})")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            OutlinedTextField(target,{target=it},label={Text("موجودی مطلوب")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        }},confirmButton={Button(onClick={runCatching { vm.addStock(selectedId,variant,StockQuantity.parse(current),StockQuantity.parse(target));adding=false }.onFailure { vm.notify(it.message?:"مقدار نامعتبر است.") }}){Text("ثبت")}},dismissButton={TextButton(onClick={adding=false}){Text("انصراف")}})
    }
    editing?.let{item->
        var current by remember(item.id){mutableStateOf(StockQuantity.format(item.onHandMicros))}
        var target by remember(item.id){mutableStateOf(StockQuantity.format(item.targetMicros))}
        AlertDialog(onDismissRequest={editing=null},title={Text("${item.materialName} ${item.variantKey}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("موجودی واقعی را پس از شمارش انبار وارد کنید.")
            OutlinedTextField(current,{current=it},label={Text("موجودی فعلی (${StockQuantity.unitLabel(item.unit)})")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            OutlinedTextField(target,{target=it},label={Text("موجودی مطلوب")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        }},confirmButton={Button(onClick={runCatching { vm.saveStock(item.id,StockQuantity.parse(current),StockQuantity.parse(target));editing=null }.onFailure { vm.notify(it.message?:"مقدار نامعتبر است.") }}){Text("ذخیره")}},dismissButton={TextButton(onClick={editing=null}){Text("انصراف")}})
    }
}
