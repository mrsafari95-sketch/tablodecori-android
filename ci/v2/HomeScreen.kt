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
            Surface(
                modifier=Modifier.fillMaxWidth(),
                shape=MaterialTheme.shapes.extraLarge,
                color=MaterialTheme.colorScheme.primary,
                contentColor=MaterialTheme.colorScheme.onPrimary
            ){
                Column(
                    Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=22.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(10.dp)
                ){
                    Text("قیمت‌گذاری، بدون حساب‌وکتاب دوباره",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    Text(
                        "قیمت یک متریال را تغییر بده؛ هزینه ساخت محصولات مرتبط همان لحظه به‌روزرسانی می‌شود.",
                        style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.82f),
                        textAlign=TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        HeroStat("محصول فعال",activeProducts.toString(),Modifier.weight(1f))
                        HeroStat("سفارش ثبت‌شده",orders.size.toString(),Modifier.weight(1f))
                    }
                }
            }
        }
        item{SectionTitle("کارهای اصلی","همه چیز در چند لمس")}
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
        item{SectionTitle("خلاصه کارگاه","اطلاعات این نسخه روی همین دستگاه ذخیره می‌شود")}
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

@Composable private fun HeroStat(label:String,value:String,modifier:Modifier){
    Surface(modifier,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.09f)){
        Column(Modifier.padding(11.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimary)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.80f),textAlign=TextAlign.Center)
        }
    }
}

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
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center
        ){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(44.dp)){
                Box(contentAlignment=Alignment.Center){Icon(tile.icon,tile.title,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)}
            }
            Spacer(Modifier.height(9.dp))
            Text(tile.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
            Text(tile.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,textAlign=TextAlign.Center)
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
