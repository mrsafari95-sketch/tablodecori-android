package com.tablodecori.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tablodecori.app.pricing.PieceInput
import com.tablodecori.app.pricing.PricingResult
import com.tablodecori.app.util.money

@Composable fun SectionTitle(title:String,subtitle:String?=null,action: (@Composable () -> Unit)? = null){
    Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
            if(subtitle!=null){Spacer(Modifier.height(2.dp));Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        if(action!=null){Spacer(Modifier.width(12.dp));action()}
    }
}

@Composable fun AppCard(modifier:Modifier=Modifier,content: @Composable ColumnScope.() -> Unit){
    ElevatedCard(
        modifier.fillMaxWidth(),
        shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.surface),
        elevation=CardDefaults.elevatedCardElevation(defaultElevation=1.dp)
    ){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)}
}

@Composable fun MoneyText(v:Long,modifier:Modifier=Modifier){Text(money(v),modifier,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium)}
@Composable fun PieceChips(pieces:List<PieceInput>){FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){pieces.forEach{SuggestionChip(onClick={},label={Text("${it.quantity} عدد ${it.widthCm}×${it.heightCm}")})}}}
@Composable fun PricingBreakdown(r:PricingResult){Column{r.lines.forEach{Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(it.name);Text(money(it.amountToman),fontWeight=FontWeight.SemiBold)}};HorizontalDivider();Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("جمع هزینه بدون سود",fontWeight=FontWeight.Bold);Text(money(r.costBeforeProfitToman),fontWeight=FontWeight.Bold)};Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("سود",fontWeight=FontWeight.Bold);Text(money(r.profitToman),fontWeight=FontWeight.Bold)};Surface(Modifier.fillMaxWidth().padding(top=10.dp),color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){Text("قیمت نهایی محصول",style=MaterialTheme.typography.bodySmall);MoneyText(r.finalPriceToman);Text("هزینه ارسال در این مبلغ محاسبه نشده است.",style=MaterialTheme.typography.bodySmall)}}}}
