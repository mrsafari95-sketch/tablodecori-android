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
import com.tablodecori.app.util.money

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
    val history by vm.history.collectAsState()
    val activeProducts = products.count { it.product.active }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HomeHero(
                activeProducts = activeProducts,
                orderCount = orders.size
            )
        }

        item { CenteredSectionTitle("دسترسی سریع", "کارهای روزمره کارگاه، یک‌جا و در دسترس") }

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
                        title = "محصولات",
                        subtitle = "ست‌ها و قیمت زنده",
                        background = ProductTint,
                        accent = ProductAccent,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate("products") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeActionCard(
                        icon = Icons.Rounded.ReceiptLong,
                        title = "لیست قیمت",
                        subtitle = "قیمت نهایی محصولات",
                        background = PriceTint,
                        accent = PriceAccent,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate("pricebook") }
                    HomeActionCard(
                        icon = Icons.Rounded.Calculate,
                        title = "محاسبه سریع",
                        subtitle = "قیمت‌گیری بدون ذخیره",
                        background = QuickTint,
                        accent = QuickAccent,
                        modifier = Modifier.weight(1f)
                    ) { onNavigate("quick") }
                }
            }
        }

        item { CenteredSectionTitle("نمای کلی کارگاه", "خلاصه‌ای از وضعیت فعلی و عملکرد") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewCard(
                        Icons.Rounded.Tune,
                        "متریال فعال",
                        materials.count { it.enabled }.toString(),
                        MaterialTheme.colorScheme.primary,
                        Modifier.weight(1f)
                    )
                    OverviewCard(
                        Icons.Rounded.PhotoSizeSelectLarge,
                        "تابلو ارسالی",
                        orders.sumOf { it.order.pieceCountSnapshot }.toString(),
                        Color(0xFF4D9B87),
                        Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewCard(
                        Icons.Rounded.TrendingUp,
                        "سود ثبت‌شده",
                        money(orders.sumOf { maxOf(0, it.order.actualProfitToman) }),
                        HomeGold,
                        Modifier.weight(1f)
                    )
                    OverviewCard(
                        Icons.Rounded.History,
                        "تغییر قیمت",
                        history.size.toString(),
                        QuickAccent,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHero(activeProducts: Int, orderCount: Int, onNavigate: (String) -> Unit = {}) {
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
private fun HeroKpi(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .09f)) {
                Icon(icon, null, Modifier.padding(9.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
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
        modifier = modifier.height(116.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = background),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent.copy(alpha = .09f)) {
                    Icon(Icons.Rounded.ChevronLeft, null, Modifier.padding(8.dp).size(19.dp), tint = accent)
                }
                Surface(shape = RoundedCornerShape(15.dp), color = accent.copy(alpha = .09f)) {
                    Icon(icon, title, Modifier.padding(9.dp).size(24.dp), tint = accent)
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun OverviewCard(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier) {
    ElevatedCard(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .10f)) {
                Icon(icon, label, Modifier.padding(10.dp).size(23.dp), tint = accent)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}
