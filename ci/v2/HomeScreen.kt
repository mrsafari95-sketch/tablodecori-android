package com.tablodecori.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=24.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)
    ){
        item{
            Surface(
                modifier=Modifier.fillMaxWidth(),
                shape=MaterialTheme.shapes.extraLarge,
                color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.78f),
                contentColor=MaterialTheme.colorScheme.onSurface
            ){
                Column(
                    Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=20.dp),
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
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
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
            HomeTile("variables",Icons.Rounded.Inventory2,"متریال‌ها","قیمت، پرت و فرمول ساخت",TileTone.Mint),
            HomeTile("products",Icons.Rounded.Widgets,"محصولات","ست‌ها و قیمت زنده",TileTone.Gold),
            HomeTile("quick",Icons.Rounded.Calculate,"محاسبه سریع","قیمت‌گیری بدون ذخیره",TileTone.Blue),
            HomeTile("pricebook",Icons.Rounded.ReceiptLong,"لیست قیمت","قیمت نهایی محصولات",TileTone.Lilac)
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
    Surface(modifier,shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface.copy(alpha=.94f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(11.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
        }
    }
}

private enum class TileTone{Mint,Gold,Blue,Lilac}
private data class HomeTile(val route:String,val icon:ImageVector,val title:String,val subtitle:String,val tone:TileTone)

@Composable private fun HomeActionCard(tile:HomeTile,modifier:Modifier,onClick:()->Unit){
    val bg=when(tile.tone){
        TileTone.Mint->MaterialTheme.colorScheme.primaryContainer.copy(alpha=.62f)
        TileTone.Gold->MaterialTheme.colorScheme.tertiaryContainer.copy(alpha=.46f)
        TileTone.Blue->MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.48f)
        TileTone.Lilac->MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.72f)
    }
    Card(
        onClick=onClick,
        modifier=modifier.height(112.dp),
        shape=MaterialTheme.shapes.large,
        colors=CardDefaults.cardColors(containerColor=bg),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp)
    ){
        Box(Modifier.fillMaxSize().padding(12.dp)){
            Surface(
                shape=CircleShape,
                color=MaterialTheme.colorScheme.surface.copy(alpha=.72f),
                modifier=Modifier.size(30.dp).align(Alignment.TopStart)
            ){
                Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.ChevronLeft,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)}
            }
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center
            ){
                Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.68f),modifier=Modifier.size(42.dp)){
                    Box(contentAlignment=Alignment.Center){Icon(tile.icon,tile.title,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)}
                }
                Spacer(Modifier.height(6.dp))
                Text(tile.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
                Text(tile.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,textAlign=TextAlign.Center)
            }
        }
    }
}

@Composable private fun OverviewCard(icon:ImageVector,label:String,value:String,modifier:Modifier){
    Card(
        modifier=modifier.height(82.dp),
        shape=MaterialTheme.shapes.large,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp)
    ){
        Row(
            Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)
        ){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.78f),modifier=Modifier.size(42.dp)){
                Box(contentAlignment=Alignment.Center){Icon(icon,label,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))}
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Text(value,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,maxLines=1,textAlign=TextAlign.Center)
                Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,maxLines=1)
            }
            MiniTrend(icon)
        }
    }
}

@Composable private fun MiniTrend(icon:ImageVector){
    val bars=when(icon){
        Icons.Rounded.Tune -> listOf(7,12,17,24)
        Icons.Rounded.TrendingUp -> listOf(8,10,7,16)
        Icons.Rounded.History -> listOf(5,8,12,9)
        else -> listOf(5,7,9,12)
    }
    Row(
        Modifier.width(32.dp).height(28.dp),
        horizontalArrangement=Arrangement.spacedBy(2.dp),
        verticalAlignment=Alignment.Bottom
    ){
        bars.forEach{h->
            Surface(
                modifier=Modifier.weight(1f).height(h.dp),
                shape=CircleShape,
                color=MaterialTheme.colorScheme.primary.copy(alpha=.62f)
            ){}
        }
    }
}
