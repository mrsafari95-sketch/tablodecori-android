package com.tablodecori.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.tablodecori.app.data.OrderFormInput
import com.tablodecori.app.data.PricedProduct
import com.tablodecori.app.data.db.AppSettingsEntity
import com.tablodecori.app.data.db.SentOrderEntity
import com.tablodecori.app.util.PersianDate
import com.tablodecori.app.util.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A full order page leaves room for the customer, artwork and payment sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderEditorScreen(
    products: List<PricedProduct>,
    settings: AppSettingsEntity?,
    initial: SentOrderEntity?,
    onDismiss: () -> Unit,
    onSave: suspend (OrderFormInput) -> Boolean,
) {
    BackHandler(onBack = onDismiss)
    val scope=rememberCoroutineScope()
    val initialProductId=initial?.productId?.takeIf{id->products.any{it.product.id==id}}?:products.firstOrNull()?.product?.id.orEmpty()
    var pid by rememberSaveable(initial?.id){mutableStateOf(initialProductId)}
    var date by rememberSaveable(initial?.id){mutableLongStateOf(initial?.dateEpochMillis?:System.currentTimeMillis())}
    var customer by rememberSaveable(initial?.id){mutableStateOf(initial?.customerName.orEmpty())}
    var instagram by rememberSaveable(initial?.id){mutableStateOf(initial?.instagramId.orEmpty())}
    var phone by rememberSaveable(initial?.id){mutableStateOf(initial?.phone.orEmpty())}
    var province by rememberSaveable(initial?.id){mutableStateOf(initial?.province.orEmpty())}
    var city by rememberSaveable(initial?.id){mutableStateOf(initial?.city.orEmpty())}
    var address by rememberSaveable(initial?.id){mutableStateOf(initial?.addressDetails.orEmpty())}
    var postal by rememberSaveable(initial?.id){mutableStateOf(initial?.postalCode.orEmpty())}
    var frameColor by rememberSaveable(initial?.id){mutableStateOf(initial?.frameColor?:settings?.defaultFrameColor.orEmpty())}
    var note by rememberSaveable(initial?.id){mutableStateOf(initial?.note.orEmpty())}
    var shipping by rememberSaveable(initial?.id){mutableStateOf((initial?.shippingCostToman?:settings?.shippingDefaultToman?:0L).toString())}
    var quote by rememberSaveable(initial?.id){mutableStateOf(initial?.quotedTotalToman?.toString().orEmpty())}
    var deposit by rememberSaveable(initial?.id){mutableStateOf((initial?.depositToman?:0L).toString())}
    var otherPaid by rememberSaveable(initial?.id){mutableStateOf((initial?.otherPaidToman?:0L).toString())}
    var codDue by rememberSaveable(initial?.id){mutableStateOf((initial?.codDueToman?:0L).toString())}
    var codCollected by rememberSaveable(initial?.id){mutableStateOf((initial?.codCollectedToman?:0L).toString())}
    var photoUri by rememberSaveable(initial?.id){mutableStateOf<String?>(null)}
    var removePhoto by rememberSaveable(initial?.id){mutableStateOf(false)}
    var quoteAuto by rememberSaveable(initial?.id){mutableStateOf(initial==null)}
    var depositAuto by rememberSaveable(initial?.id){mutableStateOf(initial==null)}
    var codAuto by rememberSaveable(initial?.id){mutableStateOf(initial==null && settings?.suggestCodRemainder!=false)}
    var picker by rememberSaveable(initial?.id){mutableStateOf(false)}
    var productMenu by rememberSaveable(initial?.id){mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}

    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->
        if(uri!=null){photoUri=uri.toString();removePhoto=false}
    }
    LaunchedEffect(pid,shipping,quoteAuto,products){
        if(quoteAuto){
            val price=products.firstOrNull{it.product.id==pid}?.pricing?.finalPriceToman
            if(price!=null) quote=runCatching{Math.addExact(price,shipping.toLongOrNull()?:0L)}.getOrNull()?.toString().orEmpty()
        }
    }
    LaunchedEffect(quote,settings?.defaultDepositPercent,depositAuto){
        if(depositAuto){
            val percent=(settings?.defaultDepositPercent?:0).coerceIn(0,100)
            deposit=((quote.toLongOrNull()?:0L)/100L*percent + (quote.toLongOrNull()?:0L)%100L*percent/100L).toString()
        }
    }
    LaunchedEffect(quote,deposit,otherPaid,codAuto){
        if(codAuto) codDue=((quote.toLongOrNull()?:0L)-(deposit.toLongOrNull()?:0L)-(otherPaid.toLongOrNull()?:0L)).coerceAtLeast(0L).toString()
    }

    val q=quote.toLongOrNull()
    val d=deposit.toLongOrNull()
    val other=otherPaid.toLongOrNull()
    val due=codDue.toLongOrNull()
    val collected=codCollected.toLongOrNull()
    val shippingValue=shipping.toLongOrNull()
    val paid=if(d!=null&&other!=null&&collected!=null) runCatching{Math.addExact(Math.addExact(d,other),collected)}.getOrNull() else null
    val valid=if(q==null||d==null||other==null||due==null||collected==null||shippingValue==null||paid==null) false
        else pid.isNotBlank()&&customer.isNotBlank()&&
            listOf(q,d,other,due,collected,shippingValue).all{it>=0L}&&
            paid<=q&&collected<=due&&d<=q&&other<=q-d&&due<=q-d-other
    val balance=if(q!=null&&paid!=null) (q-paid).coerceAtLeast(0L) else 0L
    val colors=settings?.frameColorOptions.orEmpty().split(',', '،').map{it.trim()}.filter{it.isNotBlank()}.distinct()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Text(if(initial==null)"ثبت سفارش" else "ویرایش سفارش",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            TextButton(onClick=onDismiss,enabled=!saving){Text("انصراف")}
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            OrderSection("محصول و تاریخ") {
                Box {
                    OutlinedButton(onClick={productMenu=true},modifier=Modifier.fillMaxWidth(),enabled=initial==null){
                        Text(products.firstOrNull{it.product.id==pid}?.product?.name?:initial?.productNameSnapshot?:"انتخاب محصول")
                    }
                    DropdownMenu(productMenu,{productMenu=false}){
                        products.forEach{product->DropdownMenuItem({Text(product.product.name)},{pid=product.product.id;productMenu=false;quoteAuto=true})}
                    }
                }
                OutlinedButton(onClick={picker=true},modifier=Modifier.fillMaxWidth()){Text("تاریخ: ${PersianDate.fromEpoch(date).label}")}
            }
            OrderSection("مشخصات تابلو") {
                OutlinedTextField(frameColor,{frameColor=it},label={Text("رنگ قاب")},singleLine=true,modifier=Modifier.fillMaxWidth())
                if(colors.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    colors.forEach{color->FilterChip(selected=frameColor==color,onClick={frameColor=color},label={Text(color)})}
                }
                OrderPhotoPreview(photoUri,if(removePhoto)"" else initial?.photoFileName.orEmpty())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text(if(photoUri!=null||(!removePhoto&&initial?.photoFileName?.isNotBlank()==true))"تغییر عکس ست" else "افزودن عکس ست")}
                    if(photoUri!=null||(!removePhoto&&initial?.photoFileName?.isNotBlank()==true)) TextButton(onClick={photoUri=null;removePhoto=true}){Text("حذف عکس",color=MaterialTheme.colorScheme.error)}
                }
            }
            OrderSection("اطلاعات مشتری"){
                OutlinedTextField(customer,{customer=it},label={Text("نام گیرنده *")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(instagram,{instagram=it},label={Text("آیدی اینستاگرام")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(phone,{phone=it},label={Text("شماره تماس")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(province,{province=it},label={Text("استان")},singleLine=true,modifier=Modifier.weight(1f))
                    OutlinedTextField(city,{city=it},label={Text("شهر")},singleLine=true,modifier=Modifier.weight(1f))
                }
                OutlinedTextField(address,{address=it},label={Text("جزئیات آدرس")},minLines=2,maxLines=3,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(postal,{postal=it.filter(Char::isDigit)},label={Text("کد پستی")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            }
            OrderSection("مبالغ و پرداخت"){
                MoneyField("هزینه ارسال واقعی",shipping){shipping=it;if(initial==null)quoteAuto=true}
                MoneyField("مبلغ توافق‌شده با مشتری",quote){quote=it;quoteAuto=false}
                MoneyField("بیعانه دریافت‌شده",deposit){deposit=it;depositAuto=false}
                MoneyField("سایر پرداخت‌های دریافت‌شده",otherPaid){otherPaid=it}
                MoneyField("مبلغ قابل پرداخت درب منزل",codDue){codDue=it;codAuto=false}
                MoneyField("دریافت‌شده درب منزل",codCollected){codCollected=it}
                Text("مبلغ درب منزل تا زمان ثبت دریافت، جزو دریافتی حساب نمی‌شود.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("دریافتی واقعی: ${money(paid?:0L)}  ·  مانده: ${money(balance)}",fontWeight=FontWeight.Bold)
                if(!valid && q!=null && paid!=null && customer.isNotBlank()) Text("مبلغ‌ها را بررسی کنید: دریافتی و مبلغ درب منزل نباید از مبلغ توافق‌شده بیشتر باشند.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            }
            OrderSection("یادداشت"){
                OutlinedTextField(note,{note=it},label={Text("توضیحات سفارش و ساخت")},minLines=3,maxLines=5,modifier=Modifier.fillMaxWidth())
            }
        }
        HorizontalDivider()
        Button(onClick={
            if(valid && !saving){
                saving=true
                val input=OrderFormInput(pid,date,customer.trim(),instagram.trim(),phone.trim(),province.trim(),city.trim(),address.trim(),postal.trim(),shippingValue!!,q!!,d!!,other!!,due!!,collected!!,frameColor.trim(),note.trim(),photoUri,removePhoto)
                scope.launch { try { if(onSave(input)) onDismiss() } finally { saving=false } }
            }
        },enabled=valid&&!saving,modifier=Modifier.fillMaxWidth().padding(16.dp)){
            Text(if(saving)"در حال ذخیره..." else if(initial==null)"ثبت سفارش" else "ذخیره ویرایش")
        }
    }
    if(picker){
        val state=rememberDatePickerState(initialSelectedDateMillis=date)
        DatePickerDialog(onDismissRequest={picker=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=it};picker=false}){Text("تأیید")}},dismissButton={TextButton(onClick={picker=false}){Text("انصراف")}}){DatePicker(state)}
    }
}

@Composable private fun OrderSection(title:String,content:@Composable ColumnScope.()->Unit){
    Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            content()
        }
    }
}

@Composable private fun MoneyField(label:String,value:String,onChange:(String)->Unit){
    OutlinedTextField(value,{onChange(it.filter(Char::isDigit))},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
}

@Composable private fun OrderPhotoPreview(selectedUri:String?,storedName:String){
    val context=LocalContext.current
    val key=selectedUri?:storedName
    var expanded by remember(key){mutableStateOf(false)}
    val bitmap by produceState<Bitmap?>(initialValue=null,key1=key){
        value=withContext(Dispatchers.IO){
            runCatching{
                val input=if(selectedUri!=null) context.contentResolver.openInputStream(Uri.parse(selectedUri))
                    else if(storedName.matches(Regex("[a-f0-9-]{36}\\.jpg"))) File(context.filesDir,"order_photos/$storedName").takeIf{it.isFile}?.inputStream()
                    else null
                input?.use{stream->BitmapFactory.decodeStream(stream,null,BitmapFactory.Options().apply{inSampleSize=4})}
            }.getOrNull()
        }
    }
    if(bitmap!=null){
        Card(modifier=Modifier.fillMaxWidth().height(180.dp).clickable{expanded=true}){
            Image(bitmap!!.asImageBitmap(),"عکس ست تابلو",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        }
        if(expanded) AlertDialog(onDismissRequest={expanded=false},title={Text("عکس ست تابلو")},text={Image(bitmap!!.asImageBitmap(),"عکس ست تابلو",Modifier.fillMaxWidth().heightIn(max=520.dp),contentScale=ContentScale.Fit)},confirmButton={TextButton(onClick={expanded=false}){Text("بستن")}})
    } else if(key.isNotBlank()) Text("پیش‌نمایش عکس در دسترس نیست؛ عکس را دوباره انتخاب کنید.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
}

@Composable fun OrderPhotoThumbnail(storedName:String){
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue=null,key1=storedName){
        value=withContext(Dispatchers.IO){
            if(!storedName.matches(Regex("[a-f0-9-]{36}\\.jpg"))) null
            else runCatching{BitmapFactory.decodeFile(File(context.filesDir,"order_photos/$storedName").absolutePath,BitmapFactory.Options().apply{inSampleSize=8})}.getOrNull()
        }
    }
    if(bitmap!=null) Image(bitmap!!.asImageBitmap(),"عکس ست تابلو",Modifier.fillMaxWidth().height(90.dp),contentScale=ContentScale.Crop)
}
