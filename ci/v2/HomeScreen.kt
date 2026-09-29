package com.tablodecori.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.data.outstandingToman
import com.tablodecori.app.data.PlannerEngine
import com.tablodecori.app.util.PersianDate
import com.tablodecori.app.util.money
import com.tablodecori.app.util.fa

private val HomeGold = Color(0xFFB78322)
private val ProductTint = Color(0xFFFFF6E8)
private val ProductAccent = Color(0xFF8B5A00)
private val MaterialTint = Color(0xFFEEF9F5)
private val QuickTint = Color(0xFFEEF6FF)
private val QuickAccent = Color(0xFF0E5D9A)
private val PriceTint = Color(0xFFF9F0FF)
private val PriceAccent = Color(0xFF6722B8)

@Composable
fun HomeScreen(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val products by vm.pricedProducts.collectAsState()
    val orders by vm.orders.collectAsState()
    val materials by vm.materials.collectAsState()
    val stock by vm.stockItems.collectAsState()
    val settings by vm.settings.collectAsState()
    val plannerTasks by vm.plannerTasks.collectAsState()
    val plannerOccurrences by vm.plannerOccurrences.collectAsState()
    val activeProducts = products.count { it.product.active }
    val today=PersianDate.fromEpoch(System.currentTimeMillis())
    fun dateKey(millis:Long):Int=PersianDate.fromEpoch(millis).let{it.year*10000+it.month*100+it.day}
    val todayKey=today.year*10000+today.month*100+today.day
    val pendingShip=orders.map{it.order}.filter{it.orderStatus!="SENT"&&it.plannedShipAtMillis>0L}.sortedBy{it.plannedShipAtMillis}
    val dueToday=pendingShip.count{dateKey(it.plannedShipAtMillis)==todayKey}
    val overdue=pendingShip.count{dateKey(it.plannedShipAtMillis)<todayKey}
    val receivables=orders.sumOf{it.order.outstandingToman()}
    val lowStock=stock.count{it.tracked && (it.onHandMicros<0L || (it.targetMicros>0L && it.onHandMicros*100L<=it.targetMicros*(settings?.lowStockPercent?:10)))}
    val todayTasks=plannerTasks.filter{PlannerEngine.due(it,System.currentTimeMillis())}
    val doneTasks=plannerOccurrences.count{it.dateKey==todayKey && it.status=="DONE" && todayTasks.any{task->task.id==it.taskId}}
    val activeOrders=orders.count{it.order.orderStatus=="PREPARING" || it.order.orderStatus=="READY"}

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HomeHero(
                activeProducts = activeProducts,
                orderCount = orders.size,
                onNavigate = onNavigate
            )
        }

        item { CenteredSectionTitle("دسترسی سریع", "کارهای روزمره کارگاه، یک‌جا و در دسترس") }
        item { HomeTodayPlannerCard(today.label, doneTasks, todayTasks.size) { onNavigate("planner") } }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeActionCard(
                        icon = Icons.Rounded.Inventory2,
                        title = "متریال‌ها",
                        subtitle = "قیمت، پرت و فرمول ساخت",
                        background = MaterialTint,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate("variables") }
                    HomeActionCard(
                        icon = Icons.Rounded.Widgets,
                        title = "ست‌های من",
                        subtitle = "ساخت و ویرایش",
                        background = ProductTint,
                        accent = ProductAccent,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate("products") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeActionCard(
                        icon = Icons.Rounded.BarChart,
                        title = "آمار و هزینه‌ها",
                        subtitle = "گزارش فروش و خرج",
                        background = PriceTint,
                        accent = PriceAccent,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate("reports") }
                    HomeActionCard(
                        icon = Icons.Rounded.Calculate,
                        title = "محاسبه سریع",
                        subtitle = "قیمت‌گیری بدون ذخیره",
                        background = QuickTint,
                        accent = QuickAccent,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate("quick") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeActionCard(Icons.Rounded.LocalShipping,"سفارش‌ها","ثبت، پیگیری و ارسال",MaterialTint,MaterialTheme.colorScheme.primary,Modifier.weight(1f)){onNavigate("orders")}
                    HomeActionCard(Icons.Rounded.Inventory,"انبار","موجودی و هشدار کمبود",PriceTint,PriceAccent,Modifier.weight(1f)){onNavigate("inventory")}
                }
            }
        }

        item { CenteredSectionTitle("کارهای امروز", "${today.label} · وضعیت سفارش‌های باز") }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OverviewCard(Icons.Rounded.NotificationsActive,"ارسال امروز",dueToday.toString(),MaterialTheme.colorScheme.primary,Modifier.weight(1f)){onNavigate("orders")}
                    OverviewCard(Icons.Rounded.WarningAmber,"ارسال عقب‌افتاده",overdue.toString(),Color(0xFFA65D13),Modifier.weight(1f)){onNavigate("orders")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OverviewCard(Icons.Rounded.DoneAll,"آمادهٔ ارسال",orders.count{it.order.orderStatus=="READY"}.toString(),Color(0xFF39816C),Modifier.weight(1f)){onNavigate("orders")}
                    OverviewCard(Icons.Rounded.AccountBalanceWallet,"ماندهٔ وصول",money(receivables),HomeGold,Modifier.weight(1f)){onNavigate("orders")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OverviewCard(Icons.Rounded.WarningAmber,"کمبود انبار",lowStock.toString(),Color(0xFFA65D13),Modifier.weight(1f)){onNavigate("inventory")}
                    OverviewCard(Icons.Rounded.Inventory,"اقلام انبار",stock.count{it.tracked}.toString(),MaterialTheme.colorScheme.primary,Modifier.weight(1f)){onNavigate("inventory")}
                }
            }
        }
        item { CenteredSectionTitle("نوبت‌های نزدیک ارسال", "سه سفارشی که زودتر باید بررسی شوند") }
        if(pendingShip.isEmpty()) item { ElevatedCard(Modifier.fillMaxWidth()){Text("فعلاً سفارشِ دارای نوبت ارسال باز ندارید.",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)} }
        else pendingShip.take(3).forEach{order->item(key="shipping-${order.id}"){
            ElevatedCard(onClick={onNavigate("orders")},modifier=Modifier.fillMaxWidth()){
                Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    Column{Text(order.customerName,fontWeight=FontWeight.Bold);Text(order.productNameSnapshot,style=MaterialTheme.typography.bodySmall)}
                    Text(PersianDate.fromEpoch(order.plannedShipAtMillis).label,color=MaterialTheme.colorScheme.primary)
                }
            }
        }}

        item { CenteredSectionTitle("نمای کلی کارگاه", "خلاصه‌ای از وضعیت فعلی و عملکرد") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewCard(Icons.Rounded.Tune, "متریال فعال", materials.count { it.enabled }.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { onNavigate("variables") }
                    OverviewCard(Icons.Rounded.PhotoSizeSelectLarge, "تابلو ارسالی", orders.filter { it.order.orderStatus == "SENT" }.sumOf { it.order.pieceCountSnapshot }.toString(), Color(0xFF4D9B87), Modifier.weight(1f)) { onNavigate("orders") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewCard(Icons.Rounded.TrendingUp, "سود/زیان سفارش‌ها", money(orders.sumOf { it.order.actualProfitToman }), HomeGold, Modifier.weight(1f)) { onNavigate("orders") }
                    OverviewCard(Icons.Rounded.PendingActions, "سفارش‌های در جریان", fa(activeOrders), QuickAccent, Modifier.weight(1f)) { onNavigate("orders") }
                }
            }
        }
    }
}

@Composable
private fun HomeTodayPlannerCard(dateLabel: String, doneCount: Int, taskCount: Int, onClick: () -> Unit) {
    val completed = doneCount.coerceIn(0, taskCount)
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.padding(11.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("برنامهٔ هدفمند امروز", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
            Text(dateLabel, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f), textAlign = TextAlign.Center)
            Text(
                if (taskCount == 0) "هنوز کاری برای امروز ثبت نشده؛ برنامه‌ات را بچین."
                else "${fa(completed)} از ${fa(taskCount)} کار امروز انجام شده است.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (taskCount > 0) {
                LinearProgressIndicator(
                    progress = { completed.toFloat() / taskCount },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(7.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .8f)
                )
            }
            Text(
                if (taskCount == 0) "ساخت برنامهٔ امروز ←" else "دیدن برنامه و یادآوری‌ها ←",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HomeHero(activeProducts: Int, orderCount: Int, onNavigate: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .68f)
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 24.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = .09f)
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "قیمت‌ها همیشه به‌روز،\nکارگاه همیشه مرتب",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "قیمت متریال را یک‌بار تنظیم کن؛\nقیمت همه محصولات مرتبط، لحظه‌به‌لحظه محاسبه می‌شود",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroKpi(Icons.Rounded.Inventory2, "محصول فعال", activeProducts.toString(), Modifier.weight(1f)) { onNavigate("products") }
                HeroKpi(Icons.Rounded.LocalShipping, "سفارش ثبت‌شده", orderCount.toString(), Modifier.weight(1f)) { onNavigate("orders") }
            }
        }
    }
}

@Composable
private fun HeroKpi(icon: ImageVector, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .09f)) {
                Icon(icon, label, Modifier.padding(9.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(5.dp))
            Text(value, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CenteredSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(34.dp).height(1.dp).background(HomeGold))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.width(34.dp).height(1.dp).background(HomeGold))
        }
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    background: Color,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(142.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = background),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = .09f)) {
                Icon(icon, title, Modifier.padding(10.dp).size(25.dp), tint = accent)
            }
            Spacer(Modifier.height(7.dp))
            Text(title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, textAlign = TextAlign.Center)
            Text(subtitle, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun OverviewCard(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .10f)) {
                Icon(icon, label, Modifier.padding(9.dp).size(22.dp), tint = accent)
            }
            Spacer(Modifier.height(5.dp))
            Text(value, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
