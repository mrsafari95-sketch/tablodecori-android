package com.tablodecori.app.ui.screens

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
                                    Column(Modifier.weight(1f)){Text(part.name,fontWeight=FontWeight.SemiBold);Text(if(part.id=="pack_foam"||part.id=="pack_carton")"قیمت بر اساس ابعاد ست" else "هزینه ثابت برای هر بسته‌بندی",style=MaterialTheme.typography.bodySmall)}
                                    if(part.id=="pack_foam"||part.id=="pack_carton")TextButton(onClick={sizePricingMaterial=part}){Text("جدول ابعاد")}
                                    TextButton(onClick={editing=part}){Text("ویرایش")}
                                }
                            }
                        }
                    }
                }
            }
        }
        item{OutlinedButton(onClick={vm.resetDefaults()},modifier=Modifier.fillMaxWidth()){Text("ریست به حالت اولیه")}}
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
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(isPhoto)"قیمت عکس لابراتوار" else "جدول ابعاد "+material.name)},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(isPhoto){
                Text("ابعاد عکس ثابت هستند؛ فقط قیمت خرید لابراتوار را برای هر سایز وارد کنید.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if(!isPhoto)IconButton(onClick={vm.deleteSizePrice(r.id)}){Icon(Icons.Rounded.Delete,"حذف")}
                }
            }
        }
    },confirmButton={Button(onClick=onDismiss){Text("بستن")}})
}

@Composable private fun MaterialDialog(initial:MaterialEntity?,onDismiss:()->Unit,onSave:(MaterialEntity)->Unit){
    val now=System.currentTimeMillis(); var name by remember{mutableStateOf(initial?.name?:"")}; var price by remember{mutableStateOf((initial?.priceToman?:0).toString())};var rate by remember{mutableStateOf(((initial?.rateBasisPoints?:0)/100.0).toString())};var waste by remember{mutableStateOf(((initial?.wasteBasisPoints?:0)/100.0).toString())};var cat by remember{mutableStateOf(initial?.category?:"PRODUCTION")};var type by remember{mutableStateOf(initial?.calculationType?:"PER_SET")};var expanded by remember{mutableStateOf(false)};var catExpanded by remember{mutableStateOf(false)};var formulaMode by remember{mutableStateOf(initial?.formulaMode?:"STANDARD")};var formula by remember{mutableStateOf(initial?.customFormula?:"")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"متغیر جدید" else "ویرایش متغیر")},text={Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("نام")},singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(formulaMode=="STANDARD",{formulaMode="STANDARD"},{Text("استاندارد")});FilterChip(formulaMode=="CUSTOM",{formulaMode="CUSTOM"},{Text("فرمول سفارشی")})};if(formulaMode=="CUSTOM")OutlinedTextField(formula,{formula=it},label={Text("فرمول")},supportingText={Text("width, height, qty, unitPrice, area, perimeter, count, subtotal")},modifier=Modifier.fillMaxWidth()) else Box{OutlinedButton(onClick={catExpanded=true},modifier=Modifier.fillMaxWidth()){Text(when(cat){"PRODUCTION"->"ساخت تابلو";"PACKAGING"->"بسته‌بندی";else->"هزینه عمومی"})};DropdownMenu(catExpanded,{catExpanded=false}){listOf("PRODUCTION" to "ساخت تابلو","PACKAGING" to "بسته‌بندی","OVERHEAD" to "هزینه عمومی").forEach{(v,l)->DropdownMenuItem({Text(l)},{cat=v;catExpanded=false})}}};Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(calcLabel(type))};DropdownMenu(expanded,{expanded=false}){CalculationType.entries.forEach{v->DropdownMenuItem({Text(calcLabel(v.name))},{type=v.name;expanded=false})}}};if(type=="PERCENT_OF_COST")OutlinedTextField(rate,{rate=it},label={Text("درصد")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)else OutlinedTextField(price,{price=it},label={Text("قیمت (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true);OutlinedTextField(waste,{waste=it},label={Text("پرت درصد")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)}},confirmButton={Button(onClick={val e=MaterialEntity(id=initial?.id?:UUID.randomUUID().toString(),name=name,category=cat,calculationType=type,priceToman=price.toLongOrNull()?:0,rateBasisPoints=((rate.toDoubleOrNull()?:0.0)*100).toInt(),wasteBasisPoints=((waste.toDoubleOrNull()?:0.0)*100).toInt(),enabled=initial?.enabled?:true,smartKind=initial?.smartKind?:"GENERIC",formulaMode=formulaMode,customFormula=formula,deleted=false,createdAt=initial?.createdAt?:now,updatedAt=now);onSave(e)},enabled=name.isNotBlank()){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable fun ProductsScreen(vm:MainViewModel){
    val priced by vm.pricedProducts.collectAsState(); val vars by vm.materials.collectAsState(); var q by remember{mutableStateOf("")};var edit by remember{mutableStateOf<ProductModel?>(null)};var create by remember{mutableStateOf(false)};var details by remember{mutableStateOf<PricedProduct?>(null)};var del by remember{mutableStateOf<ProductModel?>(null)}
    val list=priced.filter{q.isBlank()||it.product.name.contains(q)||it.product.pieces.any{p->"${p.widthCm}×${p.heightCm}".contains(q)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{SectionTitle("محصولات و ست‌ها","ترکیب تابلوها با قیمت‌گذاری زنده"){Button(onClick={create=true}){Text("+ ست جدید")}}};item{OutlinedTextField(q,{q=it},label={Text("جستجو در نام یا ابعاد")},modifier=Modifier.fillMaxWidth(),singleLine=true)};items(list,key={it.product.id}){p->AppCard{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(p.product.name,fontWeight=FontWeight.Bold);PieceChips(p.product.pieces)};MoneyText(p.pricing.finalPriceToman)};Spacer(Modifier.height(8.dp));Text("هزینه بدون سود: ${money(p.pricing.costBeforeProfitToman)} · سود: ${money(p.pricing.profitToman)}",style=MaterialTheme.typography.bodySmall);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Row{TextButton(onClick={details=p}){Text("ریز هزینه")};TextButton(onClick={edit=p.product}){Text("ویرایش")};TextButton(onClick={vm.duplicateProduct(p.product.id)}){Text("کپی")}};Switch(p.product.active,{vm.toggleProduct(p.product.id,it)})};TextButton(onClick={del=p.product}){Text("حذف",color=MaterialTheme.colorScheme.error)}}}}
    if(create||edit!=null) ProductDialog(vm,vars,edit,{create=false;edit=null}){id,name,pieces,ids,profit,mode,formula->vm.saveProduct(id,name,pieces,ids,manualProfit=profit,profitMode=mode,profitFormula=formula);create=false;edit=null}
    details?.let{p->AlertDialog(onDismissRequest={details=null},title={Text(p.product.name)},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())){PieceChips(p.product.pieces);Spacer(Modifier.height(10.dp));PricingBreakdown(p.pricing)}},confirmButton={Button(onClick={details=null}){Text("بستن")}})}
    del?.let{p->AlertDialog(onDismissRequest={del=null},title={Text("حذف محصول")},text={Text("این محصول از فهرست فعال حذف می‌شود؛ سفارش‌های تاریخی و Snapshot مالی دست‌نخورده می‌مانند.")},confirmButton={Button(onClick={vm.deleteProduct(p.id);del=null}){Text("حذف")}},dismissButton={TextButton(onClick={del=null}){Text("انصراف")}})}
}

@Composable private fun ProductDialog(vm:MainViewModel,vars:List<MaterialEntity>,initial:ProductModel?,onDismiss:()->Unit,onSave:(String?,String,List<PieceInput>,Set<String>,Long,String,String)->Unit){
    var name by remember{mutableStateOf(initial?.name?:"")};var manualProfit by remember{mutableStateOf(if((initial?.manualProfitToman?:0L)==0L) "" else initial!!.manualProfitToman.toString())};var profitMode by remember{mutableStateOf(initial?.profitMode?:"MANUAL")};var profitFormula by remember{mutableStateOf(initial?.profitFormula?:"")}
    val pieces=remember{mutableStateListOf<PieceInput>().apply{addAll(initial?.pieces?:listOf(PieceInput(40,60,1)))}}
    val ids=remember{mutableStateMapOf<String,Boolean>().apply{vars.filter{(!it.id.startsWith("photo_")||it.id=="photo_lab")&&!it.id.startsWith("pack_")&&it.id!="foam_packaging"&&it.id!="carton_packaging"}.forEach{put(it.id,initial?.enabledMaterialIds?.contains(it.id)?:true)}}}
    var preview by remember{mutableStateOf<PricingResult?>(null)}
    LaunchedEffect(pieces.toList(),ids.toMap(),vars){
        if(vars.isNotEmpty() && pieces.all{it.widthCm>0&&it.heightCm>0&&it.quantity>0}) try{preview=vm.calculate(pieces.toList(),ids.filterValues{it}.keys)}catch(_:Throwable){}
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"ساخت ست جدید" else "ویرایش ست")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(name,{name=it},label={Text("نام محصول")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Text("تابلوهای داخل ست",fontWeight=FontWeight.Bold)
            pieces.forEachIndexed{i,p->PieceEditorRow(p,{pieces[i]=it},{if(pieces.size>1)pieces.removeAt(i)})}
            OutlinedButton(onClick={pieces.add(PieceInput(20,30,1))},modifier=Modifier.fillMaxWidth()){Text("+ افزودن سایز")}
            Text("سود این ست",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(profitMode=="MANUAL",{profitMode="MANUAL"},{Text("دستی")});FilterChip(profitMode=="FORMULA",{profitMode="FORMULA"},{Text("فرمول سفارشی")})};if(profitMode=="MANUAL")OutlinedTextField(manualProfit,{manualProfit=it.filter{ch->ch.isDigit()}},label={Text("سود دستی (تومان)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth()) else OutlinedTextField(profitFormula,{profitFormula=it},label={Text("فرمول سود")},supportingText={Text("متغیرها: count, area, perimeter, cost")},modifier=Modifier.fillMaxWidth());Text("اجزای فعال",fontWeight=FontWeight.Bold)
            Text("بسته‌بندی محصول",fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("40×60","50×70","60×90","70×100").forEach{size->FilterChip(selected=false,onClick={},label={Text("بسته‌بندی "+size)})}}
            vars.filter{(!it.id.startsWith("photo_")||it.id=="photo_lab")&&!it.id.startsWith("pack_")&&it.id!="foam_packaging"&&it.id!="carton_packaging"}.forEach{m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(m.name);Switch(ids[m.id]?:false,{ids[m.id]=it})}}
            preview?.let{Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("قیمت زنده");MoneyText(it.finalPriceToman)}}}
        }
    },confirmButton={Button(onClick={onSave(initial?.id,name,pieces.toList(),ids.filterValues{it}.keys,manualProfit.toLongOrNull()?:0L,profitMode,profitFormula)},enabled=name.isNotBlank()&&pieces.all{it.widthCm>0&&it.heightCm>0&&it.quantity>0}){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable fun PieceEditorRow(p:PieceInput,onChange:(PieceInput)->Unit,onDelete:()->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){SmallNumber("عرض",p.widthCm,{onChange(p.copy(widthCm=it))},Modifier.weight(1f));SmallNumber("ارتفاع",p.heightCm,{onChange(p.copy(heightCm=it))},Modifier.weight(1f));SmallNumber("تعداد",p.quantity,{onChange(p.copy(quantity=it))},Modifier.weight(.8f));TextButton(onClick=onDelete){Text("×",color=MaterialTheme.colorScheme.error)}}}
@Composable private fun SmallNumber(label:String,value:Int,on:(Int)->Unit,modifier:Modifier){var text by remember(value){mutableStateOf(if(value==0) "" else value.toString())};OutlinedTextField(text,{raw->val digits=raw.filter{it.isDigit()};text=digits;if(digits.isNotEmpty())on(digits.toIntOrNull()?:0)},label={Text(label)},placeholder={Text("0")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=modifier,singleLine=true)}

@Composable fun QuickScreen(vm:MainViewModel){
    val vars by vm.materials.collectAsState()
    val foamPrices by vm.sizePrices("pack_foam").collectAsState(initial=emptyList())
    val cartonPrices by vm.sizePrices("pack_carton").collectAsState(initial=emptyList())
    val scope=rememberCoroutineScope()
    val pieces=remember{mutableStateListOf(PieceInput(40,60,1))}
    val ids=remember{mutableStateMapOf<String,Boolean>()}
    var result by remember{mutableStateOf<PricingResult?>(null)};var saveDialog by remember{mutableStateOf(false)}
    var packageOpen by remember{mutableStateOf(false)};var selectedPackage by remember{mutableStateOf("40x60")}
    val selectable=vars.filter{it.id in setOf("frame_pvc","glass","backboard_3mm","frame_supplies","production_labor","photo_lab","unexpected_cost","inflation")}
    LaunchedEffect(selectable){selectable.forEach{if(it.id !in ids)ids[it.id]=it.enabled};ids["packaging_bundle"]=true}
    fun recalc(){scope.launch{try{result=vm.calculate(pieces.toList(),ids.filterValues{it}.keys)}catch(_:Throwable){}}}
    LaunchedEffect(pieces.toList(),ids.toMap(),vars,foamPrices,cartonPrices,selectedPackage){if(vars.isNotEmpty())recalc()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{SectionTitle("محاسبه سریع","عکس لابراتوار از روی ابعاد هر تابلو خودکار محاسبه می‌شود.")}
        item{AppCard{Text("تابلوهای داخل ست",fontWeight=FontWeight.Bold);pieces.forEachIndexed{i,p->PieceEditorRow(p,{pieces[i]=it;recalc()},{if(pieces.size>1){pieces.removeAt(i);recalc()}})};OutlinedButton(onClick={pieces.add(PieceInput(20,30,1));recalc()},modifier=Modifier.fillMaxWidth()){Text("+ افزودن سایز")}}}
        item{AppCard{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("بسته‌بندی",fontWeight=FontWeight.Bold);Text("یک بسته‌بندی برای کل ست انتخاب کنید.",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={packageOpen=!packageOpen}){Text(if(packageOpen)"بستن" else "انتخاب")}}
            if(packageOpen) Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(40 to 60,50 to 70,60 to 90,70 to 100).forEach{(w,h)->val key=w.toString()+"x"+h.toString();FilterChip(selected=selectedPackage==key,onClick={selectedPackage=key;packageOpen=false;recalc()},label={Text("بسته‌بندی "+w+"×"+h)},leadingIcon=if(selectedPackage==key){{Text("✓")}}else null)}
            }
            val wh=selectedPackage.split("x").map{it.toInt()};val foam=foamPrices.firstOrNull{it.widthCm==wh[0]&&it.heightCm==wh[1]}?.priceToman?:0L;val carton=cartonPrices.firstOrNull{it.widthCm==wh[0]&&it.heightCm==wh[1]}?.priceToman?:0L
            val tape=vars.firstOrNull{it.id=="pack_tape"}?.priceToman?:0L;val labor=vars.firstOrNull{it.id=="pack_labor"}?.priceToman?:0L
            Text("انتخاب‌شده: بسته‌بندی "+wh[0]+"×"+wh[1]+" — "+money(foam+carton+tape+labor),fontWeight=FontWeight.SemiBold)
        }}
        item{AppCard{Text("متغیرهای قیمت",fontWeight=FontWeight.Bold);selectable.forEach{m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(m.name);Text(if(m.id=="photo_lab")"خودکار بر اساس ابعاد تابلو" else calcLabel(m.calculationType),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(ids[m.id]?:false,{ids[m.id]=it;recalc()})}};Divider();Text("بسته‌بندی "+selectedPackage.replace("x","×"),fontWeight=FontWeight.Bold)}}
        result?.let{r->item{AppCard{PricingBreakdown(r);Button(onClick={saveDialog=true},modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Text("ذخیره به عنوان محصول")}}}}
        item{OutlinedButton(onClick={pieces.clear();pieces.add(PieceInput(40,60,1));ids.clear();selectedPackage="40x60";vm.resetDefaults()},modifier=Modifier.fillMaxWidth()){Text("ریست به حالت اولیه")}}
    }
    if(saveDialog){var name by remember{mutableStateOf("ست جدید")};AlertDialog(onDismissRequest={saveDialog=false},title={Text("ذخیره محصول")},text={OutlinedTextField(name,{name=it},label={Text("نام محصول")})},confirmButton={Button(onClick={vm.saveProduct(null,name,pieces.toList(),ids.filterValues{it}.keys);saveDialog=false}){Text("ذخیره")}},dismissButton={TextButton(onClick={saveDialog=false}){Text("انصراف")}})}
}


