package com.tablodecori.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
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
    val visible=vars.filter{it.id in setOf("frame_pvc","glass","backboard_3mm","frame_supplies","production_labor","photo_lab","packaging_bundle","unexpected_cost","inflation")}
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
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(r.widthCm.toString()+"×"+r.heightCm+(if(r.pieceCount>0)" • "+r.pieceCount+" تکه" else ""),Modifier.width(105.dp),fontWeight=FontWeight.Bold)
                    OutlinedTextField(rowPrice,{rowPrice=it.filter(Char::isDigit)},label={Text("تومان")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.width(170.dp))
                    TextButton(onClick={vm.saveSizePrice(r.copy(priceToman=rowPrice.toLongOrNull()?:0L,updatedAt=System.currentTimeMillis()))}){Text("ثبت")}
                    IconButton(onClick={deletingRow=r}){Icon(Icons.Rounded.Delete,"حذف",tint=MaterialTheme.colorScheme.error)}
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
    val priced by vm.pricedProducts.collectAsState(); val vars by vm.materials.collectAsState(); var q by remember{mutableStateOf("")};var edit by remember{mutableStateOf<ProductModel?>(null)};var create by remember{mutableStateOf(false)};var details by remember{mutableStateOf<PricedProduct?>(null)};var del by remember{mutableStateOf<ProductModel?>(null)}
    val list=priced.filter{q.isBlank()||it.product.name.contains(q)||it.product.pieces.any{p->"${p.widthCm}×${p.heightCm}".contains(q)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{SectionTitle("محصولات و ست‌ها","ترکیب تابلوها با قیمت‌گذاری زنده"){Button(onClick={create=true}){Text("+ ست جدید")}}};item{OutlinedTextField(q,{q=it},label={Text("جستجو در نام یا ابعاد")},modifier=Modifier.fillMaxWidth(),singleLine=true)};items(list,key={it.product.id}){p->AppCard{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(p.product.name,fontWeight=FontWeight.Bold);PieceChips(p.product.pieces)};MoneyText(p.pricing.finalPriceToman)};Spacer(Modifier.height(8.dp));Text("هزینه بدون سود: ${money(p.pricing.costBeforeProfitToman)} · سود: ${money(p.pricing.profitToman)}",style=MaterialTheme.typography.bodySmall);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Row{TextButton(onClick={details=p}){Text("ریز هزینه")};TextButton(onClick={edit=p.product}){Text("ویرایش")};TextButton(onClick={vm.duplicateProduct(p.product.id)}){Text("کپی")}};Switch(p.product.active,{vm.toggleProduct(p.product.id,it)})};TextButton(onClick={del=p.product}){Text("حذف",color=MaterialTheme.colorScheme.error)}}}}
    if(create||edit!=null) ProductDialog(vm,vars,edit,{create=false;edit=null}){id,name,pieces,ids,profit,mode,formula,packageKey->vm.saveProduct(id,name,pieces,ids,manualProfit=profit,profitMode=mode,profitFormula=formula,packagingSizeKey=packageKey);create=false;edit=null}
    details?.let{p->AlertDialog(onDismissRequest={details=null},title={Text(p.product.name)},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())){PieceChips(p.product.pieces);Spacer(Modifier.height(10.dp));PricingBreakdown(p.pricing)}},confirmButton={Button(onClick={details=null}){Text("بستن")}})}
    del?.let{p->AlertDialog(onDismissRequest={del=null},title={Text("حذف محصول")},text={Text("این محصول از فهرست فعال حذف می‌شود؛ سفارش‌های تاریخی و Snapshot مالی دست‌نخورده می‌مانند.")},confirmButton={Button(onClick={vm.deleteProduct(p.id);del=null}){Text("حذف")}},dismissButton={TextButton(onClick={del=null}){Text("انصراف")}})}
}

@Composable private fun ProductDialog(vm:MainViewModel,vars:List<MaterialEntity>,initial:ProductModel?,onDismiss:()->Unit,onSave:(String?,String,List<PieceInput>,Set<String>,Long,String,String,String)->Unit){
    var name by remember{mutableStateOf(initial?.name?:"")};var profitPercent by remember{mutableStateOf(initial?.profitFormula?.substringAfter("cost*","")?.substringBefore("/100","")?.takeIf{it.isNotBlank()}?:"0")};val profitMode="FORMULA"
    val pieces=remember{mutableStateListOf<PieceInput>().apply{addAll(initial?.pieces?:listOf(PieceInput(40,60,1)))}}
    val ids=remember(initial?.id,vars.map{it.id to it.enabled}){mutableStateMapOf<String,Boolean>().apply{vars.filter{(!it.id.startsWith("photo_")||it.id=="photo_lab")&&!it.id.startsWith("pack_")&&it.id!="foam_packaging"&&it.id!="carton_packaging"}.forEach{v->put(v.id,v.enabled)}}}
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
    var preview by remember{mutableStateOf<PricingResult?>(null)}
    LaunchedEffect(pieces.toList(),ids.toMap(),vars,selectedProductPackage){
        if(vars.isNotEmpty() && pieces.all{it.widthCm>0&&it.heightCm>0&&it.quantity>0}) try{preview=vm.calculate(pieces.toList(),ids.filterValues{it}.keys,selectedProductPackage)}catch(_:Throwable){}
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"ساخت ست جدید" else "ویرایش ست")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(name,{name=it},label={Text("نام محصول")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Text("تابلوهای داخل ست",fontWeight=FontWeight.Bold)
            pieces.forEachIndexed{i,p->PieceEditorRow(p,{pieces[i]=it},{if(pieces.size>1)pieces.removeAt(i)})}
            OutlinedButton(onClick={pieces.add(PieceInput(20,30,1))},modifier=Modifier.fillMaxWidth()){Text("+ افزودن سایز")}
            Text("سود این ست",fontWeight=FontWeight.Bold);OutlinedTextField(profitPercent,{profitPercent=it.filter{ch->ch.isDigit()||ch=='.'}},label={Text("درصد سود این ست")},suffix={Text("٪")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true);Text("درصد سود برای هر ست مستقل است.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("اجزای فعال",fontWeight=FontWeight.Bold)
            Text("بسته‌بندی محصول",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selected=selectedProductPackage.isBlank(),onClick={selectedProductPackage="";ids["packaging_bundle"]=true},label={Text("خودکار")},leadingIcon=if(selectedProductPackage.isBlank()){{Text("✓")}}else null);productPackageSizes.forEach{(w,h)->val key=w.toString()+"x"+h.toString();FilterChip(selected=selectedProductPackage==key,onClick={selectedProductPackage=key;ids["packaging_bundle"]=true},label={Text("بسته‌بندی "+w+"×"+h)},leadingIcon=if(selectedProductPackage==key){{Text("✓")}}else null)}}
            vars.filter{(!it.id.startsWith("photo_")||it.id=="photo_lab")&&!it.id.startsWith("pack_")&&it.id!="foam_packaging"&&it.id!="carton_packaging"}.forEach{m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(m.name);Switch(ids[m.id]?:false,{ids[m.id]=it})}}
            preview?.let{Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("قیمت زنده");MoneyText(it.finalPriceToman)}}}
        }
    },confirmButton={Button(onClick={onSave(initial?.id,name,pieces.toList(),ids.filterValues{it}.keys,0L,profitMode,if(profitPercent.isBlank()) "" else "cost*"+profitPercent+"/100",selectedProductPackage)},enabled=name.isNotBlank()&&pieces.all{it.widthCm>0&&it.heightCm>0&&it.quantity>0}){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
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
    val pieces=rememberSaveable(saver=listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<PieceInput>,Int>(
        save={list->list.flatMap{p->listOf(p.widthCm,p.heightCm,p.quantity)}},
        restore={saved->mutableStateListOf<PieceInput>().apply{saved.chunked(3).forEach{v->if(v.size==3)add(PieceInput(v[0],v[1],v[2]))}}}
    )){mutableStateListOf(PieceInput(40,60,1))}
    val ids=rememberSaveable(saver=listSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String,Boolean>,String>(
        save={map->map.map{(id,on)->id+"="+if(on)"1" else "0"}},
        restore={saved->mutableStateMapOf<String,Boolean>().apply{saved.forEach{entry->val cut=entry.lastIndexOf('=');if(cut>0)put(entry.substring(0,cut),entry.substring(cut+1)=="1")}}}
    )){mutableStateMapOf<String,Boolean>()}
    var result by remember{mutableStateOf<PricingResult?>(null)};var saveDialog by rememberSaveable{mutableStateOf(false)}
    var packageOpen by rememberSaveable{mutableStateOf(false)};var selectedPackage by rememberSaveable{mutableStateOf("")}
    val quickListState=androidx.compose.foundation.lazy.rememberLazyListState()
    val packageSizes=(foamPrices+cartonPrices+tapePrices+laborPrices)
        .filter{it.enabled&&it.widthCm>0&&it.heightCm>0}
        .map{minOf(it.widthCm,it.heightCm) to maxOf(it.widthCm,it.heightCm)}
        .distinct()
        .sortedWith(compareBy<Pair<Int,Int>>{it.first*it.second}.thenBy{it.first}.thenBy{it.second})
    val selectable=vars.filter{it.id in setOf("frame_pvc","glass","backboard_3mm","frame_supplies","production_labor","photo_lab","unexpected_cost","inflation")}
    LaunchedEffect(selectable){selectable.forEach{if(it.id !in ids)ids[it.id]=it.enabled};ids["packaging_bundle"]=true}
    fun recalc(){scope.launch{try{result=vm.calculate(pieces.toList(),ids.filterValues{it}.keys,selectedPackage)}catch(_:Throwable){}}}
    LaunchedEffect(pieces.toList(),ids.toMap(),vars,foamPrices,cartonPrices,tapePrices,laborPrices,selectedPackage){if(vars.isNotEmpty())recalc()}
    LazyColumn(Modifier.fillMaxSize(),state=quickListState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("محاسبه سریع","عکس لابراتوار از روی ابعاد هر تابلو خودکار محاسبه می‌شود.")}
        item{AppCard{Text("تابلوهای داخل ست",fontWeight=FontWeight.Bold);pieces.forEachIndexed{i,p->PieceEditorRow(p,{pieces[i]=it;recalc()},{if(pieces.size>1){pieces.removeAt(i);recalc()}})};OutlinedButton(onClick={pieces.add(PieceInput(20,30,1));recalc()},modifier=Modifier.fillMaxWidth()){Text("+ افزودن سایز")}}}
        item{AppCard{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("بسته‌بندی",fontWeight=FontWeight.Bold);Text("یک بسته‌بندی برای کل ست انتخاب کنید.",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={packageOpen=!packageOpen}){Text(if(packageOpen)"بستن" else "انتخاب")}}
            if(packageOpen) Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=selectedPackage.isBlank(),onClick={selectedPackage="";packageOpen=false;recalc()},label={Text("خودکار")},leadingIcon=if(selectedPackage.isBlank()){{Text("✓")}}else null);packageSizes.forEach{(w,h)->val key=w.toString()+"x"+h.toString();FilterChip(selected=selectedPackage==key,onClick={selectedPackage=key;packageOpen=false;recalc()},label={Text("بسته‌بندی "+w+"×"+h)},leadingIcon=if(selectedPackage==key){{Text("✓")}}else null)}
            }
            if(selectedPackage.isBlank()){Text("انتخاب‌شده: خودکار — "+money(result?.lines?.firstOrNull{it.materialId=="packaging_bundle"}?.amountToman?:0L),fontWeight=FontWeight.SemiBold)}else{val wh=selectedPackage.split("x").map{it.toInt()};val foam=foamPrices.firstOrNull{(it.widthCm==wh[0]&&it.heightCm==wh[1])||(it.widthCm==wh[1]&&it.heightCm==wh[0])}?.priceToman?:vars.firstOrNull{it.id=="pack_foam"}?.priceToman?:0L;val carton=cartonPrices.firstOrNull{(it.widthCm==wh[0]&&it.heightCm==wh[1])||(it.widthCm==wh[1]&&it.heightCm==wh[0])}?.priceToman?:vars.firstOrNull{it.id=="pack_carton"}?.priceToman?:0L;val tape=tapePrices.firstOrNull{(it.widthCm==wh[0]&&it.heightCm==wh[1])||(it.widthCm==wh[1]&&it.heightCm==wh[0])}?.priceToman?:vars.firstOrNull{it.id=="pack_tape"}?.priceToman?:0L;val labor=laborPrices.firstOrNull{(it.widthCm==wh[0]&&it.heightCm==wh[1])||(it.widthCm==wh[1]&&it.heightCm==wh[0])}?.priceToman?:vars.firstOrNull{it.id=="pack_labor"}?.priceToman?:0L;Text("انتخاب‌شده: بسته‌بندی "+wh[0]+"×"+wh[1]+" — "+money(foam+carton+tape+labor),fontWeight=FontWeight.SemiBold)}
        }}
        item{AppCard{Text("متغیرهای قیمت",fontWeight=FontWeight.Bold);selectable.forEach{m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(m.name);Text(if(m.id=="photo_lab")"خودکار بر اساس ابعاد تابلو" else calcLabel(m.calculationType),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(ids[m.id]?:false,{ids[m.id]=it;recalc()})}};Divider();Text(if(selectedPackage.isBlank()) "بسته‌بندی خودکار" else "بسته‌بندی "+selectedPackage.replace("x","×"),fontWeight=FontWeight.Bold)}}
        result?.let{r->item{AppCard{PricingBreakdown(r);Button(onClick={saveDialog=true},modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Text("ذخیره به عنوان محصول")}}}}
    }
    if(saveDialog){var name by remember{mutableStateOf("ست جدید")};AlertDialog(onDismissRequest={saveDialog=false},title={Text("ذخیره محصول")},text={OutlinedTextField(name,{name=it},label={Text("نام محصول")})},confirmButton={Button(onClick={vm.saveProduct(null,name,pieces.toList(),ids.filterValues{it}.keys,packagingSizeKey=selectedPackage);saveDialog=false}){Text("ذخیره")}},dismissButton={TextButton(onClick={saveDialog=false}){Text("انصراف")}})}
}




@Composable fun SettingsScreen(vm:MainViewModel){
    val current by vm.settings.collectAsState()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var rounding by remember(current?.roundingStepToman){mutableStateOf((current?.roundingStepToman?:10000L).toString())}
    var shipping by remember(current?.shippingDefaultToman){mutableStateOf((current?.shippingDefaultToman?:0L).toString())}
    var dark by remember(current?.darkMode){mutableStateOf(current?.darkMode?:false)}
    var pendingImport by remember{mutableStateOf<String?>(null)}
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null) scope.launch {
            runCatching {
                val data=vm.exportFullBackup()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use{it.write(data)}
                    ?: error("فایل بکاپ قابل نوشتن نیست.")
            }.onSuccess{vm.notify("بکاپ کامل ذخیره شد.")}.onFailure{vm.notify(it.message?:"خطا در بکاپ‌گیری")}
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
        item{SectionTitle("تنظیمات","سود از این صفحه حذف شده و برای هر ست به‌صورت درصد مستقل در ویرایش محصول تعیین می‌شود.")}
        item{AppCard{
            OutlinedTextField(rounding,{rounding=it.filter(Char::isDigit)},label={Text("گرد کردن قیمت (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(shipping,{shipping=it.filter(Char::isDigit)},label={Text("هزینه ارسال پیش‌فرض (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("حالت تیره");Switch(dark,{dark=it})}
            Button(onClick={vm.saveSettings(rounding.toLongOrNull()?:10000L,shipping.toLongOrNull()?:0L,dark)},modifier=Modifier.fillMaxWidth()){Text("ذخیره تنظیمات")}
        }}
        item{AppCard{
            Text("پشتیبان‌گیری و بازیابی",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text("بکاپ کامل شامل متریال‌ها، جدول ابعاد، محصولات، سفارش‌ها، تاریخچه قیمت و تنظیمات برنامه است.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={exportLauncher.launch("tablodecori-backup.json")},modifier=Modifier.fillMaxWidth()){Text("دریافت بکاپ کامل")}
            OutlinedButton(onClick={importLauncher.launch(arrayOf("application/json","text/plain","*/*"))},modifier=Modifier.fillMaxWidth()){Text("ایمپورت / بازیابی بکاپ")}
        }}
    }
    pendingImport?.let{data->
        AlertDialog(
            onDismissRequest={pendingImport=null},
            title={Text("بازیابی بکاپ")},
            text={Text("با بازیابی، تمام اطلاعات فعلی این گوشی با اطلاعات فایل بکاپ جایگزین می‌شود. آیا مطمئن هستید؟")},
            confirmButton={Button(onClick={
                pendingImport=null
                scope.launch{runCatching{vm.importFullBackup(data)}.onFailure{vm.notify(it.message?:"بازیابی بکاپ ناموفق بود.")}}
            }){Text("بله، بازیابی شود")}},
            dismissButton={TextButton(onClick={pendingImport=null}){Text("خیر")}}
        )
    }
}
