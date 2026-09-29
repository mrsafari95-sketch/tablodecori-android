package com.tablodecori.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablodecori.app.util.PersianDate
import com.tablodecori.app.util.fa
import java.util.Calendar
import java.util.TimeZone

/** Returns UTC midnight, matching the date representation already stored by the app. */
object JalaliCalendar {
    private val utc=TimeZone.getTimeZone("UTC")
    val months=listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    fun toUtcMillis(year:Int,month:Int,day:Int):Long? {
        if(year !in 1200..1600 || month !in 1..12 || day !in 1..31) return null
        val calendar=Calendar.getInstance(utc).apply { clear();set(year+621,Calendar.MARCH,1) }
        repeat(410){
            val parts=PersianDate.gregorianToJalali(calendar.get(Calendar.YEAR),calendar.get(Calendar.MONTH)+1,calendar.get(Calendar.DAY_OF_MONTH))
            if(parts[0]==year && parts[1]==month && parts[2]==day) return calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH,1)
        }
        return null
    }
    fun daysInMonth(year:Int,month:Int)=when(month){in 1..6->31;in 7..11->30;12->if(toUtcMillis(year,12,30)!=null)30 else 29;else->0}
    fun saturdayOffset(year:Int,month:Int):Int {
        val date=toUtcMillis(year,month,1)?:return 0
        return Calendar.getInstance(utc).apply{timeInMillis=date}.get(Calendar.DAY_OF_WEEK)%7
    }
}

@Composable fun PersianCalendarPicker(initialMillis:Long,onDismiss:()->Unit,onSelect:(Long)->Unit){
    val initial=PersianDate.fromEpoch(initialMillis.takeIf{it>0L}?:System.currentTimeMillis())
    var year by remember(initialMillis){mutableIntStateOf(initial.year)}
    var month by remember(initialMillis){mutableIntStateOf(initial.month)}
    var day by remember(initialMillis){mutableIntStateOf(initial.day)}
    fun stepMonth(delta:Int){
        val serial=year*12+(month-1)+delta
        year=Math.floorDiv(serial,12)
        month=Math.floorMod(serial,12)+1
        day=day.coerceAtMost(JalaliCalendar.daysInMonth(year,month))
    }
    val selected=JalaliCalendar.toUtcMillis(year,month,day)
    val days=JalaliCalendar.daysInMonth(year,month)
    val offset=JalaliCalendar.saturdayOffset(year,month)
    AlertDialog(onDismissRequest=onDismiss,title={Text("تقویم شمسی",fontWeight=FontWeight.Bold)},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={year--}){Text("سال قبل")}
            Text(fa(year),fontWeight=FontWeight.Bold)
            TextButton(onClick={year++}){Text("سال بعد")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={stepMonth(-1)}){Text("ماه قبل")}
            Text(JalaliCalendar.months[month-1],fontWeight=FontWeight.Bold)
            TextButton(onClick={stepMonth(1)}){Text("ماه بعد")}
        }
        Row(Modifier.fillMaxWidth()){listOf("ش","ی","د","س","چ","پ","ج").forEach{label->Box(Modifier.weight(1f).height(28.dp),contentAlignment=Alignment.Center){Text(label,style=MaterialTheme.typography.labelSmall)}}}
        repeat(6){week->Row(Modifier.fillMaxWidth()){
            repeat(7){weekday->
                val number=week*7+weekday-offset+1
                if(number in 1..days){
                    Box(Modifier.weight(1f).height(39.dp)
                        .background(if(number==day)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,CircleShape)
                        .clickable{day=number},contentAlignment=Alignment.Center){Text(fa(number),fontWeight=if(number==day)FontWeight.Bold else FontWeight.Normal)}
                }else Spacer(Modifier.weight(1f).height(39.dp))
            }
        }}
        TextButton(onClick={val today=PersianDate.fromEpoch(System.currentTimeMillis());year=today.year;month=today.month;day=today.day}){Text("امروز")}
    }},confirmButton={Button(onClick={selected?.let(onSelect)},enabled=selected!=null){Text("انتخاب تاریخ")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
