package com.tablodecori.app.data

import com.tablodecori.app.data.db.PlannerOccurrenceEntity
import com.tablodecori.app.data.db.PlannerTaskEntity
import com.tablodecori.app.util.PersianDate
import java.util.Calendar

/** One source for recurrence, daily scoring and progress. No rows are generated for unvisited days. */
object PlannerEngine {
    data class QuickAdd(val title:String,val timeMillis:Long,val recurrence:String,val weekDays:String)
    data class WeeklySummary(val days:List<Long>,val scores:List<Int?>,val completed:Int,val deferred:Int,val bestCompletionHour:Int?,val mostDeferredCategory:String?) {
        val average:Int? get()=scores.filterNotNull().takeIf{it.isNotEmpty()}?.average()?.toInt()
    }
    fun weeklySummary(tasks:List<PlannerTaskEntity>,occurrences:List<PlannerOccurrenceEntity>,restDays:Set<Int>,todayMillis:Long):WeeklySummary {
        val calendar=Calendar.getInstance().apply{timeInMillis=todayMillis;add(Calendar.DAY_OF_MONTH,-6)}
        val days=(0..6).map{val day=calendar.timeInMillis;calendar.add(Calendar.DAY_OF_MONTH,1);day}
        val keys=days.map(::dateKey).toSet()
        val logs=occurrences.filter{it.dateKey in keys}
        val done=logs.filter{it.status=="DONE"}
        val bestHour=done.filter{it.changedAt>0L}.groupingBy{Calendar.getInstance().apply{timeInMillis=it.changedAt}.get(Calendar.HOUR_OF_DAY)}.eachCount().maxByOrNull{it.value}?.key
        val byId=tasks.associateBy{it.id}
        val deferredCategory=logs.filter{it.status=="DEFERRED"}.mapNotNull{byId[it.taskId]?.category}.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key
        return WeeklySummary(days,days.map{score(tasks,occurrences,it,dateKey(it) in restDays)},done.size,logs.count{it.status=="DEFERRED"},bestHour,deferredCategory)
    }
    fun parseQuickAdd(text:String,now:Long):QuickAdd? {
        val normalized=text.map{ch->when(ch){in '۰'..'۹'->('0'.code+ch.code-'۰'.code).toChar();in '٠'..'٩'->('0'.code+ch.code-'٠'.code).toChar();else->ch}}.joinToString("")
        val clock=Regex("ساعت\\s*(\\d{1,2})(?::(\\d{1,2}))?\\s*(صبح|ظهر|عصر|شب)?").find(normalized)
        val shortClock=if(clock==null)Regex("(?<!\\d)(\\d{1,2})\\s*(صبح|ظهر|عصر|شب)").find(normalized) else null
        val dayNames=listOf("شنبه","یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه")
        val weekly=dayNames.indexOfFirst{normalized.contains("هر $it")}
        val relative=when {normalized.contains("پس‌فردا")||normalized.contains("پس فردا")->2;normalized.contains("فردا")->1;else->0}
        if(clock==null && shortClock==null && weekly<0 && relative==0)return null
        val calendar=Calendar.getInstance().apply{timeInMillis=now;add(Calendar.DAY_OF_MONTH,relative)}
        if(weekly>=0){val current=calendar.get(Calendar.DAY_OF_WEEK)%7;var add=Math.floorMod(weekly-current,7);if(add==0 && clock==null)add=7;calendar.add(Calendar.DAY_OF_MONTH,add)}
        var hour=clock?.groupValues?.get(1)?.toIntOrNull()?:shortClock?.groupValues?.get(1)?.toIntOrNull()?:calendar.get(Calendar.HOUR_OF_DAY)
        val minute=clock?.groupValues?.get(2)?.toIntOrNull()?:0
        if(hour !in 0..23 || minute !in 0..59)return null
        val part=clock?.groupValues?.get(3).orEmpty().ifBlank{shortClock?.groupValues?.get(2).orEmpty()}
        if(part in setOf("عصر","شب") && hour in 1..11)hour+=12
        if(part=="ظهر" && hour in 1..11)hour+=12
        calendar.set(Calendar.HOUR_OF_DAY,hour);calendar.set(Calendar.MINUTE,minute);calendar.set(Calendar.SECOND,0)
        if(weekly>=0 && calendar.timeInMillis<=now)calendar.add(Calendar.DAY_OF_MONTH,7)
        val withoutClock=(clock?:shortClock)?.let{normalized.removeRange(it.range)}?:normalized
        val title=withoutClock.replace("پس‌فردا","").replace("پس فردا","").replace("فردا","").replace("امروز","")
            .replace(Regex("هر\\s*(شنبه|یکشنبه|دوشنبه|سه‌شنبه|چهارشنبه|پنجشنبه|جمعه)"),"").trim().ifBlank{text.trim()}
        return QuickAdd(title,calendar.timeInMillis,if(weekly>=0)"WEEKLY" else "NONE",if(weekly>=0)weekly.toString() else "")
    }
    fun dateKey(millis:Long):Int=PersianDate.fromEpoch(millis).let{it.year*10000+it.month*100+it.day}
    fun due(task:PlannerTaskEntity,dayMillis:Long):Boolean {
        if(!task.active || dateKey(dayMillis)<dateKey(task.plannedAtMillis))return false
        return when(task.recurrence){
            "DAILY"->true
            "WEEKLY"->(Calendar.getInstance().apply{timeInMillis=dayMillis}.get(Calendar.DAY_OF_WEEK)%7).toString() in task.weekDays.split(',')
            "MONTHLY"->PersianDate.fromEpoch(dayMillis).day==PersianDate.fromEpoch(task.plannedAtMillis).day
            else->dateKey(dayMillis)==dateKey(task.plannedAtMillis)
        }
    }
    fun occurrenceTime(task:PlannerTaskEntity,dayMillis:Long):Long {
        val time=Calendar.getInstance().apply{timeInMillis=task.plannedAtMillis}
        val day=Calendar.getInstance().apply{timeInMillis=dayMillis}
        return Calendar.getInstance().apply{clear();set(day.get(Calendar.YEAR),day.get(Calendar.MONTH),day.get(Calendar.DAY_OF_MONTH),time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE));set(Calendar.SECOND,0)}.timeInMillis
    }
    fun next(task:PlannerTaskEntity,fromMillis:Long):Long? {
        val c=Calendar.getInstance().apply{timeInMillis=fromMillis;set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
        repeat(370){
            val day=c.timeInMillis
            if(due(task,day)){
                val at=occurrenceTime(task,day)
                if(at>=fromMillis)return at
            }
            c.add(Calendar.DAY_OF_MONTH,1)
        }
        return null
    }
    fun score(tasks:List<PlannerTaskEntity>,occurrences:List<PlannerOccurrenceEntity>,dayMillis:Long,rest:Boolean=false):Int? {
        if(rest)return null
        val logs=occurrences.filter{it.dateKey==dateKey(dayMillis)}.associateBy{it.taskId}
        val due=tasks.filter{due(it,dayMillis) && logs[it.id]?.reason!="LITE_DAY"}
        if(due.isEmpty())return null
        val available=due.sumOf{it.priority.coerceIn(1,3)}
        val earned=due.sumOf{t->when(logs[t.id]?.status){"DONE"->t.priority.coerceIn(1,3)*100;"DEFERRED"->t.priority.coerceIn(1,3)*25;else->0}}
        return (earned/available).coerceIn(0,100)
    }
    fun label(score:Int?):String=when {score==null->"🌿 روزی آرام، بدون امتیاز";score>=90->"🌟 عالی؛ به برنامه رسیدی";score>=70->"✅ خوب؛ مسیر را ادامه بده";score>=40->"🌱 چند قدم خوب برداشتی";else->"🌿 فردا با یک کار کوچک شروع کن"}
    fun xpForCompletion(priority:Int,onTime:Boolean,createdAfterDue:Boolean,lateDay:Boolean):Int {
        if(createdAfterDue)return 0
        val base=priority.coerceIn(1,3)*10+(if(onTime)5 else 0)
        return if(lateDay)base/2 else base
    }
    fun level(totalXp:Int):Pair<String,Int> {
        val names=listOf("نوآموز","شاگرد","استادکار","سرکارگر","مدیر حرفه‌ای","استاد تابلو")
        val index=(totalXp.coerceAtLeast(0)/250).coerceAtMost(names.lastIndex)
        return names[index] to index+1
    }
    fun streak(tasks:List<PlannerTaskEntity>,occurrences:List<PlannerOccurrenceEntity>,restDays:Set<Int>,todayMillis:Long):Int {
        val calendar=Calendar.getInstance().apply{timeInMillis=todayMillis;add(Calendar.DAY_OF_MONTH,-1)}
        val shields=mutableSetOf<String>()
        var success=0
        repeat(365){
            val day=calendar.timeInMillis
            val value=score(tasks,occurrences,day,dateKey(day) in restDays)
            if(value!=null){
                if(value>=70)success++
                else {
                    val week="${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.WEEK_OF_YEAR)}"
                    if(!shields.add(week))return success
                }
            }
            calendar.add(Calendar.DAY_OF_MONTH,-1)
        }
        return success
    }
}
