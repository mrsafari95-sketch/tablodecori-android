package com.tablodecori.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.data.*
import com.tablodecori.app.data.db.MaterialEntity
import com.tablodecori.app.data.db.SizePriceEntity
import com.tablodecori.app.pricing.*
import com.tablodecori.app.ui.*
import org.json.JSONObject
import com.tablodecori.app.util.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable fun VariablesScreen(vm:MainViewModel){
    val vars by vm.materials.collectAsState()
    var editing by remember{mutableStateOf<MaterialEntity?>(null)}
    var creating by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<MaterialEntity?>(null)}
    var sizePricingMaterial by remember{mutableStateOf<MaterialEntity?>(null)}
    var packagingOpen by remember{mutableStateOf(false)}
    val visible=MaterialCatalog.mainMaterials(vars)
    val packageParts=vars.filter{it.id in setOf("pack_foam","pack_carton","pack_tape","pack_labor")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("متریال‌ها و هزینه‌ها","متغیرهای اصلی قیمت؛ جزئیات عکس و بسته‌بندی داخل خودشان قرار دارد"){Button(onClick={creating=true}){Text("+ متغیر")}}}
        listOf("PRODUCTION" to "ساخت تابلو","PACKAGING" to "بسته‌بندی","OVERHEAD" to "هزینه عمومی").forEach{(cat,title)->
            val list=visible.filter{it.category==cat}
            if(list.isNotEmpty()){
                item{Text(title,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))}
                items(list,key={it.id}){m->
                    AppCard{
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text(m.name,fontWeight=FontWeight.Bold)
                                val sub=when(m.id){"photo_lab"->"۲۲ سایز ثابت؛ قیمت هر سایز مستقل";"packaging_bundle"->"یک هزینه برای کل ست؛ شامل فوم، کارتن، چسب، لیبل و دستمزد";else->if(m.calculationType=="PERCENT_OF_COST")percentFromBasisPoints(m.rateBasisPoints) else money(m.priceToman)}
                                Text(sub,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when(m.id){
                                "photo_lab"->TextButton(onClick={sizePricingMaterial=m}){Text("قیمت ابعاد")}
                                "packaging_bundle"->TextButton(onClick={packagingOpen=!packagingOpen}){Text(if(packagingOpen)"بستن جزئیات" else "جزئیات")}
                                else->TextButton(onClick={editing=m}){Text("ویرایش")}
                            }
                            Switch(checked=m.enabled,onCheckedChange={vm.toggleMaterial(m.id,it)})
                        }
                        if(m.id!="photo_lab"&&m.id!="packaging_bundle")TextButton(onClick={deleting=m}){Text("حذف",color=MaterialTheme.colorScheme.error)}
                    }
                    if(m.id=="packaging_bundle"&&packagingOpen){
                        AppCard{
                            Text("اجزای بسته‌بندی",fontWeight=FontWeight.Bold)
                            Text("این موارد جداگانه روی تابلو اعمال نمی‌شوند؛ مجموع آن‌ها یک‌بار به کل ست اضافه می‌شود.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            packageParts.forEach{part->
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                    Column(Modifier.weight(1f)){Text(part.name,fontWeight=FontWeight.SemiBold);Text("قیمت بر اساس ابعاد ست",style=MaterialTheme.typography.bodySmall)}
                                    TextButton(onClick={sizePricingMaterial=part}){Text("جدول ابعاد")}
                                    TextButton(onClick={editing=part}){Text("ویرایش")}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(creating||editing!=null) MaterialDialog(initial=editing,onDismiss={creating=false;editing=null},onSave={vm.saveMaterial(it);creating=false;editing=null})
    deleting?.let{m->AlertDialog(onDismissRequest={deleting=null},title={Text("حذف متغیر")},text={Text("اگر این متغیر در محصولی استفاده شده باشد، به‌صورت امن غیرفعال می‌شود. ادامه می‌دهید؟")},confirmButton={Button(onClick={vm.deleteMaterial(m.id);deleting=null}){Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}){Text("انصراف")}})}
    sizePricingMaterial?.let{m->SizePricingDialog(vm,m){sizePricingMaterial=null}}
}
private fun calcLabel(t:String)=when(t){"PER_SQUARE_METER"->"متر مربع";"PER_LINEAR_METER"->"متر طول";"PER_PIECE"->"هر تابلو";"PER_SET"->"هر ست";"PERCENT_OF_COST"->"درصد از هزینه";else->"بسته‌بندی هوشمند"}

@Composable private fun SizePricingDialog(vm:MainViewModel,material:MaterialEntity,onDismiss:()->Unit){
    val prices by vm.sizePrices(material.id).collectAsState(initial=emptyList())
    val isPhoto=material.id=="photo_lab"
    var w by remember{mutableStateOf("")};var h by remember{mutableStateOf("")};var count by remember{mutableStateOf("")};var price by remember{mutableStateOf("")}
    var deletingRow by remember{mutableStateOf<SizePriceEntity?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(isPhoto)"قیمت عکس لابراتوار" else "جدول ابعاد "+material.name)},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(isPhoto){
                Text("قیمت خرید لابراتوار برای هر سایز مستقل است. می‌توانید ابعاد جدید هم اضافه کنید.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(w,{w=it.filter(Char::isDigit)},label={Text("عرض")},modifier=Modifier.weight(1f));OutlinedTextField(h,{h=it.filter(Char::isDigit)},label={Text("ارتفاع")},modifier=Modifier.weight(1f))}
                OutlinedTextField(price,{price=it.filter(Char::isDigit)},label={Text("قیمت تومان")},modifier=Modifier.fillMaxWidth())
                Button(onClick={val now=System.currentTimeMillis();val ww=w.toIntOrNull()?:0;val hh=h.toIntOrNull()?:0;vm.saveSizePrice(SizePriceEntity(id=material.id+"_"+ww+"x"+hh,materialId=material.id,widthCm=ww,heightCm=hh,pieceCount=0,priceToman=price.toLongOrNull()?:0,createdAt=now,updatedAt=now));w="";h="";price=""},enabled=w.toIntOrNull()?.let{it>0}==true&&h.toIntOrNull()?.let{it>0}==true&&price.toLongOrNull()!=null,modifier=Modifier.fillMaxWidth()){Text("اضافه کردن ابعاد جدید")}
            }else{
                Text("برای هر اندازه بسته‌بندی، قیمت این جزء را تعریف کنید. تعداد تکه باعث می‌شود مثلاً ست ۳ تکه با ست ۵ تکه قیمت متفاوت داشته باشد.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(w,{w=it.filter(Char::isDigit)},label={Text("عرض")},modifier=Modifier.weight(1f));OutlinedTextField(h,{h=it.filter(Char::isDigit)},label={Text("ارتفاع")},modifier=Modifier.weight(1f))}
                OutlinedTextField(count,{count=it.filter(Char::isDigit)},label={Text("تعداد تابلو در ست؛ خالی = همه")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(price,{price=it.filter(Char::isDigit)},label={Text("قیمت تومان")},modifier=Modifier.fillMaxWidth())
                Button(onClick={val now=System.currentTimeMillis();val ww=w.toIntOrNull()?:0;val hh=h.toIntOrNull()?:0;val cc=count.toIntOrNull()?:0;vm.saveSizePrice(SizePriceEntity(id=material.id+"_"+ww+"x"+hh+"_"+cc,materialId=material.id,widthCm=ww,heightCm=hh,pieceCount=cc,priceToman=price.toLongOrNull()?:0,createdAt=now,updatedAt=now));w="";h="";count="";price=""},enabled=w.toIntOrNull()?.let{it>0}==true&&h.toIntOrNull()?.let{it>0}==true&&price.toLongOrNull()!=null,modifier=Modifier.fillMaxWidth()){Text("افزودن قیمت")}
            }
            prices.forEach{r->
                var rowPrice by remember(r.id,r.priceToman){mutableStateOf(if(r.priceToman==0L)"" else r.priceToman.toString())}
                AppCard{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(r.widthCm.toString()+"×"+r.heightCm+(if(r.pieceCount>0)" • "+r.pieceCount+" تکه" else ""),Modifier.weight(1f),fontWeight=FontWeight.Bold)
                        OutlinedTextField(rowPrice,{rowPrice=it.filter(Char::isDigit)},label={Text("تومان")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.width(150.dp))
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={vm.saveSizePrice(r.copy(priceToman=rowPrice.toLongOrNull()?:0L,updatedAt=System.currentTimeMillis()))}){Text("ثبت قیمت")}
                        TextButton(onClick={deletingRow=r}){Icon(Icons.Rounded.Delete,"حذف");Spacer(Modifier.width(4.dp));Text("حذف ابعاد",color=MaterialTheme.colorScheme.error)}
                    }
                }
            }
        }
    },confirmButton={Button(onClick=onDismiss){Text("بستن")}})
    deletingRow?.let{r->
        AlertDialog(
            onDismissRequest={deletingRow=null},
            title={Text("حذف ابعاد")},
            text={Text("ابعاد "+r.widthCm+"×"+r.heightCm+(if(r.pieceCount>0)" برای "+r.pieceCount+" تکه" else "")+" حذف شود؟ این ابعاد دیگر در محاسبه قیمت و انتخاب‌های مرتبط استفاده نمی‌شود.")},
            confirmButton={Button(onClick={vm.deleteSizePrice(r.id);deletingRow=null}){Text("حذف")}},
            dismissButton={TextButton(onClick={deletingRow=null}){Text("انصراف")}}
        )
    }
}

@Composable private fun MaterialDialog(initial:MaterialEntity?,onDismiss:()->Unit,onSave:(MaterialEntity)->Unit){
    val now=System.currentTimeMillis(); var name by remember{mutableStateOf(initial?.name?:"")}; var price by remember{mutableStateOf((initial?.priceToman?:0).toString())};var rate by remember{mutableStateOf(((initial?.rateBasisPoints?:0)/100.0).toString())};var waste by remember{mutableStateOf(((initial?.wasteBasisPoints?:0)/100.0).toString())};var cat by remember{mutableStateOf(initial?.category?:"PRODUCTION")};var type by remember{mutableStateOf(initial?.calculationType?:"PER_SET")};var expanded by remember{mutableStateOf(false)};var catExpanded by remember{mutableStateOf(false)};var formulaMode by remember{mutableStateOf(initial?.formulaMode?:"STANDARD")};var formula by remember{mutableStateOf(initial?.customFormula?:"")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"متغیر جدید" else "ویرایش متغیر")},text={Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("نام")},singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(formulaMode=="STANDARD",{formulaMode="STANDARD"},{Text("استاندارد")});FilterChip(formulaMode=="CUSTOM",{formulaMode="CUSTOM"},{Text("فرمول سفارشی")})};if(formulaMode=="CUSTOM")OutlinedTextField(formula,{formula=it},label={Text("فرمول")},supportingText={Text("width, height, qty, unitPrice, area, perimeter, count, subtotal")},modifier=Modifier.fillMaxWidth()) else Box{OutlinedButton(onClick={catExpanded=true},modifier=Modifier.fillMaxWidth()){Text(when(cat){"PRODUCTION"->"ساخت تابلو";"PACKAGING"->"بسته‌بندی";else->"هزینه عمومی"})};DropdownMenu(catExpanded,{catExpanded=false}){listOf("PRODUCTION" to "ساخت تابلو","PACKAGING" to "بسته‌بندی","OVERHEAD" to "هزینه عمومی").forEach{(v,l)->DropdownMenuItem({Text(l)},{cat=v;catExpanded=false})}}};Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(calcLabel(type))};DropdownMenu(expanded,{expanded=false}){CalculationType.entries.forEach{v->DropdownMenuItem({Text(calcLabel(v.name))},{type=v.name;expanded=false})}}};if(type=="PERCENT_OF_COST")OutlinedTextField(rate,{rate=it},label={Text("درصد")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)else OutlinedTextField(price,{price=it},label={Text("قیمت (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);OutlinedTextField(waste,{waste=it},label={Text("پرت درصد")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)}},confirmButton={Button(onClick={val e=MaterialEntity(id=initial?.id?:UUID.randomUUID().toString(),name=name,category=cat,calculationType=type,priceToman=price.toLongOrNull()?:0,rateBasisPoints=((rate.toDoubleOrNull()?:0.0)*100).toInt(),wasteBasisPoints=((waste.toDoubleOrNull()?:0.0)*100).toInt(),enabled=initial?.enabled?:true,smartKind=initial?.smartKind?:"GENERIC",formulaMode=formulaMode,customFormula=formula,deleted=false,createdAt=initial?.createdAt?:now,updatedAt=now);onSave(e)},enabled=name.isNotBlank()){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable fun ProductsScreen(vm:MainViewModel){
    val priced by vm.pricedProducts.collectAsState()
    val vars by vm.materials.collectAsState()
    val context=LocalContext.current
    var q by rememberSaveable{mutableStateOf("")}
    var sort by rememberSaveable{mutableStateOf("name")}
    var onlyActive by rememberSaveable{mutableStateOf(false)}
    var edit by remember{mutableStateOf<ProductModel?>(null)}
    var create by remember{mutableStateOf(false)}
    var details by remember{mutableStateOf<PricedProduct?>(null)}
    var del by remember{mutableStateOf<ProductModel?>(null)}
    val list=remember(priced,q,sort,onlyActive){
        val query=q.trim()
        val filtered=priced.filter{p->
            (!onlyActive||p.product.active) &&
                (query.isBlank()||p.product.name.contains(query,ignoreCase=true)||p.product.pieces.any{piece->
                    "${piece.widthCm}×${piece.heightCm}".contains(query)||"${piece.widthCm}x${piece.heightCm}".contains(query,ignoreCase=true)
                })
        }
        when(sort){
            "price"->filtered.sortedWith(compareBy<PricedProduct>{it.pricing.finalPriceToman}.thenBy{it.product.name})
            "pieces"->filtered.sortedWith(compareByDescending<PricedProduct>{it.pricing.pieceCount}.thenBy{it.product.name})
            else->filtered.sortedBy{it.product.name}
        }
    }
    // The public price list includes active products only, just as the former price-list tab did.
    val exportList=list.filter{it.product.active}
    val csvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        if(uri!=null)runCatching{requireNotNull(context.contentResolver.openOutputStream(uri)).use{it.write(Exporters.pricebookCsv(exportList).toByteArray(Charsets.UTF_8))}}
            .onSuccess{vm.notify("فهرست قیمت به صورت CSV ذخیره شد.")}
            .onFailure{vm.notify("ذخیره فایل CSV انجام نشد.")}
    }
    val pdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        if(uri!=null)runCatching{requireNotNull(context.contentResolver.openOutputStream(uri)).use{Exporters.writePricebookPdf(it,exportList)}}
            .onSuccess{vm.notify("فهرست قیمت به صورت PDF ذخیره شد.")}
            .onFailure{vm.notify("ذخیره فایل PDF انجام نشد.")}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("محصولات و ست‌ها","مدیریت ست‌ها و فهرست قیمت زنده در یک جا"){Button(onClick={create=true}){Text("+ ست جدید")}}}
        item{OutlinedTextField(q,{q=it},label={Text("جستجو در نام یا ابعاد")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=sort=="name",onClick={sort="name"},label={Text("نام")})
            FilterChip(selected=sort=="pieces",onClick={sort="pieces"},label={Text("تعداد تابلو")})
            FilterChip(selected=sort=="price",onClick={sort="price"},label={Text("قیمت")})
            FilterChip(selected=onlyActive,onClick={onlyActive=!onlyActive},label={Text("فقط فعال")})
        }}
        item{AppCard{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                listOf("CSV","PDF","چاپ","اشتراک").forEach{label->
                    TextButton(onClick={when(label){"CSV"->{csvLauncher.launch("tablodecori-pricebook.csv")};"PDF"->{pdfLauncher.launch("tablodecori-pricebook.pdf")};"چاپ"->{Exporters.printPricebook(context,exportList)};else->{shareProductPriceList(context,exportList)}}},enabled=exportList.isNotEmpty(),contentPadding=PaddingValues(0.dp),modifier=Modifier.weight(1f).height(36.dp)){Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1)}
                }
            }
        }}
        if(list.isEmpty())item{AppCard{Text(if(onlyActive)"محصول فعالِ مطابق جستجو پیدا نشد." else "محصولی مطابق جستجو پیدا نشد.")}}
        items(list,key={it.product.id}){p->AppCard{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Column(Modifier.weight(1f)){
                    Text(p.product.name,fontWeight=FontWeight.Bold)
                    PieceChips(p.product.pieces)
                    if(!p.product.active)Text("غیرفعال؛ در خروجی قیمت‌ها نمی‌آید.",style=MaterialTheme.typography.bodySmall)
                }
                MoneyText(p.pricing.finalPriceToman)
            }
            if(p.product.photoFileName.isNotBlank())OrderPhotoThumbnail(p.product.photoFileName)
            Spacer(Modifier.height(8.dp))
            Text("هزینه بدون سود: ${money(p.pricing.costBeforeProfitToman)} · سود: ${money(p.pricing.profitToman)}",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                TextButton(onClick={copyProductText(context,"💰 قیمت: ${money(p.pricing.finalPriceToman)}");vm.notify("قیمت کپی شد.")}){Text("کپی قیمت")}
                TextButton(onClick={
                    val dimensions=p.product.pieces.joinToString("\n"){piece->"${piece.quantity} عدد ${piece.widthCm} در ${piece.heightCm}"}
                    copyProductText(context,"🖼️ ${p.product.name}\n📐 ابعاد ست:\n$dimensions\n\n💰 قیمت: ${money(p.pricing.finalPriceToman)}")
                    vm.notify("نام ست، ابعاد و قیمت کپی شد.")
                }){Text("کپی ابعاد و قیمت")}
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                TextButton(onClick={details=p}){Text("ریز هزینه")}
                TextButton(onClick={edit=p.product}){Text("ویرایش")}
                TextButton(onClick={vm.duplicateProduct(p.product.id)}){Text("تکثیر ست")}
                TextButton(onClick={del=p.product}){Text("حذف",color=MaterialTheme.colorScheme.error)}
                Switch(p.product.active,{vm.toggleProduct(p.product.id,it)})
            }
        }}
    }
    if(create||edit!=null) ProductDialog(vm,vars,edit,{create=false;edit=null}){id,name,pieces,ids,profit,mode,formula,packageKey,type,designCost,photoUri,removePhoto->vm.saveProduct(id,name,pieces,ids,manualProfit=profit,profitMode=mode,profitFormula=formula,packagingSizeKey=packageKey,productType=type,designMaterialsCostToman=designCost,selectedPhotoUri=photoUri,removePhoto=removePhoto);create=false;edit=null}
    details?.let{p->AlertDialog(onDismissRequest={details=null},title={Text(p.product.name)},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())){PieceChips(p.product.pieces);Spacer(Modifier.height(10.dp));PricingBreakdown(p.pricing)}},confirmButton={Button(onClick={details=null}){Text("بستن")}})}
    del?.let{p->AlertDialog(onDismissRequest={del=null},title={Text("حذف محصول")},text={Text("این محصول از فهرست فعال حذف می‌شود؛ سفارش‌های تاریخی و Snapshot مالی دست‌نخورده می‌مانند.")},confirmButton={Button(onClick={vm.deleteProduct(p.id);del=null}){Text("حذف")}},dismissButton={TextButton(onClick={del=null}){Text("انصراف")}})}
}

private fun shareProductPriceList(context:Context,list:List<PricedProduct>){
    val text=list.joinToString("\n\n"){p->"🖼️ ${p.product.name}\n📐 ابعاد: ${p.product.pieces.joinToString(" + "){ "${it.quantity} عدد ${it.widthCm}×${it.heightCm}" }}\n💰 قیمت: ${money(p.pricing.finalPriceToman)}"}
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"اشتراک فهرست قیمت"))
}

private fun copyProductText(context:Context,text:String){
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("tablodecori",text))
}

@Composable private fun ProductDialog(vm:MainViewModel,vars:List<MaterialEntity>,initial:ProductModel?,onDismiss:()->Unit,onSave:(String?,String,List<PieceInput>,Set<String>,Long,String,String,String,String,Long,String?,Boolean)->Unit){
    var addingReliefMaterial by remember{mutableStateOf(false)}
    val newReliefIds=remember{mutableStateListOf<String>()}
    var name by remember{mutableStateOf(initial?.name?:"")};var profitPercent by remember{mutableStateOf(initial?.profitFormula?.substringAfter("cost*","")?.substringBefore("/100","")?.takeIf{it.isNotBlank()}?:"0")};var profitMode by remember(initial?.id){mutableStateOf(if(initial?.profitMode=="FORMULA")"FORMULA" else "MANUAL")};var manualProfit by remember(initial?.id){mutableStateOf((initial?.manualProfitToman?:0L).toString())}
    var productType by remember(initial?.id){mutableStateOf(initial?.productType?:"STANDARD")}
    var designCost by remember(initial?.id){mutableStateOf((initial?.designMaterialsCostToman?:0L).toString())}
    var productPhotoUri by remember(initial?.id){mutableStateOf<String?>(null)}
    var removeProductPhoto by remember(initial?.id){mutableStateOf(false)}
    val productPhotoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null){productPhotoUri=uri.toString();removeProductPhoto=false}}
    val pieces=remember{mutableStateListOf<PieceInput>().apply{addAll(initial?.pieces?:listOf(PieceInput(40,60,1)))}}
    val selectable=MaterialCatalog.selectableForProduct(vars)
    val shownMaterials=if(productType=="RELIEF")selectable.filter(MaterialCatalog::selectableForRelief) else selectable.filter{it.smartKind!=MaterialCatalog.RELIEF_CUSTOM}
    val savedCanonical=MaterialCatalog.selectedCanonicalIds(initial?.enabledMaterialIds.orEmpty())
    val ids=remember(initial?.id){mutableStateMapOf<String,Boolean>()}
    LaunchedEffect(selectable.map{it.id to it.enabled}){
        selectable.forEach{m->if(m.id !in ids)ids[m.id]=if(initial==null)m.enabled && m.smartKind!=MaterialCatalog.RELIEF_CUSTOM else m.id in savedCanonical}
    }
    val selectedIds=ids.filterValues{it}.keys.intersect(shownMaterials.map{it.id}.toSet() + if(productType=="RELIEF")newReliefIds else emptyList())
    val foamPrices by vm.sizePrices("pack_foam").collectAsState(initial=emptyList())
    val cartonPrices by vm.sizePrices("pack_carton").collectAsState(initial=emptyList())
    val tapePrices by vm.sizePrices("pack_tape").collectAsState(initial=emptyList())
    val laborPrices by vm.sizePrices("pack_labor").collectAsState(initial=emptyList())
    val productPackageSizes=(foamPrices+cartonPrices+tapePrices+laborPrices)
        .filter{it.enabled&&it.widthCm>0&&it.heightCm>0}
        .map{minOf(it.widthCm,it.heightCm) to maxOf(it.widthCm,it.heightCm)}
        .distinct()
        .sortedWith(compareBy<Pair<Int,Int>>{it.first*it.second}.thenBy{it.first}.thenBy{it.second})
    var selectedProductPackage by remember(initial?.id){mutableStateOf(initial?.packagingSizeKey.orEmpty())}
    var manualPackaging by remember(initial?.id){mutableStateOf(initial?.packagingSizeKey?.takeIf{it.startsWith("manual:")}?.removePrefix("manual:")?:"0")}
    var preview by remember{mutableStateOf<PricingResult?>(null)}
    LaunchedEffect(pieces.toList(),ids.toMap(),vars,foamPrices,cartonPrices,tapePrices,laborPrices,selectedProductPackage,profitPercent,profitMode,manualProfit,productType,designCost){
        if(vars.isNotEmpty() && pieces.all{it.widthCm>0&&it.heightCm>0&&it.quantity>0}) try{preview=vm.calculate(pieces.toList(),selectedIds,selectedProductPackage,if(profitMode=="FORMULA")"cost*"+(profitPercent.ifBlank{"0"})+"/100" else "",if(profitMode=="MANUAL")manualProfit.toLongOrNull()?:0L else 0L,if(productType=="RELIEF")designCost.toLongOrNull()?:0L else 0L)}catch(_:Throwable){preview=null}
    }
    if(!addingReliefMaterial) AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"ساخت ست جدید" else "ویرایش ست")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(name,{name=it},label={Text("نام محصول")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Text("عکس ست (اختیاری)",fontWeight=FontWeight.SemiBold)
            OrderPhotoPreview(productPhotoUri,if(removeProductPhoto)"" else initial?.photoFileName.orEmpty(),compact=true)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                OutlinedButton(onClick={productPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text(if(productPhotoUri!=null||(!removeProductPhoto&&initial?.photoFileName?.isNotBlank()==true))"تغییر عکس" else "افزودن عکس")}
                if(productPhotoUri!=null||(!removeProductPhoto&&initial?.photoFileName?.isNotBlank()==true))TextButton(onClick={productPhotoUri=null;removeProductPhoto=true}){Text("حذف عکس")}
            }
            preview?.let{Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("قیمت برآوردی",fontWeight=FontWeight.Bold);MoneyText(it.finalPriceToman)}}}
            Divider()
            Text("نوع محصول",fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=productType=="STANDARD",onClick={productType="STANDARD"},label={Text("تابلو معمولی")});FilterChip(selected=productType=="RELIEF",onClick={productType="RELIEF";ids["frame_pvc"]=true;ids["packaging_bundle"]=true},label={Text("تابلو برجسته")})}
            if(productType=="RELIEF"){
                OutlinedTextField(designCost,{designCost=it.filter(Char::isDigit)},label={Text("هزینه دستی مواد طراحی (تومان)")},supportingText={Text("فقط برای این ست؛ قاب و بسته‌بندی از قیمت‌های فعلی محاسبه می‌شوند.")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedButton(onClick={addingReliefMaterial=true},modifier=Modifier.fillMaxWidth()){Text("+ متغیر سفارشی تابلو برجسته")}
            }
            Text("تابلوهای داخل ست",fontWeight=FontWeight.Bold)
            pieces.forEachIndexed{i,p->PieceEditorRow(p,{pieces[i]=it},{if(pieces.size>1)pieces.removeAt(i)})}
            OutlinedButton(onClick={pieces.add(PieceInput(20,30,1))},modifier=Modifier.fillMaxWidth()){Text("+ افزودن سایز")}
            Divider()
            Text("سود این ست",fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=profitMode=="MANUAL",onClick={profitMode="MANUAL"},label={Text("مبلغ ثابت")});FilterChip(selected=profitMode=="FORMULA",onClick={profitMode="FORMULA"},label={Text("درصد از هزینه")})}
            if(profitMode=="MANUAL") OutlinedTextField(manualProfit,{manualProfit=it.filter(Char::isDigit)},label={Text("سود دستی (تومان)")},supportingText={Text("پیش‌فرض صفر تومان است؛ این مبلغ به هزینه ست افزوده می‌شود.")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            else OutlinedTextField(profitPercent,{profitPercent=it.filter{ch->ch.isDigit()||ch=='.'}},label={Text("درصد سود این ست")},suffix={Text("٪")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true)
            Divider()
            Text("اجزای فعال",fontWeight=FontWeight.Bold)
            Text("بسته‌بندی محصول",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selected=selectedProductPackage.isBlank(),onClick={selectedProductPackage="";ids["packaging_bundle"]=true},label={Text("خودکار")});productPackageSizes.forEach{(w,h)->val key=w.toString()+"x"+h.toString();FilterChip(selected=selectedProductPackage==key,onClick={selectedProductPackage=key;ids["packaging_bundle"]=true},label={Text("بسته‌بندی "+w+"×"+h)})};FilterChip(selected=selectedProductPackage.startsWith("manual:"),onClick={selectedProductPackage="manual:${manualPackaging.ifBlank{"0"}}";ids["packaging_bundle"]=true},label={Text("قیمت دستی")})}
            if(selectedProductPackage.startsWith("manual:"))OutlinedTextField(manualPackaging,{manualPackaging=it.filter(Char::isDigit);selectedProductPackage="manual:${manualPackaging.ifBlank{"0"}}"},label={Text("هزینه بسته‌بندی برای کل ست (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            if(ids["packaging_bundle"]==true){
                val key=PricingEngine.choosePackagingSize(pieces.toList(),foamPrices+cartonPrices,selectedProductPackage)
                Text(if(selectedProductPackage.startsWith("manual:"))"هزینه بسته‌بندی دستی: ${money(preview?.packagingCostToman?:0L)}" else "اندازه بسته انتخابی: ${key.replace("x","×")} · هزینه برآوردشده: ${money(preview?.packagingCostToman?:0L)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            }
            shownMaterials.forEach{m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(m.name);Switch(ids[m.id]?:false,{ids[m.id]=it})}}
        }
    },confirmButton={Button(onClick={onSave(initial?.id,name,pieces.toList(),selectedIds,if(profitMode=="MANUAL")manualProfit.toLongOrNull()?:0L else 0L,profitMode,if(profitMode=="FORMULA")"cost*"+profitPercent.ifBlank{"0"}+"/100" else "",selectedProductPackage,productType,if(productType=="RELIEF")designCost.toLongOrNull()?:0L else 0L,productPhotoUri,removeProductPhoto)},enabled=name.isNotBlank()&&pieces.all{it.widthCm>0&&it.heightCm>0&&it.quantity>0}&&(profitMode!="MANUAL"||manualProfit.toLongOrNull()!=null)&&(productType!="RELIEF"||designCost.toLongOrNull()!=null)){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
    if(addingReliefMaterial) CustomMaterialDialog("متغیر تابلو برجسته","قیمت این متغیر در محاسبهٔ ست برجسته لحاظ می‌شود.",onDismiss={addingReliefMaterial=false}){materialName,price,type->
        val id=vm.createReliefMaterial(materialName,price,type)
        if(id!=null){newReliefIds.add(id);ids[id]=true;addingReliefMaterial=false}
        id!=null
    }
}

@Composable private fun CustomMaterialDialog(title:String,description:String,onDismiss:()->Unit,onSave:suspend (String,Long,String)->Boolean){
    val scope=rememberCoroutineScope()
    var name by remember{mutableStateOf("")}
    var price by remember{mutableStateOf("")}
    var type by remember{mutableStateOf("PER_SET")}
    var saving by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name,{name=it},label={Text("نام متغیر")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(price,{price=it.filter(Char::isDigit)},label={Text("قیمت واحد (تومان)")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Text("روش محاسبه",fontWeight=FontWeight.SemiBold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("PER_SET" to "هر ست","PER_PIECE" to "هر تابلو","PER_SQUARE_METER" to "مترمربع","PER_LINEAR_METER" to "متر طول").forEach{(key,label)->
                FilterChip(selected=type==key,onClick={type=key},label={Text(label)})
            }
        }
    }},confirmButton={Button(onClick={saving=true;scope.launch { if(!onSave(name.trim(),price.toLongOrNull()?:0L,type))saving=false }},enabled=!saving&&name.isNotBlank()&&price.toLongOrNull()!=null){Text(if(saving)"در حال ثبت…" else "افزودن")}},dismissButton={TextButton(onClick=onDismiss,enabled=!saving){Text("انصراف")}})
}

@Composable fun PieceEditorRow(p:PieceInput,onChange:(PieceInput)->Unit,onDelete:()->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){SmallNumber("عرض",p.widthCm,{onChange(p.copy(widthCm=it))},Modifier.weight(1f));SmallNumber("ارتفاع",p.heightCm,{onChange(p.copy(heightCm=it))},Modifier.weight(1f));SmallNumber("تعداد",p.quantity,{onChange(p.copy(quantity=it))},Modifier.weight(.8f));TextButton(onClick=onDelete){Text("×",color=MaterialTheme.colorScheme.error)}}}
@Composable private fun SmallNumber(label:String,value:Int,on:(Int)->Unit,modifier:Modifier){var text by remember(value){mutableStateOf(if(value==0) "" else value.toString())};OutlinedTextField(text,{raw->val digits=raw.filter{it.isDigit()};text=digits;if(digits.isNotEmpty())on(digits.toIntOrNull()?:0)},label={Text(label)},placeholder={Text("0")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=modifier,singleLine=true)}

@Composable fun QuickScreen(vm:MainViewModel){
    val vars by vm.materials.collectAsState()
    val foamPrices by vm.sizePrices("pack_foam").collectAsState(initial=emptyList())
    val cartonPrices by vm.sizePrices("pack_carton").collectAsState(initial=emptyList())
    val tapePrices by vm.sizePrices("pack_tape").collectAsState(initial=emptyList())
    val laborPrices by vm.sizePrices("pack_labor").collectAsState(initial=emptyList())
    val scope=rememberCoroutineScope()
    val draft by vm.quickDraft.collectAsState()
    val pieces=draft.pieces
    val ids=draft.enabledIds
    val selectedPackage=draft.selectedPackage
    var quickManualPackaging by rememberSaveable{mutableStateOf("0")}
    val packageOpen=draft.packageOpen
    val manualProfit=draft.manualProfitToman
    val productType=draft.productType
    val designCost=draft.designMaterialsCostToman
    var result by remember{mutableStateOf<PricingResult?>(null)}
    var saveDialog by rememberSaveable{mutableStateOf(false)}
    var addReliefQuick by remember{mutableStateOf(false)}
    val newReliefQuickIds=remember{mutableStateListOf<String>()}
    val quickListState=androidx.compose.foundation.lazy.rememberLazyListState()
    val packageSizes=(foamPrices+cartonPrices+tapePrices+laborPrices)
        .filter{it.enabled&&it.widthCm>0&&it.heightCm>0}
        .map{minOf(it.widthCm,it.heightCm) to maxOf(it.widthCm,it.heightCm)}
        .distinct()
        .sortedWith(compareBy<Pair<Int,Int>>{it.first*it.second}.thenBy{it.first}.thenBy{it.second})
    val selectable=vars.filter{it.id in setOf("frame_pvc","glass","backboard_3mm","frame_supplies","production_labor","photo_lab","unexpected_cost","inflation") || it.smartKind==MaterialCatalog.RELIEF_CUSTOM}
    LaunchedEffect(selectable){vm.initializeQuickIds(selectable.associate{it.id to (it.enabled && it.smartKind!=MaterialCatalog.RELIEF_CUSTOM)} + ("packaging_bundle" to true))}
    val quickSelectedIds=ids.filterValues{it}.keys.let{if(productType=="RELIEF")it.intersect(selectable.filter(MaterialCatalog::selectableForRelief).map{m->m.id}.toSet() + newReliefQuickIds + "packaging_bundle") else it.filterTo(mutableSetOf()){id->selectable.any{m->m.id==id&&m.smartKind!=MaterialCatalog.RELIEF_CUSTOM}||id=="packaging_bundle"}}
    fun recalc(){scope.launch{try{result=vm.calculate(pieces.toList(),quickSelectedIds,selectedPackage,manualProfitToman=manualProfit,designMaterialsCostToman=if(productType=="RELIEF")designCost else 0L)}catch(_:Throwable){result=null}}}
    LaunchedEffect(pieces.toList(),ids.toMap(),vars,foamPrices,cartonPrices,tapePrices,laborPrices,selectedPackage,manualProfit,productType,designCost){if(vars.isNotEmpty())recalc()}
    LazyColumn(Modifier.fillMaxSize(),state=quickListState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("محاسبه سریع",if(productType=="RELIEF")"مواد طراحی دستی؛ قاب و بسته‌بندی با قیمت روز" else "عکس لابراتوار از روی ابعاد هر تابلو خودکار محاسبه می‌شود.")}
        item{AppCard{Text("نوع تابلو",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=productType=="STANDARD",onClick={vm.setQuickProductType("STANDARD")},label={Text("معمولی")});FilterChip(selected=productType=="RELIEF",onClick={vm.setQuickProductType("RELIEF")},label={Text("برجسته")})};if(productType=="RELIEF"){OutlinedTextField(designCost.toString(),{vm.setQuickDesignCost(it.filter(Char::isDigit).toLongOrNull()?:0L)},label={Text("هزینه دستی مواد طراحی (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedButton(onClick={addReliefQuick=true},modifier=Modifier.fillMaxWidth()){Text("+ متغیر سفارشی تابلو برجسته")}}}}
        item{AppCard{Text("تابلوهای داخل ست",fontWeight=FontWeight.Bold);pieces.forEachIndexed{i,p->PieceEditorRow(p,{vm.updateQuickPiece(i,it)},{vm.removeQuickPiece(i)})};OutlinedButton(onClick={vm.addQuickPiece()},modifier=Modifier.fillMaxWidth()){Text("+ افزودن سایز")}}}
        item{AppCard{Text("سود دستی",fontWeight=FontWeight.Bold);OutlinedTextField(manualProfit.toString(),{vm.setQuickProfit(it.filter(Char::isDigit).toLongOrNull()?:0L)},label={Text("مبلغ سود (تومان)")},supportingText={Text("پیش‌فرض صفر تومان؛ مستقیم به قیمت ست افزوده می‌شود.")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)}}
        item{AppCard{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("بسته‌بندی",fontWeight=FontWeight.Bold);Text("یک بسته‌بندی برای کل ست انتخاب کنید.",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={vm.toggleQuickPackage()}){Text(if(packageOpen)"بستن" else "انتخاب")}}
            if(packageOpen) Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=selectedPackage.isBlank(),onClick={vm.setQuickPackage("")},label={Text("خودکار")});packageSizes.forEach{(w,h)->val key=w.toString()+"x"+h.toString();FilterChip(selected=selectedPackage==key,onClick={vm.setQuickPackage(key)},label={Text("بسته‌بندی "+w+"×"+h)})};FilterChip(selected=selectedPackage.startsWith("manual:"),onClick={vm.setQuickPackage("manual:${quickManualPackaging.ifBlank{"0"}}")},label={Text("قیمت دستی")})
            }
            if(selectedPackage.startsWith("manual:"))OutlinedTextField(quickManualPackaging,{quickManualPackaging=it.filter(Char::isDigit);vm.setQuickPackage("manual:${quickManualPackaging.ifBlank{"0"}}")},label={Text("هزینه بسته‌بندی برای کل ست (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            if("packaging_bundle" in quickSelectedIds){
                val key=PricingEngine.choosePackagingSize(pieces,foamPrices+cartonPrices,selectedPackage)
                Text(if(selectedPackage.startsWith("manual:"))"هزینه بسته‌بندی دستی: ${money(result?.packagingCostToman?:0L)}" else "${if(selectedPackage.isBlank())"خودکار" else "اندازهٔ انتخابی"}: بسته‌بندی ${key.replace("x","×")} · ${money(result?.packagingCostToman?:0L)}",fontWeight=FontWeight.SemiBold)
                if(selectedPackage.isBlank())Text("اندازه مناسب با جاگیری تابلوها انتخاب می‌شود؛ برای ابعاد بیرون از جدول، هزینه از اندازه‌های ثبت‌شده برآورد می‌شود.",style=MaterialTheme.typography.bodySmall)
            }
        }}
        item{AppCard{Text(if(productType=="RELIEF")"اجزای تابلو برجسته" else "متغیرهای قیمت",fontWeight=FontWeight.Bold);selectable.filter{if(productType=="RELIEF")MaterialCatalog.selectableForRelief(it) else it.smartKind!=MaterialCatalog.RELIEF_CUSTOM}.forEach{m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(m.name);Text(if(m.id=="photo_lab")"خودکار بر اساس ابعاد تابلو" else calcLabel(m.calculationType),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(ids[m.id]?:false,{vm.setQuickId(m.id,it)})}};Divider();Text(if(selectedPackage.isBlank())"بسته‌بندی خودکار" else if(selectedPackage.startsWith("manual:"))"بسته‌بندی با قیمت دستی" else "بسته‌بندی "+selectedPackage.replace("x","×"),fontWeight=FontWeight.Bold)}}
        result?.let{r->item{AppCard{PricingBreakdown(r);Button(onClick={saveDialog=true},modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Text("ذخیره به عنوان محصول")}}}}
    }
    if(saveDialog){var name by remember{mutableStateOf("ست جدید")};AlertDialog(onDismissRequest={saveDialog=false},title={Text("ذخیره محصول")},text={OutlinedTextField(name,{name=it},label={Text("نام محصول")})},confirmButton={Button(onClick={vm.saveProduct(null,name,pieces.toList(),quickSelectedIds,manualProfit=manualProfit,packagingSizeKey=selectedPackage,productType=productType,designMaterialsCostToman=if(productType=="RELIEF")designCost else 0L);saveDialog=false}){Text("ذخیره")}},dismissButton={TextButton(onClick={saveDialog=false}){Text("انصراف")}})}
    if(addReliefQuick) CustomMaterialDialog("متغیر تابلو برجسته","در محاسبهٔ سریع و ست‌های برجسته قابل انتخاب می‌شود.",onDismiss={addReliefQuick=false}){materialName,price,type->
        val id=vm.createReliefMaterial(materialName,price,type)
        if(id!=null){newReliefQuickIds.add(id);vm.setQuickId(id,true);addReliefQuick=false}
        id!=null
    }
}




@Composable fun SettingsScreen(vm:MainViewModel){
    val current by vm.settings.collectAsState()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var rounding by remember(current?.roundingStepToman){mutableStateOf((current?.roundingStepToman?:10000L).toString())}
    var shipping by remember(current?.shippingDefaultToman){mutableStateOf((current?.shippingDefaultToman?:0L).toString())}
    var dark by remember(current?.darkMode){mutableStateOf(current?.darkMode?:false)}
    var depositPercent by remember(current?.defaultDepositPercent){mutableStateOf((current?.defaultDepositPercent?:0).toString())}
    var defaultFrameColor by remember(current?.defaultFrameColor){mutableStateOf(current?.defaultFrameColor.orEmpty())}
    var frameColorOptions by remember(current?.frameColorOptions){mutableStateOf(current?.frameColorOptions?:"مشکی، سفید، طلایی، نقره‌ای، چوبی")}
    var suggestCodRemainder by remember(current?.suggestCodRemainder){mutableStateOf(current?.suggestCodRemainder?:true)}
    var defaultShippingPayer by remember(current?.defaultShippingPayer){mutableStateOf(current?.defaultShippingPayer?:"RECIPIENT")}
    var defaultOrderStatus by remember(current?.defaultOrderStatus){mutableStateOf(current?.defaultOrderStatus?:"PREPARING")}
    var lowStockPercent by remember(current?.lowStockPercent){mutableStateOf((current?.lowStockPercent?:10).toString())}
    var openingCash by remember(current?.openingCashToman){mutableStateOf((current?.openingCashToman?:0L).toString())}
    val stockPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)vm.refreshStockAlerts()}
    var pendingImport by remember{mutableStateOf<String?>(null)}
    var exportIncludesPlanner by remember{mutableStateOf(true)}
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null) scope.launch {
            runCatching {
                val data=vm.exportFullBackup(exportIncludesPlanner)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use{it.write(data)}
                    ?: error("فایل بکاپ قابل نوشتن نیست.")
            }.onSuccess{
                context.getSharedPreferences("workshop_backup",Context.MODE_PRIVATE).edit().putLong("last_export_at",System.currentTimeMillis()).apply()
                vm.notify(if(exportIncludesPlanner)"بکاپ کامل ذخیره شد." else "بکاپ اطلاعات کارگاه ذخیره شد.")
            }.onFailure{vm.notify(it.message?:"ذخیرهٔ بکاپ انجام نشد.")}
        }
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null) scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}
                    ?: error("فایل بکاپ قابل خواندن نیست.")
            }.onSuccess{pendingImport=it}.onFailure{vm.notify(it.message?:"خطا در خواندن بکاپ")}
        }
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{SectionTitle("تنظیمات کارگاه","پیش‌فرض‌های قیمت‌گذاری، سفارش، انبار و پشتیبان‌گیری")}
        item{AppCard{
            OutlinedTextField(rounding,{rounding=it.filter(Char::isDigit)},label={Text("گرد کردن قیمت (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(shipping,{shipping=it.filter(Char::isDigit)},label={Text("هزینه ارسال پیش‌فرض (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("حالت تیره");Switch(dark,{dark=it})}
            Button(onClick={vm.saveSettings(rounding.toLongOrNull()?:10000L,shipping.toLongOrNull()?:0L,dark)},modifier=Modifier.fillMaxWidth()){Text("ذخیره تنظیمات")}
        }}
        item{AppCard{
            Text("تنظیمات سفارش",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text("مقدارهای پیشنهادی هنگام ثبت سفارش جدید؛ هر سفارش جداگانه قابل تغییر است.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(depositPercent,{depositPercent=it.filter(Char::isDigit)},label={Text("درصد بیعانه پیشنهادی")},suffix={Text("٪")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(defaultFrameColor,{defaultFrameColor=it},label={Text("رنگ قاب پیش‌فرض")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(frameColorOptions,{frameColorOptions=it},label={Text("رنگ‌های پیشنهادی قاب")},supportingText={Text("رنگ‌ها را با ویرگول جدا کنید؛ رنگ دلخواه در سفارش نیز قابل تایپ است.")},minLines=2,maxLines=3,modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("پیشنهاد مانده به‌عنوان پرداخت درب منزل",modifier=Modifier.weight(1f));Switch(suggestCodRemainder,{suggestCodRemainder=it})}
            Text("پرداخت پیش‌فرض هزینه ارسال",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=defaultShippingPayer=="RECIPIENT",onClick={defaultShippingPayer="RECIPIENT"},label={Text("پس‌کرایه")});FilterChip(selected=defaultShippingPayer=="SENDER",onClick={defaultShippingPayer="SENDER"},label={Text("فروشنده")})}
            Text("وضعیت پیش‌فرض سفارش جدید",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("PREPARING" to "در آماده‌سازی","READY" to "آماده ارسال","SENT" to "ارسال‌شده").forEach{(key,label)->FilterChip(selected=defaultOrderStatus==key,onClick={defaultOrderStatus=key},label={Text(label)})}}
            Button(onClick={vm.saveOrderPreferences(depositPercent.toInt(),defaultFrameColor,frameColorOptions,suggestCodRemainder,defaultShippingPayer,defaultOrderStatus)},enabled=depositPercent.toIntOrNull()?.let{it in 0..100}==true,modifier=Modifier.fillMaxWidth()){Text("ذخیره تنظیمات سفارش")}
        }}
        item{AppCard{
            Text("هشدار موجودی انبار",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text("وقتی موجودی هر قلم به این درصد از موجودی مطلوب یا کمتر برسد، اعلان فرستاده می‌شود.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(lowStockPercent,{lowStockPercent=it.filter(Char::isDigit)},label={Text("آستانه هشدار موجودی")},suffix={Text("٪")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(5,10,20).forEach{value->FilterChip(selected=lowStockPercent==value.toString(),onClick={lowStockPercent=value.toString()},label={Text("$value٪")})}}
            Button(onClick={vm.saveStockThreshold(lowStockPercent.toInt());if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)stockPermission.launch(Manifest.permission.POST_NOTIFICATIONS)},enabled=lowStockPercent.toIntOrNull()?.let{it in 1..100}==true,modifier=Modifier.fillMaxWidth()){Text("ذخیره آستانه انبار")}
        }}
        item{AppCard{
            Text("موجودی آغازین گزارش مالی",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text("اگر از قبل پولی برای کارگاه مانده است، اینجا وارد کنید. مانده ماه‌های بعد از دریافتی‌ها و هزینه‌های ثبت‌شده محاسبه می‌شود.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(openingCash,{openingCash=it.filter{ch->ch.isDigit()||ch=='-'}},label={Text("موجودی آغازین (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            Button(onClick={vm.saveOpeningCash(openingCash.toLong())},enabled=openingCash.toLongOrNull()!=null,modifier=Modifier.fillMaxWidth()){Text("ذخیره موجودی آغازین")}
        }}
        item{AppCard{
            Text("پشتیبان‌گیری و بازیابی",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text("بکاپ کامل همهٔ اطلاعات را نگه می‌دارد. بکاپ کارگاه شامل سفارش‌ها، قیمت و موجودی متریال، محصولات، هزینه‌ها و عکس‌هاست؛ برنامه‌ریز شخصی در آن نیست.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={exportIncludesPlanner=true;exportLauncher.launch("tablodecori-full-backup.json")},modifier=Modifier.fillMaxWidth()){Text("بکاپ کامل اپ")}
            OutlinedButton(onClick={exportIncludesPlanner=false;exportLauncher.launch("tablodecori-business-backup.json")},modifier=Modifier.fillMaxWidth()){Text("بکاپ اطلاعات کارگاه، بدون برنامه‌ریز")}
            OutlinedButton(onClick={importLauncher.launch(arrayOf("application/json","text/plain","*/*"))},modifier=Modifier.fillMaxWidth()){Text("ایمپورت / بازیابی بکاپ")}
        }}
    }
    pendingImport?.let{data->
        AlertDialog(
            onDismissRequest={pendingImport=null},
            title={Text("بازیابی بکاپ")},
            text={Text(if(runCatching{JSONObject(data).optString("scope")=="business"}.getOrDefault(false))"اطلاعات کارگاه با این بکاپ جایگزین می‌شود؛ برنامه‌ریز شخصی فعلی باقی می‌ماند. ادامه می‌دهید؟" else "با بازیابی، تمام اطلاعات فعلی این گوشی با اطلاعات فایل بکاپ جایگزین می‌شود. ادامه می‌دهید؟")},
            confirmButton={Button(onClick={
                pendingImport=null
                scope.launch{runCatching{vm.importFullBackup(data)}.onFailure{vm.notify(it.message?:"بازیابی بکاپ ناموفق بود.")}}
            }){Text("بله، بازیابی شود")}},
            dismissButton={TextButton(onClick={pendingImport=null}){Text("خیر")}}
        )
    }
}
