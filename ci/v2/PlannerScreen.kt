package com.tablodecori.app.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.data.PlannerEngine
import com.tablodecori.app.data.PlannerScheduler
import com.tablodecori.app.data.db.PlannerOccurrenceEntity
import com.tablodecori.app.data.db.PlannerSettingsEntity
import com.tablodecori.app.data.db.PlannerTaskEntity
import com.tablodecori.app.ui.AppCard
import com.tablodecori.app.util.PersianDate
import com.tablodecori.app.util.fa
import kotlinx.coroutines.delay
import java.util.Calendar

private val taskCategories=listOf("SHIPPING" to "ارسال سفارش","CONTENT" to "اینستاگرام و محتوا","CUSTOMER" to "پیگیری مشتری","PURCHASE" to "خرید و تأمین","FINANCE" to "مالی و قیمت","BACKUP" to "بکاپ و نگهداری","PERSONAL" to "شخصی")
private data class PlannerTemplate(val title:String,val category:String,val hour:Int,val recurrence:String="NONE",val weekday:Int=-1)
private val plannerTemplates=listOf(
    PlannerTemplate("ارسال تابلو", "SHIPPING", 9),
    PlannerTemplate("انتشار پست اینستاگرام", "CONTENT", 17),
    PlannerTemplate("پیگیری مشتری", "CUSTOMER", 11),
    PlannerTemplate("بازبینی قیمت متریال", "FINANCE", 10),
    PlannerTemplate("پشتیبان‌گیری از اطلاعات", "BACKUP", 18, "WEEKLY", 6)
)
private fun templateTask(template:PlannerTemplate,now:Long):PlannerTaskEntity {
    val calendar=Calendar.getInstance().apply{timeInMillis=now;set(Calendar.HOUR_OF_DAY,template.hour);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
    if(template.weekday>=0){
        while(calendar.get(Calendar.DAY_OF_WEEK)%7!=template.weekday || calendar.timeInMillis<=now)calendar.add(Calendar.DAY_OF_MONTH,1)
    }else if(calendar.timeInMillis<=now)calendar.add(Calendar.DAY_OF_MONTH,1)
    return PlannerTaskEntity("",template.title,calendar.timeInMillis,category=template.category,recurrence=template.recurrence,weekDays=if(template.weekday>=0)template.weekday.toString() else "",createdAt=now,updatedAt=now)
}
private fun categoryLabel(key:String)=taskCategories.firstOrNull{it.first==key}?.second?:"شخصی"
private fun statusLabel(key:String?)=when(key){"DONE"->"✓ انجام شد";"SKIPPED"->"✗ انجام نشد";"DEFERRED"->"⏰ موکول شد";else->"در انتظار"}
private fun stepDay(time:Long,delta:Int)=Calendar.getInstance().apply{timeInMillis=time;add(Calendar.DAY_OF_MONTH,delta)}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlannerScreen(vm:MainViewModel,onNavigate:(String)->Unit){
    val tasks by vm.plannerTasks.collectAsState()
    val occurrences by vm.plannerOccurrences.collectAsState()
    val xp by vm.plannerXp.collectAsState()
    val rewards by vm.plannerRewards.collectAsState()
    val restDays by vm.plannerRestDays.collectAsState()
    val settings by vm.plannerSettings.collectAsState()
    val orders by vm.orders.collectAsState()
    val stock by vm.stockItems.collectAsState()
    val materials by vm.materials.collectAsState()
    val context=LocalContext.current
    val nowMillis=System.currentTimeMillis()
    val lastBackup=context.getSharedPreferences("workshop_backup",Context.MODE_PRIVATE).getLong("last_export_at",0L)
    var page by rememberSaveable{mutableStateOf("TODAY")}
    var selectedDay by rememberSaveable{mutableLongStateOf(System.currentTimeMillis())}
    var showCreate by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<PlannerTaskEntity?>(null)}
    var statusFor by remember{mutableStateOf<PlannerTaskEntity?>(null)}
    var focusTask by remember{mutableStateOf<PlannerTaskEntity?>(null)}
    var focusEnd by remember{mutableLongStateOf(0L)}
    var focusRemaining by remember{mutableIntStateOf(0)}
    var month by rememberSaveable{val p=PersianDate.fromEpoch(System.currentTimeMillis());mutableIntStateOf(p.year*100+p.month)}
    val dayKey=PlannerEngine.dateKey(selectedDay)
    val due=tasks.filter{PlannerEngine.due(it,selectedDay)}.sortedWith(compareByDescending<PlannerTaskEntity>{it.priority}.thenBy{it.plannedAtMillis})
    val logs=occurrences.filter{it.dateKey==dayKey}.associateBy{it.taskId}
    val rest=restDays.any{it.dateKey==dayKey}
    val score=PlannerEngine.score(tasks,occurrences,selectedDay,rest)
    val completed=due.count{logs[it.id]?.status=="DONE"}
    val totalXp=xp.sumOf{it.amount}.coerceAtLeast(0)
    val spentXp=rewards.filter{it.redeemedAt>0}.sumOf{it.xpCost}
    val streak=PlannerEngine.streak(tasks,occurrences,restDays.map{it.dateKey}.toSet(),System.currentTimeMillis())
    val weekly=PlannerEngine.weeklySummary(tasks,occurrences,restDays.map{it.dateKey}.toSet(),nowMillis)
    LaunchedEffect(focusTask?.id,focusEnd){
        val target=focusTask?:return@LaunchedEffect
        while(focusEnd>0L){
            val left=((focusEnd-System.currentTimeMillis())/1000L).toInt().coerceAtLeast(0)
            focusRemaining=left
            if(left==0){vm.plannerFocus(target.id,selectedDay,25);focusTask=null;focusEnd=0L;break}
            delay(1000L)
        }
    }

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("برنامهٔ کارگاه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("TODAY" to "روزانه","MONTH" to "تقویم شمسی","REWARDS" to "پیشرفت و پاداش","SETTINGS" to "تنظیمات پلنر").forEach{(key,label)->FilterChip(page==key,onClick={page=key},label={Text(label)})}
        }}
        if(page=="TODAY"){
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={selectedDay=stepDay(selectedDay,-1)}){Text("روز قبل")}
                Text(PersianDate.fromEpoch(selectedDay).label,fontWeight=FontWeight.Bold)
                TextButton(onClick={selectedDay=stepDay(selectedDay,1)}){Text("روز بعد")}
            }}
            item{AppCard{
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    val color=MaterialTheme.colorScheme.primary
                    Box(Modifier.size(82.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){drawArc(color.copy(alpha=.15f),-90f,360f,false,style=androidx.compose.ui.graphics.drawscope.Stroke(width=10.dp.toPx()));drawArc(color,-90f,(score?:0)*3.6f,false,style=androidx.compose.ui.graphics.drawscope.Stroke(width=10.dp.toPx()))};Text("${fa(score?:0)}٪",fontWeight=FontWeight.Bold)}
                    Column{Text("${fa(completed)} از ${fa(due.size)} کار انجام شد",fontWeight=FontWeight.Bold);Text(PlannerEngine.label(score));Text("🔥 ${fa(streak)} روز موفق · ⭐ ${fa(totalXp)} امتیاز",style=MaterialTheme.typography.bodySmall)}
                }
                Text(if(rest)"امروز روز استراحت است؛ رشته پیشرفت حفظ می‌شود." else "یک کار کوچک هم قدم مهمی است 🌿",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
            item{AppCard{Text("۳ کار مهم این روز",fontWeight=FontWeight.Bold);due.filter{logs[it.id]?.status !in setOf("DONE","SKIPPED","DEFERRED")}.take(3).forEach{Text("• ${it.title}")};if(due.isEmpty())Text("برای این روز کاری نداری؛ یک کار تازه اضافه کن 🌿")}}
            if(due.size>6 || due.sumOf{it.durationMinutes}>360)item{AppCard{
                Text("برنامهٔ امروز شلوغ است",fontWeight=FontWeight.Bold)
                Text("${fa(due.size)} کار در برنامه داری. سه کار مهم را نگه دار و برای بقیه زمان تازه‌ای انتخاب کن.")
            }}
            item{Button(onClick={showCreate=true},modifier=Modifier.fillMaxWidth()){Text("+ افزودن کار سریع")}}
            item{AppCard{
                Text("کارهای آماده برای شروع",fontWeight=FontWeight.Bold)
                Text("یک الگو را انتخاب کن؛ ساعت و جزئیاتش را بعداً هم می‌توانی ویرایش کنی.",style=MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    plannerTemplates.forEach{template->
                        val exists=tasks.any{it.title==template.title && it.category==template.category && (it.recurrence!="NONE" || PlannerEngine.dateKey(it.plannedAtMillis)>=PlannerEngine.dateKey(nowMillis))}
                        OutlinedButton(onClick={vm.savePlannerTask(templateTask(template,System.currentTimeMillis()))},enabled=!exists){Text(template.title)}
                    }
                }
            }}
            focusTask?.let{task->item{AppCard{Text("زمان تمرکز: ${task.title}",fontWeight=FontWeight.Bold);Text("${fa(focusRemaining/60)}:${fa(focusRemaining%60).padStart(2,'۰')} باقی مانده");OutlinedButton(onClick={focusTask=null;focusEnd=0L}){Text("توقف تمرکز")}}}}
            if(due.size>3 && PlannerEngine.dateKey(System.currentTimeMillis())==dayKey)item{OutlinedButton(onClick={
                due.drop(3).filter{logs[it.id]?.status==null}.forEach{vm.plannerStatus(it.id,selectedDay,"DEFERRED","LITE_DAY")}
            },modifier=Modifier.fillMaxWidth()){Text("🌿 امروز سبک‌تر؛ کارهای دیگر به فردا منتقل شوند")}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("روز استراحت / مرخصی");Switch(rest,{vm.plannerRest(dayKey,it)})}}
            if(due.isEmpty())item{Text("کارها را با عنوان کوتاه و ساعت ثبت کن؛ باقی تنظیمات اختیاری‌اند.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(due.isNotEmpty())item{Text("خط زمان کارها",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)}
            val timeline=due.sortedBy{PlannerEngine.occurrenceTime(it,selectedDay)}
            val nextTask=if(dayKey==PlannerEngine.dateKey(nowMillis))timeline.firstOrNull{PlannerEngine.occurrenceTime(it,selectedDay)>=nowMillis}?.id else null
            items(timeline,key={it.id}){task->
                PlannerTaskCard(task,logs[task.id],selectedDay,dayKey,task.id==nextTask,vm,
                    onEdit={editing=task},onSkip={statusFor=task},onFocus={focusTask=task;focusEnd=System.currentTimeMillis()+25*60*1000L},onNavigate=onNavigate)
            }
            item{
                val low=stock.any{it.tracked && it.targetMicros>0L && it.onHandMicros*10<=it.targetMicros}
                val shipping=orders.any{it.order.orderStatus!="SENT"&&it.order.plannedShipAtMillis>0L&&PlannerEngine.dateKey(it.order.plannedShipAtMillis)==dayKey}
                val stalePrices=materials.any{it.enabled && !it.deleted && it.updatedAt>0L && nowMillis-it.updatedAt>21L*86400000L}
                val noRecentOrders=orders.isNotEmpty() && orders.none{nowMillis-it.order.dateEpochMillis in 0L..14L*86400000L}
                PlannerSuggestions(vm,tasks,due,occurrences,low,shipping,stalePrices,noRecentOrders,lastBackup,nowMillis)
            }
        }else if(page=="MONTH"){
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={val s=(month/100)*12+(month%100-1)-1;month=Math.floorDiv(s,12)*100+Math.floorMod(s,12)+1}){Text("ماه قبل")};Text("${JalaliCalendar.months[month%100-1]} ${fa(month/100)}",fontWeight=FontWeight.Bold);TextButton(onClick={val s=(month/100)*12+(month%100-1)+1;month=Math.floorDiv(s,12)*100+Math.floorMod(s,12)+1}){Text("ماه بعد")}}}
            item{AppCard{Text("رنگ هر روز میزان پیشرفت همان روز را نشان می‌دهد.",style=MaterialTheme.typography.bodySmall);PlannerMonthGrid(month,tasks,occurrences,restDays.map{it.dateKey}.toSet()){selectedDay=it;page="TODAY"}}}
            item{AppCard{
                Text("مرور ۷ روز اخیر",fontWeight=FontWeight.Bold)
                PlannerWeekChart(weekly)
                Text(weekly.average?.let{"میانگین پیشرفت: ${fa(it)}٪ · ${fa(weekly.completed)} کار انجام‌شده · ${fa(weekly.deferred)} کار موکول‌شده"}?:"هنوز کاری برای محاسبهٔ پیشرفت این هفته ثبت نشده است.")
                if(weekly.completed>=3)weekly.bestCompletionHour?.let{Text("💡 بیشتر کارهای ثبت‌شده را حوالی ساعت ${fa(it)} انجام داده‌ای. این ساعت شاید برای کارهای مهم مناسب باشد.")}
                if(weekly.deferred>=2)weekly.mostDeferredCategory?.let{Text("🌱 کارهای «${categoryLabel(it)}» بیشتر جابه‌جا شده‌اند؛ زمان یا اندازهٔ آن‌ها را بازبینی کن.")}
            }}
            item{AppCard{Text("جمع‌بندی ماه",fontWeight=FontWeight.Bold);val monthDays=(1..JalaliCalendar.daysInMonth(month/100,month%100)).mapNotNull{JalaliCalendar.toUtcMillis(month/100,month%100,it)};val values=monthDays.mapNotNull{PlannerEngine.score(tasks,occurrences,it,PlannerEngine.dateKey(it) in restDays.map{r->r.dateKey})};Text("میانگین امتیاز: ${fa(if(values.isEmpty())0 else values.average().toInt())}٪ · ${fa(values.count{it>=70})} روز خوب")}}
        }else if(page=="REWARDS"){
            item{AppCard{Text("پیشرفت کارگاه",fontWeight=FontWeight.Bold);Text("سطح ${fa(PlannerEngine.level(totalXp).second)} · ${PlannerEngine.level(totalXp).first}");Text("⭐ امتیاز کل: ${fa(totalXp)} · قابل استفاده: ${fa(totalXp-spentXp)}");Text("🔥 روزهای موفق پیاپی: ${fa(streak)} · هر هفته یک روز کم‌انرژی، این رشته را قطع نمی‌کند.")}}
            item{AppCard{Text("نشان‌ها",fontWeight=FontWeight.Bold);Text(if(streak>=7)"🏅 ۷ روز پیاپی" else "🏅 ۷ روز پیاپی · با ادامه مسیر باز می‌شود");Text(if(completed>=3)"🌟 سه کار یک روز" else "🌟 سه کار یک روز · هنوز فرصت داری")}}
            item{PlannerRewardEditor(vm)}
            items(rewards,key={it.id}){reward->AppCard{Text(reward.title,fontWeight=FontWeight.Bold);Text("${fa(reward.xpCost)} امتیاز");Button(onClick={vm.redeemPlannerReward(reward.id)},enabled=reward.redeemedAt==0L&&totalXp-spentXp>=reward.xpCost){Text(if(reward.redeemedAt>0L)"دریافت شده" else "دریافت پاداش")}}}
        }else{
            item{PlannerSettingsCard(vm,settings,context)}
        }
    }
    if(showCreate||editing!=null)PlannerTaskSheet(
        initial=editing,
        onDismiss={showCreate=false;editing=null},
        onSave={task->vm.savePlannerTask(task);showCreate=false;editing=null},
        onDelete={id->vm.deletePlannerTask(id);showCreate=false;editing=null}
    )
    statusFor?.let{task->var reason by remember{mutableStateOf("")};AlertDialog(onDismissRequest={statusFor=null},title={Text("دلیل انجام نشدن، اختیاری است")},text={Column{Row(Modifier.horizontalScroll(rememberScrollState())){listOf("وقت نشد","منتظر دیگران","فراموش کردم","اولویتش عوض شد").forEach{value->FilterChip(reason==value,onClick={reason=value},label={Text(value)})}};OutlinedTextField(reason,{reason=it},label={Text("دلیل")})}},confirmButton={Button(onClick={vm.plannerStatus(task.id,selectedDay,"SKIPPED",reason);statusFor=null}){Text("ثبت")}},dismissButton={TextButton(onClick={statusFor=null}){Text("انصراف")}})}
}

@Composable private fun PlannerTaskCard(
    task:PlannerTaskEntity,
    log:PlannerOccurrenceEntity?,
    selectedDay:Long,
    dayKey:Int,
    isNext:Boolean,
    vm:MainViewModel,
    onEdit:()->Unit,
    onSkip:()->Unit,
    onFocus:()->Unit,
    onNavigate:(String)->Unit,
){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        if(isNext)Text("● اکنون · کار بعدی در برنامه",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        AppCard{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Column(Modifier.weight(1f)){
                    Text(task.title,fontWeight=FontWeight.Bold)
                    val time=Calendar.getInstance().apply{timeInMillis=task.plannedAtMillis}
                    val priority=when(task.priority){3->"مهم";2->"متوسط";else->"سبک"}
                    Text("${categoryLabel(task.category)} · ${String.format("%02d:%02d",time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE))} · $priority",style=MaterialTheme.typography.bodySmall)
                }
                Text(statusLabel(log?.status),color=if(log?.status=="DONE")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(task.note.isNotBlank())Text(task.note,style=MaterialTheme.typography.bodySmall)
            if(task.checklist.isNotBlank())Text(task.checklist.lines().filter{it.isNotBlank()}.joinToString("\n"){"☐ $it"},style=MaterialTheme.typography.bodySmall)
            if((log?.focusMinutes?:0)>0)Text("تمرکز ثبت‌شده: ${fa(log!!.focusMinutes)} دقیقه",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                val canChange=dayKey<=PlannerEngine.dateKey(System.currentTimeMillis())
                TextButton(onClick={vm.plannerStatus(task.id,selectedDay,"DONE")},enabled=canChange&&log?.status!="DONE"){Text("✓ انجام شد")}
                TextButton(onClick={vm.plannerStatus(task.id,selectedDay,"DEFERRED","به فردا")},enabled=canChange&&log?.status!="DEFERRED"){Text("⏰ فردا")}
                TextButton(onClick=onSkip,enabled=canChange){Text("✗ انجام نشد")}
                TextButton(onClick=onEdit){Text("ویرایش")}
                if(dayKey==PlannerEngine.dateKey(System.currentTimeMillis()))TextButton(onClick=onFocus){Text("⏱️ تمرکز ۲۵ دقیقه")}
            }
            if(task.category in setOf("SHIPPING","FINANCE","BACKUP")){
                val target=when(task.category){"SHIPPING"->"orders";"FINANCE"->if(task.title.contains("قیمت")||task.title.contains("متریال"))"variables" else "reports";else->"settings"}
                val label=when(target){"orders"->"باز کردن سفارش‌ها ←";"variables"->"باز کردن متریال‌ها ←";"reports"->"باز کردن گزارش‌ها ←";else->"رفتن به پشتیبان‌گیری ←"}
                TextButton(onClick={onNavigate(target)}){Text(label)}
            }
        }
    }
}

@Composable private fun PlannerSuggestion(message:String,action:String,onClick:()->Unit){
    Column{Text(message,style=MaterialTheme.typography.bodySmall);TextButton(onClick=onClick){Text(action)}}
}

@Composable private fun PlannerSuggestions(
    vm:MainViewModel,
    tasks:List<PlannerTaskEntity>,
    due:List<PlannerTaskEntity>,
    occurrences:List<PlannerOccurrenceEntity>,
    low:Boolean,
    shipping:Boolean,
    stalePrices:Boolean,
    noRecentOrders:Boolean,
    lastBackup:Long,
    nowMillis:Long,
){
    AppCard{
        Text("پیشنهادهای کارگاه",fontWeight=FontWeight.Bold)
        val todayKey=PlannerEngine.dateKey(nowMillis)
        if(low && tasks.none{it.title in setOf("خرید اقلام با موجودی کم","خرید اقلام کم‌موجود") && PlannerEngine.dateKey(it.plannedAtMillis)>=todayKey})
            PlannerSuggestion("📦 چند قلم انبار به حد هشدار رسیده‌اند.","برنامه‌ریزی خرید"){
                vm.savePlannerTask(templateTask(PlannerTemplate("خرید اقلام با موجودی کم","PURCHASE",10),nowMillis))
            }
        if(shipping)Text("🚚 امروز نوبت ارسال سفارش داری. جزئیات را در بخش سفارش‌ها ببین.")
        if(stalePrices && tasks.none{it.title=="بازبینی قیمت متریال"})
            PlannerSuggestion("💰 قیمت بعضی متریال‌ها بیش از سه هفته بازبینی نشده است.","افزودن بازبینی"){
                vm.savePlannerTask(templateTask(plannerTemplates[3],nowMillis))
            }
        if((lastBackup==0L || nowMillis-lastBackup>7L*86400000L) && tasks.none{it.category=="BACKUP" && it.recurrence=="WEEKLY"})
            PlannerSuggestion(if(lastBackup==0L)"💾 هنوز زمان آخرین بکاپ در این گوشی ثبت نشده است." else "💾 از آخرین بکاپ ثبت‌شده بیش از یک هفته گذشته است.","یادآوری بکاپ"){
                vm.savePlannerTask(templateTask(plannerTemplates[4],nowMillis))
            }
        if(noRecentOrders && tasks.none{it.title=="انتشار محتوا برای جذب مشتری" && PlannerEngine.dateKey(it.plannedAtMillis)>=todayKey})
            PlannerSuggestion("📣 دو هفته است سفارش تازه‌ای در برنامه ثبت نشده است.","برنامه‌ریزی محتوا"){
                vm.savePlannerTask(templateTask(PlannerTemplate("انتشار محتوا برای جذب مشتری","CONTENT",17),nowMillis))
            }
        val repeatedlyDeferred=due.firstOrNull{task->occurrences.count{it.taskId==task.id && it.status=="DEFERRED"}>=2}
        if(repeatedlyDeferred!=null)Text("🌱 «${repeatedlyDeferred.title}» چند بار جابه‌جا شده است؛ شاید تقسیمش به دو کار کوچک‌تر کمک کند.")
        if(tasks.isEmpty() && !low && !shipping)Text("با یک کار کوچک مثل پیگیری مشتری شروع کن 🌿")
    }
}

@Composable private fun PlannerWeekChart(summary:PlannerEngine.WeeklySummary){
    val bar=MaterialTheme.colorScheme.primary
    val empty=MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(92.dp)){
        val cell=size.width/7f
        summary.scores.forEachIndexed{index,value->
            val height=if(value==null)4.dp.toPx() else (size.height*value.coerceIn(0,100)/100f).coerceAtLeast(4.dp.toPx())
            drawRect(if(value==null)empty else bar,topLeft=Offset(index*cell+cell*.2f,size.height-height),size=Size(cell*.6f,height))
        }
    }
    Row(Modifier.fillMaxWidth()){summary.days.forEach{day->val weekday=Calendar.getInstance().apply{timeInMillis=day}.get(Calendar.DAY_OF_WEEK)%7;Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(listOf("ش","ی","د","س","چ","پ","ج")[weekday],style=MaterialTheme.typography.labelSmall)}}}
}

@Composable private fun PlannerMonthGrid(month:Int,tasks:List<PlannerTaskEntity>,logs:List<PlannerOccurrenceEntity>,rest:Set<Int>,onDay:(Long)->Unit){
    Row(Modifier.fillMaxWidth()){listOf("ش","ی","د","س","چ","پ","ج").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it,style=MaterialTheme.typography.labelSmall)}}}
    val offset=JalaliCalendar.saturdayOffset(month/100,month%100)
    val days=JalaliCalendar.daysInMonth(month/100,month%100)
    repeat(6){week->Row(Modifier.fillMaxWidth()){repeat(7){i->val day=week*7+i-offset+1
        if(day in 1..days){val time=JalaliCalendar.toUtcMillis(month/100,month%100,day)?:0L;val score=PlannerEngine.score(tasks,logs,time,PlannerEngine.dateKey(time) in rest)
            val color=when{score==null->MaterialTheme.colorScheme.surfaceVariant;score>=90->Color(0xFF61B394);score>=70->Color(0xFFA4CCAB);score>=40->Color(0xFFE7CC8B);else->Color(0xFFE7A599)}
            Surface(Modifier.weight(1f).padding(2.dp).height(36.dp).clickable{onDay(time)},color=color,shape=MaterialTheme.shapes.small){Box(contentAlignment=Alignment.Center){Text(fa(day))}}
        }else Spacer(Modifier.weight(1f).height(36.dp))
    }}}
}

@Composable private fun PlannerRewardEditor(vm:MainViewModel){var title by remember{mutableStateOf("")};var cost by remember{mutableStateOf("100")};AppCard{Text("پاداش واقعی خودت را تعریف کن",fontWeight=FontWeight.Bold);OutlinedTextField(title,{title=it},label={Text("مثلاً یک قهوه خوب")},modifier=Modifier.fillMaxWidth());OutlinedTextField(cost,{cost=it.filter(Char::isDigit)},label={Text("امتیاز لازم")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());Button(onClick={vm.addPlannerReward(title,cost.toInt());title=""},enabled=title.isNotBlank()&&cost.toIntOrNull()?.let{it>0}==true){Text("افزودن پاداش")}}}

@Composable private fun PlannerSettingsCard(vm:MainViewModel,value:PlannerSettingsEntity?,context:Context){
    var quietStart by remember(value?.quietStartHour){mutableStateOf((value?.quietStartHour?:22).toString())}
    var quietEnd by remember(value?.quietEndHour){mutableStateOf((value?.quietEndHour?:7).toString())}
    var limit by remember(value?.dailyNotificationLimit){mutableStateOf((value?.dailyNotificationLimit?:8).toString())}
    var morning by remember(value?.morningBriefHour){mutableStateOf((value?.morningBriefHour?:8).toString())}
    var evening by remember(value?.eveningReviewHour){mutableStateOf((value?.eveningReviewHour?:20).toString())}
    var brief by remember(value?.morningBriefEnabled){mutableStateOf(value?.morningBriefEnabled?:false)}
    var review by remember(value?.eveningReviewEnabled){mutableStateOf(value?.eveningReviewEnabled?:true)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->vm.notify(if(granted)"اعلان‌های پلنر فعال شد." else "برای یادآوری، اجازه اعلان گوشی لازم است.")}
    val exact=if(Build.VERSION.SDK_INT>=31)(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
    AppCard{
        Text("یادآوری‌ها",fontWeight=FontWeight.Bold)
        Text("برای کارهای مهم، آلارم برجسته و برای سایر کارها، یادآوری معمولی نمایش داده می‌شود.",style=MaterialTheme.typography.bodySmall)
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)Button(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("اجازه اعلان")}
        if(!exact)OutlinedButton(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply{data=android.net.Uri.parse("package:${context.packageName}")})}}){Text("اجازه آلارم دقیق")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(quietStart,{quietStart=it.filter(Char::isDigit)},label={Text("سکوت از ساعت")},modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(quietEnd,{quietEnd=it.filter(Char::isDigit)},label={Text("تا ساعت")},modifier=Modifier.weight(1f),singleLine=true)}
        OutlinedTextField(limit,{limit=it.filter(Char::isDigit)},label={Text("حداکثر اعلان روزانه")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("خلاصه صبحگاهی");Switch(brief,{brief=it})}
        OutlinedTextField(morning,{morning=it.filter(Char::isDigit)},label={Text("ساعت خلاصه صبح")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("مرور شبانه");Switch(review,{review=it})}
        OutlinedTextField(evening,{evening=it.filter(Char::isDigit)},label={Text("ساعت مرور شب")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button(onClick={vm.savePlannerSettings(PlannerSettingsEntity(quietStartHour=quietStart.toInt(),quietEndHour=quietEnd.toInt(),dailyNotificationLimit=limit.toInt(),morningBriefHour=morning.toInt(),eveningReviewHour=evening.toInt(),morningBriefEnabled=brief,eveningReviewEnabled=review,updatedAt=System.currentTimeMillis()))},enabled=listOf(quietStart,quietEnd,morning,evening).all{it.toIntOrNull()?.let{n->n in 0..23}==true}&&limit.toIntOrNull()?.let{it in 1..20}==true,modifier=Modifier.fillMaxWidth()){Text("ذخیره تنظیمات پلنر")}
        OutlinedButton(onClick={PlannerScheduler.test(context,value)},modifier=Modifier.fillMaxWidth()){Text("تست یادآوری")}
        Text("در بعضی گوشی‌ها، برای دریافت یادآوری پس از بستن برنامه باید «شروع خودکار» و اجرای پس‌زمینه را فعال کنید. با دکمهٔ تست، دریافت اعلان را بررسی کنید.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PlannerTaskSheet(initial:PlannerTaskEntity?,onDismiss:()->Unit,onSave:(PlannerTaskEntity)->Unit,onDelete:(String)->Unit){
    val now=System.currentTimeMillis()
    val c=Calendar.getInstance().apply{timeInMillis=initial?.plannedAtMillis?:now}
    var title by remember(initial?.id){mutableStateOf(initial?.title.orEmpty())}
    var day by remember(initial?.id){mutableLongStateOf(initial?.plannedAtMillis?:now)}
    var hour by remember(initial?.id){mutableStateOf(c.get(Calendar.HOUR_OF_DAY).toString())}
    var minute by remember(initial?.id){mutableStateOf(c.get(Calendar.MINUTE).toString())}
    var category by remember(initial?.id){mutableStateOf(initial?.category?:"PERSONAL")}
    var priority by remember(initial?.id){mutableIntStateOf(initial?.priority?:2)}
    var recurrence by remember(initial?.id){mutableStateOf(initial?.recurrence?:"NONE")}
    var days by remember(initial?.id){mutableStateOf(initial?.weekDays.orEmpty())}
    var reminder by remember(initial?.id){mutableIntStateOf(initial?.reminderMinutes?:0)}
    var alarm by remember(initial?.id){mutableStateOf(initial?.alarm?:false)}
    var note by remember(initial?.id){mutableStateOf(initial?.note.orEmpty())}
    var checklist by remember(initial?.id){mutableStateOf(initial?.checklist.orEmpty())}
    var duration by remember(initial?.id){mutableStateOf((initial?.durationMinutes?:0).toString())}
    var advanced by remember{mutableStateOf(initial!=null)}
    var pickDate by remember{mutableStateOf(false)}
    var confirmDelete by remember(initial?.id){mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().heightIn(max=640.dp).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(if(initial==null)"کار جدید" else "ویرایش کار",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(title,{title=it},label={Text("عنوان کار")},placeholder={Text("مثلاً ارسال تابلو")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        PlannerEngine.parseQuickAdd(title,now)?.let{parsed->
            TextButton(onClick={title=parsed.title;day=parsed.timeMillis;val parsedTime=Calendar.getInstance().apply{timeInMillis=parsed.timeMillis};hour=parsedTime.get(Calendar.HOUR_OF_DAY).toString();minute=parsedTime.get(Calendar.MINUTE).toString();recurrence=parsed.recurrence;days=parsed.weekDays}){
                Text("✨ تشخیص زمان از متن: ${PersianDate.fromEpoch(parsed.timeMillis).label} · ${Calendar.getInstance().apply{timeInMillis=parsed.timeMillis}.get(Calendar.HOUR_OF_DAY)}:${Calendar.getInstance().apply{timeInMillis=parsed.timeMillis}.get(Calendar.MINUTE).toString().padStart(2,'0')}")
            }
        }
        TextButton(onClick={pickDate=true}){Text("📅 ${PersianDate.fromEpoch(day).label}")}
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("صبح" to 8,"ظهر" to 12,"عصر" to 17).forEach{(label,value)->FilterChip(hour==value.toString(),onClick={hour=value.toString();minute="0"},label={Text(label)})}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(hour,{hour=it.filter(Char::isDigit)},label={Text("ساعت")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(minute,{minute=it.filter(Char::isDigit)},label={Text("دقیقه")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f),singleLine=true)}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){taskCategories.forEach{(key,label)->FilterChip(category==key,onClick={category=key},label={Text(label)})}}
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("NONE" to "یک‌بار","DAILY" to "هر روز","WEEKLY" to "هفتگی","MONTHLY" to "ماهانه").forEach{(key,label)->FilterChip(recurrence==key,onClick={recurrence=key},label={Text(label)})}}
        if(recurrence=="WEEKLY")Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("ش","ی","د","س","چ","پ","ج").forEachIndexed{i,label->val selected=i.toString() in days.split(',');FilterChip(selected,onClick={days=if(selected)days.split(',').filter{it!=i.toString()}.joinToString(",") else (days.split(',').filter{it.isNotBlank()}+i.toString()).joinToString(",")},label={Text(label)})}}
        TextButton(onClick={advanced=!advanced}){Text(if(advanced)"گزینه‌های کمتر" else "گزینه‌های بیشتر")}
        if(advanced){
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf(1 to "سبک",2 to "متوسط",3 to "مهم").forEach{(key,label)->FilterChip(priority==key,onClick={priority=key},label={Text(label)})}}
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf(0 to "سر ساعت",10 to "۱۰ دقیقه قبل",30 to "۳۰ دقیقه قبل").forEach{(value,label)->FilterChip(reminder==value,onClick={reminder=value},label={Text(label)})}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("آلارم برجسته");Switch(alarm,{alarm=it})}
            OutlinedTextField(duration,{duration=it.filter(Char::isDigit)},label={Text("مدت تقریبی (دقیقه)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(checklist,{checklist=it},label={Text("زیرکارها؛ هر خط یک مورد")},modifier=Modifier.fillMaxWidth(),minLines=2)
            OutlinedTextField(note,{note=it},label={Text("یادداشت")},modifier=Modifier.fillMaxWidth())
        }
        val preview=if(hour.toIntOrNull()?.let{it in 0..23}==true && minute.toIntOrNull()?.let{it in 0..59}==true)"${PersianDate.fromEpoch(day).label} · ${hour.padStart(2,'0')}:${minute.padStart(2,'0')}" else "ساعت نامعتبر"
        Text("پیش‌نمایش: $preview",style=MaterialTheme.typography.bodySmall)
        Button(onClick={val date=Calendar.getInstance().apply{timeInMillis=day;set(Calendar.HOUR_OF_DAY,hour.toInt());set(Calendar.MINUTE,minute.toInt());set(Calendar.SECOND,0)}.timeInMillis;onSave(PlannerTaskEntity(initial?.id.orEmpty(),title,date,category,priority,duration.toIntOrNull()?:0,recurrence,days,note,checklist,reminder,alarm,true,initial?.createdAt?:now,now))},enabled=title.isNotBlank()&&hour.toIntOrNull()?.let{it in 0..23}==true&&minute.toIntOrNull()?.let{it in 0..59}==true&&(recurrence!="WEEKLY"||days.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text("ذخیره کار")}
        if(initial!=null)TextButton(onClick={confirmDelete=true}){Text("برداشتن کار از برنامه",color=MaterialTheme.colorScheme.error)}
    }}
    if(pickDate)PersianCalendarPicker(day,onDismiss={pickDate=false}){day=it;pickDate=false}
    if(confirmDelete && initial!=null)AlertDialog(
        onDismissRequest={confirmDelete=false},
        title={Text("برداشتن کار از برنامه")},
        text={Text("«${initial.title}» از برنامهٔ روزهای آینده برداشته می‌شود. سابقهٔ انجام و امتیازهای ثبت‌شده باقی می‌مانند.")},
        confirmButton={Button(onClick={onDelete(initial.id);confirmDelete=false}){Text("برداشتن کار")}},
        dismissButton={TextButton(onClick={confirmDelete=false}){Text("انصراف")}}
    )
}
