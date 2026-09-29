package com.tablodecori.app.ui.screens

import com.tablodecori.app.util.PersianDate
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class JalaliCalendarTest {
    @Test fun selectedPersianDayRoundTripsThroughStoredUtcDate() {
        val millis=JalaliCalendar.toUtcMillis(1405,7,6)
        assertNotNull(millis)
        val c=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{timeInMillis=millis!!}
        assertArrayEquals(intArrayOf(1405,7,6),PersianDate.gregorianToJalali(c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH)))
    }

    @Test fun invalidJalaliDatesAreRejected() {
        assertNull(JalaliCalendar.toUtcMillis(1405,7,31))
        assertEquals(31,JalaliCalendar.daysInMonth(1405,1))
        assertEquals(30,JalaliCalendar.daysInMonth(1405,7))
    }
}
