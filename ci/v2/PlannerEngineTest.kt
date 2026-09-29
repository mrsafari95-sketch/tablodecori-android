package com.tablodecori.app.data

import com.tablodecori.app.data.db.PlannerOccurrenceEntity
import com.tablodecori.app.data.db.PlannerTaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PlannerEngineTest {
    private fun at(day:Int,hour:Int=8)=Calendar.getInstance().apply{clear();set(2026,Calendar.SEPTEMBER,day,hour,0)}.timeInMillis
    private fun task(recurrence:String="NONE",priority:Int=2)=PlannerTaskEntity("t","ارسال تابلو",at(26),priority=priority,recurrence=recurrence,weekDays="0",createdAt=at(25),updatedAt=at(25))

    @Test fun dailyAndWeeklyRecurrenceStayLazy(){
        assertTrue(PlannerEngine.due(task("DAILY"),at(27)))
        assertFalse(PlannerEngine.due(task("NONE"),at(27)))
        assertTrue(PlannerEngine.due(task("WEEKLY"),at(26)))
        assertFalse(PlannerEngine.due(task("WEEKLY"),at(27)))
        assertEquals(PlannerEngine.dateKey(at(27)),PlannerEngine.dateKey(PlannerEngine.next(task("DAILY"),at(26,9))!!))
    }

    @Test fun scoreIsWeightedAndLiteDayDoesNotPenalize(){
        val important=task(priority=3)
        val small=task(priority=1).copy(id="small")
        val day=PlannerEngine.dateKey(at(26))
        val logs=listOf(PlannerOccurrenceEntity("t",day,"DONE"),PlannerOccurrenceEntity("small",day,"DEFERRED",reason="LITE_DAY"))
        assertEquals(100,PlannerEngine.score(listOf(important,small),logs,at(26)))
        assertNull(PlannerEngine.score(listOf(important),logs,at(26),rest=true))
        assertEquals(0,PlannerEngine.xpForCompletion(3,onTime=true,createdAfterDue=true,lateDay=false))
        assertTrue(PlannerEngine.xpForCompletion(3,onTime=true,createdAfterDue=false,lateDay=true)<PlannerEngine.xpForCompletion(3,onTime=true,createdAfterDue=false,lateDay=false))
    }
    @Test fun persianQuickAddParsesTomorrowAndWeekly(){
        val tomorrow=PlannerEngine.parseQuickAdd("فردا ساعت ۸ صبح ارسال تابلو",at(26))!!
        assertEquals("ارسال تابلو",tomorrow.title)
        assertEquals(PlannerEngine.dateKey(at(27)),PlannerEngine.dateKey(tomorrow.timeMillis))
        val weekly=PlannerEngine.parseQuickAdd("هر شنبه ۵ عصر پست اینستاگرام",at(26))
        assertNotNull(weekly)
        assertEquals("WEEKLY",weekly!!.recurrence)
        assertEquals("0",weekly.weekDays)
        assertEquals(17,Calendar.getInstance().apply{timeInMillis=weekly.timeMillis}.get(Calendar.HOUR_OF_DAY))
    }
    @Test fun weeklySummaryCountsOnlyTheRecentSevenDays(){
        val current=task()
        val completed=PlannerOccurrenceEntity("t",PlannerEngine.dateKey(at(26)),"DONE",changedAt=at(26,10))
        val old=PlannerOccurrenceEntity("t",PlannerEngine.dateKey(at(15)),"DONE",changedAt=at(15,10))
        val summary=PlannerEngine.weeklySummary(listOf(current),listOf(completed,old),emptySet(),at(27))
        assertEquals(7,summary.days.size)
        assertEquals(1,summary.completed)
        assertEquals(100,summary.average)
        assertEquals(10,summary.bestCompletionHour)
    }
}
