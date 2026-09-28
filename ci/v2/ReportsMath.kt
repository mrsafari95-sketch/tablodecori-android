package com.tablodecori.app.data

import com.tablodecori.app.data.db.ExpenseEntity
import com.tablodecori.app.data.db.SentOrderEntity
import com.tablodecori.app.util.PersianDate

data class FinanceSnapshot(
    val sales:Long, val receipts:Long, val outstanding:Long, val orderMargin:Long,
    val materialPurchases:Long, val payroll:Long, val otherSpending:Long,
    val openingCash:Long, val carriedFromEarlier:Long, val closingCash:Long,
)

/** The cash ledger uses actual receipts and payments; the order margin is a separate cost snapshot. */
object ReportsMath {
    fun monthKey(time:Long):Int=PersianDate.fromEpoch(time).let{it.year*100+it.month}
    fun finance(orders:List<SentOrderEntity>,expenses:List<ExpenseEntity>,month:Int,openingCash:Long):FinanceSnapshot {
        val currentOrders=orders.filter{monthKey(it.dateEpochMillis)==month}
        val currentExpenses=expenses.filter{monthKey(it.dateEpochMillis)==month}
        val earlierReceipts=orders.filter{monthKey(it.dateEpochMillis)<month}.sumOf{it.receivedToman}
        val earlierSpending=expenses.filter{monthKey(it.dateEpochMillis)<month}.sumOf{it.amountToman}
        val carried=Math.subtractExact(Math.addExact(openingCash,earlierReceipts),earlierSpending)
        val receipts=currentOrders.sumOf{it.receivedToman}
        val spending=currentExpenses.sumOf{it.amountToman}
        return FinanceSnapshot(
            currentOrders.sumOf{it.quotedTotalToman},receipts,currentOrders.sumOf{it.outstandingToman()},
            currentOrders.sumOf{it.actualProfitToman},
            currentExpenses.filter{it.category=="MATERIAL"}.sumOf{it.amountToman},
            currentExpenses.filter{it.category=="PAYROLL"}.sumOf{it.amountToman},
            currentExpenses.filter{it.category=="OTHER"}.sumOf{it.amountToman},
            openingCash,carried,Math.subtractExact(Math.addExact(carried,receipts),spending)
        )
    }

    fun ranking(orders:List<SentOrderEntity>,selector:(SentOrderEntity)->String):List<Pair<String,Int>> =
        orders.groupingBy{selector(it).trim().ifBlank{"نامشخص"}}.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String,Int>>{it.value}.thenBy{it.key}).map{it.key to it.value}
}
