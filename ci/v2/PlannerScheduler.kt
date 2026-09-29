package com.tablodecori.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tablodecori.app.MainActivity
import com.tablodecori.app.TablodecoriApp
import com.tablodecori.app.data.db.PlannerSettingsEntity
import com.tablodecori.app.data.db.PlannerTaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

object PlannerScheduler {
    const val REMIND="com.tablodecori.app.PLANNER_REMIND"
    const val DONE="com.tablodecori.app.PLANNER_DONE"
    const val SKIP="com.tablodecori.app.PLANNER_SKIP"
    const val SNOOZE="com.tablodecori.app.PLANNER_SNOOZE"
    const val BRIEF="com.tablodecori.app.PLANNER_BRIEF"
    const val REVIEW="com.tablodecori.app.PLANNER_REVIEW"
    private const val NORMAL_CHANNEL="planner_reminders"
    private const val ALARM_CHANNEL="planner_alarms"
    private const val REVIEW_CHANNEL="planner_reviews"
    private const val REWARD_CHANNEL="planner_rewards"

    private fun manager(c:Context)=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun intent(c:Context,action:String,id:String,dayMillis:Long=0L,unique:String=""):Intent=Intent(c,PlannerAlarmReceiver::class.java).apply{
        this.action=action;data=Uri.parse("tablodecori://planner/$action/$id/$unique")
        putExtra("taskId",id);putExtra("dayMillis",dayMillis)
    }
    private fun pending(c:Context,action:String,id:String,dayMillis:Long=0L,unique:String="")=
        PendingIntent.getBroadcast(c,(action+id+unique).hashCode(),intent(c,action,id,dayMillis,unique),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun at(c:Context,time:Long,pi:PendingIntent){
        val alarms=manager(c)
        if(Build.VERSION.SDK_INT>=31 && alarms.canScheduleExactAlarms()){
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi) }
            catch (_:SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi) }
        } else if(Build.VERSION.SDK_INT>=23)alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi)
        else alarms.set(AlarmManager.RTC_WAKEUP,time,pi)
    }
    private fun quietAdjusted(time:Long,settings:PlannerSettingsEntity?):Long {
        val start=settings?.quietStartHour?:22;val end=settings?.quietEndHour?:7
        if(start==end)return time
        val c=Calendar.getInstance().apply{timeInMillis=time}
        val hour=c.get(Calendar.HOUR_OF_DAY)
        val quiet=if(start>end)hour>=start||hour<end else hour>=start&&hour<end
        if(!quiet)return time
        if(start>end && hour>=start)c.add(Calendar.DAY_OF_MONTH,1)
        c.set(Calendar.HOUR_OF_DAY,end);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0)
        return c.timeInMillis
    }
    fun schedule(c:Context,task:PlannerTaskEntity,settings:PlannerSettingsEntity?,fromMillis:Long=System.currentTimeMillis()){
        cancel(c,task.id)
        if(!task.active)return
        val due=PlannerEngine.next(task,fromMillis)?:return
        val trigger=quietAdjusted((due-task.reminderMinutes*60000L).coerceAtLeast(System.currentTimeMillis()+1000L),settings)
        at(c,trigger,pending(c,REMIND,task.id,due))
    }
    fun cancel(c:Context,id:String){manager(c).cancel(pending(c,REMIND,id))}
    fun snooze(c:Context,id:String,dayMillis:Long){at(c,System.currentTimeMillis()+600000L,pending(c,REMIND,id,dayMillis,"snooze-${System.currentTimeMillis()}"))}
    private fun daily(c:Context,action:String,hour:Int,enabled:Boolean){
        val pi=pending(c,action,"daily")
        manager(c).cancel(pi)
        if(!enabled)return
        val fireAt=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,hour);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);if(timeInMillis<=System.currentTimeMillis())add(Calendar.DAY_OF_MONTH,1)}.timeInMillis
        at(c,fireAt,pi)
    }
    fun refreshAll(c:Context,tasks:List<PlannerTaskEntity>,settings:PlannerSettingsEntity?){
        tasks.forEach{schedule(c,it,settings)}
        daily(c,BRIEF,settings?.morningBriefHour?:8,settings?.morningBriefEnabled?:false)
        daily(c,REVIEW,settings?.eveningReviewHour?:20,settings?.eveningReviewEnabled?:true)
    }
    private fun channels(c:Context){
        if(Build.VERSION.SDK_INT<26)return
        val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(NotificationChannel(NORMAL_CHANNEL,"یادآوری کارها",NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(ALARM_CHANNEL,"آلارم کارهای مهم",NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(REVIEW_CHANNEL,"مرور روزانه",NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(REWARD_CHANNEL,"پاداش و پیشرفت",NotificationManager.IMPORTANCE_LOW)).forEach(nm::createNotificationChannel)
    }
    private fun allowed(c:Context,settings:PlannerSettingsEntity?):Boolean {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
        val prefs=c.getSharedPreferences("planner_notify",Context.MODE_PRIVATE)
        val today=PlannerEngine.dateKey(System.currentTimeMillis())
        val count=if(prefs.getInt("day",0)==today)prefs.getInt("count",0) else 0
        if(count>=(settings?.dailyNotificationLimit?:8))return false
        prefs.edit().putInt("day",today).putInt("count",count+1).apply()
        return true
    }
    fun notifyTask(c:Context,task:PlannerTaskEntity,dayMillis:Long,settings:PlannerSettingsEntity?){
        if(!allowed(c,settings))return
        channels(c)
        val channel=if(task.alarm)ALARM_CHANNEL else NORMAL_CHANNEL
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(c,channel).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if(task.alarm)"⏰ کار مهم کارگاه" else "🌿 یادآوری کار")
            .setContentText(task.title).setContentIntent(open).setAutoCancel(true)
            .addAction(0,"✓ انجام شد",pending(c,DONE,task.id,dayMillis,"action-${PlannerEngine.dateKey(dayMillis)}"))
            .addAction(0,"⏰ ۱۰ دقیقه بعد",pending(c,SNOOZE,task.id,dayMillis,"action-${PlannerEngine.dateKey(dayMillis)}"))
            .addAction(0,"✗ انجام نشد",pending(c,SKIP,task.id,dayMillis,"action-${PlannerEngine.dateKey(dayMillis)}"))
            .build()
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(task.id.hashCode(),notification)
    }
    fun notifySummary(c:Context,title:String,body:String,settings:PlannerSettingsEntity?){
        if(!allowed(c,settings))return
        channels(c)
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice=NotificationCompat.Builder(c,REVIEW_CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(body).setContentIntent(open).setAutoCancel(true).build()
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(title.hashCode(),notice)
    }
    fun test(c:Context,settings:PlannerSettingsEntity?){notifySummary(c,"🔔 تست یادآوری پلنر","اگر این اعلان را می‌بینید، اجازهٔ اعلان فعال است.",settings)}
}

class PlannerAlarmReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val result=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{
            try{
                val app=context.applicationContext as TablodecoriApp
                val repo=app.repository
                val dao=app.database.plannerDao()
                val id=intent.getStringExtra("taskId").orEmpty()
                val due=intent.getLongExtra("dayMillis",0L)
                val settings=dao.settings()
                when(intent.action){
                    PlannerScheduler.REMIND->{
                        val task=dao.task(id)
                        if(task!=null && due>0L){
                            val status=dao.occurrence(id,PlannerEngine.dateKey(due))?.status
                            if(status !in setOf("DONE","SKIPPED","DEFERRED") && PlannerEngine.due(task,due))PlannerScheduler.notifyTask(context,task,due,settings)
                            PlannerScheduler.schedule(context,task,settings,due+1L)
                        }
                    }
                    PlannerScheduler.DONE,PlannerScheduler.SKIP->{
                        if(due>0L)repo.setPlannerStatus(id,due,if(intent.action==PlannerScheduler.DONE)"DONE" else "SKIPPED")
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.hashCode())
                    }
                    PlannerScheduler.SNOOZE->{
                        if(due>0L)PlannerScheduler.snooze(context,id,due)
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.hashCode())
                    }
                    PlannerScheduler.BRIEF,PlannerScheduler.REVIEW->{
                        val today=System.currentTimeMillis()
                        val tasks=dao.tasks().filter{PlannerEngine.due(it,today)}
                        val open=tasks.filter{dao.occurrence(it.id,PlannerEngine.dateKey(today))?.status !in setOf("DONE","SKIPPED","DEFERRED")}
                        if(open.isNotEmpty())PlannerScheduler.notifySummary(context,if(intent.action==PlannerScheduler.BRIEF)"☀️ امروز من" else "🌙 مرور شبانه","${open.size} کار باز داری؛ یکی‌یکی پیش می‌ریم 🌿",settings)
                        PlannerScheduler.refreshAll(context,dao.tasks(),settings)
                    }
                }
            }catch(_:Throwable){}finally{result.finish()}
        }
    }
}

class PlannerBootReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED))return
        val result=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{
            try{val dao=(context.applicationContext as TablodecoriApp).database.plannerDao();PlannerScheduler.refreshAll(context,dao.tasks(),dao.settings());PomodoroTimer.refresh(context,rearm=true)}catch(_:Throwable){}finally{result.finish()}
        }
    }
}
