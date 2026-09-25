package com.tablodecori.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tablodecori.app.MainViewModel
import com.tablodecori.app.ui.SectionTitle
import com.tablodecori.app.util.money

@Composable fun HomeScreen(vm:MainViewModel,onNavigate:(String)->Unit){
    val products by vm.pricedProducts.collectAsState()
    val orders by vm.orders.collectAsState()
    val materials by vm.materials.collectAsState()
    val history by vm.history.collectAsState()
    val activeProducts=products.count{it.product.active}
    val activeMaterials=materials.count{it.enabled}
    val pieces=orders.sumOf{it.order.pieceCountSnapshot}
    val profit=orders.sumOf{maxOf(0,it.order.actualProfitToman)}
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=28.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)
    ){
        item{
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text("مدیریت کارگاه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("وضعیت امروز و دسترسی سریع به کارهای اصلی",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                DashboardKpi(Icons.Rounded.Widgets,"محصول فعال",activeProducts.toString(),Modifier.weight(1f))
                DashboardKpi(Icons.Rounded.LocalShipping,"سفارش",orders.size.toString(),Modifier.weight(1f))
            }
        }
        item{
            Surface(
                onClick={onNavigate.bind("quick")},
                shape=MaterialTheme.shapes.large,
                color=MaterialTheme.colorScheme.primaryContainer,
                contentColor=MaterialTheme.colorScheme.onPrimaryContainer,
                modifier=Modifier.fillMaxWidth()
            ){
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary,modifier=Modifier.size(52.dp)){
                        Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.Calculate,null,tint=MaterialTheme.colorScheme.onPrimary,modifier=Modifier.size(27.dp))}
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)){
                        Text("محاسبه سریع قیمت",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text("ابعاد و تعداد را وارد کن و همان لحظه قیمت بگیر",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronLeft,null,tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
        item{SectionTitle("دسترسی سریع","ابزارهای اصلی کارگاه")}
        val tiles=listOf(
            HomeTile("variables",Icons.Rounded.Inventory2,"متریال‌ها","قیمت و هزینه‌ها"),
            HomeTile("products",Icons.Rounded.Widgets,"محصولات","ست‌ها و قیمت زنده"),
            HomeTile("orders",Icons.Rounded.LocalShipping,"سفارش‌ها","ارسال، هزینه و سود"),
            HomeTile("pricebook",Icons.Rounded.ReceiptLong,"لیست قیمت","قیمت نهایی محصولات"),
            HomeTile("reports",Icons.Rounded.BarChart,"گزارش‌ها","عملکرد و تغییرات"),
            HomeTile("settings",Icons.Rounded.Settings,"تنظیمات","قیمت‌گذاری و داده‌ها")
        )
        items(tiles.chunked(2)){row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                row.forEach{tile->HomeActionCard(tile,Modifier.weight(1f)){onNavigate(tile.route)}}
                if(row.size==1)Spacer(Modifier.weight(1f))
            }
        }
        item{SectionTitle("نمای کلی","خلاصه وضعیت ثبت‌شده")}
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OverviewCard(Icons.Rounded.Tune,"متریال فعال",activeMaterials.toString(),Modifier.weight(1f))
                OverviewCard(Icons.Rounded.PhotoSizeSelectLarge,"تابلو ارسالی",pieces.toString(),Modifier.weight(1f))
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OverviewCard(Icons.Rounded.TrendingUp,"سود ثبت‌شده",money(profit),Modifier.weight(1f))
                OverviewCard(Icons.Rounded.History,"تغییر قیمت",history.size.toString(),Modifier.weight(1f))
            }
        }
    }
}

private fun ((String)->Unit).bind(route:String):()->Unit = { this(route) }
private data class HomeTile(val route:String,val icon:ImageVector,val title:String,val subtitle:String)

@Composable private fun DashboardKpi(icon:ImageVector,label:String,value:String,modifier:Modifier){
    Card(modifier,shape=MaterialTheme.shapes.large,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(38.dp)){
                Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))}
            }
            Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun HomeActionCard(tile:HomeTile,modifier:Modifier,onClick:()->Unit){
    Card(
        onClick=onClick,
        modifier=modifier.height(132.dp),
        shape=MaterialTheme.shapes.large,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ){
        Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(42.dp)){
                Box(contentAlignment=Alignment.Center){Icon(tile.icon,tile.title,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)}
            }
            Column{
                Text(tile.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                Text(tile.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
        }
    }
}

@Composable private fun OverviewCard(icon:ImageVector,label:String,value:String,modifier:Modifier){
    Card(modifier,shape=MaterialTheme.shapes.large,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f))){
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Icon(icon,label,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(21.dp))
            Text(value,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=1)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
