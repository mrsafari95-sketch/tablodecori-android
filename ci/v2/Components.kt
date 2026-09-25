package com.tablodecori.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tablodecori.app.pricing.PieceInput
import com.tablodecori.app.pricing.PricingResult
import com.tablodecori.app.util.money

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
fun SoftIcon(icon: ImageVector, contentDescription: String? = null, modifier: Modifier = Modifier) {
    Surface(modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun CenteredSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun MoneyText(value: Long, modifier: Modifier = Modifier) {
    Text(money(value), modifier = modifier, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
fun PieceChips(pieces: List<PieceInput>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pieces.forEach {
            SuggestionChip(onClick = {}, label = { Text("${it.quantity} عدد ${it.widthCm}×${it.heightCm}") })
        }
    }
}

@Composable
fun PricingBreakdown(r: PricingResult) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        r.lines.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.name, style = MaterialTheme.typography.bodyMedium)
                Text(money(it.amountToman), fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("جمع هزینه بدون سود", fontWeight = FontWeight.Bold)
            Text(money(r.costBeforeProfitToman), fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("سود", fontWeight = FontWeight.Bold)
            Text(money(r.profitToman), fontWeight = FontWeight.Bold)
        }
        Surface(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("قیمت نهایی محصول", style = MaterialTheme.typography.bodySmall)
                MoneyText(r.finalPriceToman)
                Text("هزینه ارسال در این مبلغ محاسبه نشده است.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
