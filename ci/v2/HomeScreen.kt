package com.tablodecori.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
                    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.72f),modifier=Modifier.size(54.dp)){
                        Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)}
                    }
                    Text("قیمت‌ها همیشه به‌روز،\nکارگاه همیشه مرتب",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    Text(
                        "قیمت متریال را یک‌بار تغییر بده؛\nقیمت همه محصولات مرتبط لحظه‌ای دوباره محاسبه می‌شود.",
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
        item{HomeSectionHeader("دسترسی سریع","کارهای روزمره کارگاه، یکجا و در دسترس")}
        val tiles=listOf(
            HomeTile("variables",Icons.Rounded.Inventory2,"متریال‌ها","قیمت، پرت و فرمول ساخت"),
            HomeTile("products",Icons.Rounded.Widgets,"محصولات","ست‌ها و قیمت زنده"),
            HomeTile("quick",Icons.Rounded.Calculate,"محاسبه سریع","قیمت‌گیری بدون ذخیره"),
            HomeTile("pricebook",Icons.Rounded.ReceiptLong,"لیست قیمت","قیمت نهایی محصولات")
        )
        items(tiles.chunked(2)){row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                row.forEach{tile->HomeActionCard(tile,Modifier.weight(1f)){onNavigate(tile.route)}}
                if(row.size==1)Spacer(Modifier.weight(1f))
            }
        }
        item{HomeSectionHeader("نمای کلی کارگاه","خلاصه‌ای از وضعیت فعلی")}
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

@Composable private fun HomeSectionHeader(title:String,subtitle:String){
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
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

@Composable private fun HomeActionCard(tile:HomeTile,modifier:Modifier,onClick:()->Unit){
    Card(
        onClick=onClick,
        modifier=modifier.height(118.dp),
        shape=MaterialTheme.shapes.large,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ){
        Column(
            Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=11.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center
        ){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(42.dp)){
                Box(contentAlignment=Alignment.Center){Icon(tile.icon,tile.title,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)}
            }
            Spacer(Modifier.height(6.dp))
            Text(tile.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
            Text(tile.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,textAlign=TextAlign.Center)
        }
    }
}

@Composable private fun OverviewCard(icon:ImageVector,label:String,value:String,modifier:Modifier){
    Card(
        modifier=modifier,
        shape=MaterialTheme.shapes.large,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f)),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ){
        Row(
            Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=13.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(11.dp)
        ){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(42.dp)){
                Box(contentAlignment=Alignment.Center){Icon(icon,label,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))}
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)){
                Text(value,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,maxLines=1,textAlign=TextAlign.Center)
                Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
            }
        }
    }
}
