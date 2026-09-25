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
                shape=RoundedCornerShape(28.dp),
                color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.74f),
                contentColor=MaterialTheme.colorScheme.onSurface,
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.06f))
            ){
                Column(
                    Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(9.dp)
                ){
                    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary.copy(alpha=.07f),modifier=Modifier.size(50.dp)){
                        Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)}
                    }
                    Text("قیمت‌ها همیشه به‌روز،\nکارگاه همیشه مرتب",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    Text("قیمت متریال را یک‌بار تنظیم کن؛\nقیمت همه محصولات مرتبط، لحظه‌ای دوباره محاسبه می‌شود.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        HeroStat("سفارش ثبت‌شده",orders.size.toString(),Icons.Rounded.LocalShipping,Modifier.weight(1f))
                        HeroStat("محصول فعال",activeProducts.toString(),Icons.Rounded.Inventory2,Modifier.weight(1f))
                    }
                }
            }
        }

        item{HomeSectionHeader("دسترسی سریع","کارهای روزمره کارگاه، یکجا و در دسترس")}
        val tiles=listOf(
            HomeTile("products",Icons.Rounded.Widgets,"محصولات","ست‌ها و قیمت زنده",TileTone.Gold),
            HomeTile("variables",Icons.Rounded.Inventory2,"متریال‌ها","قیمت، پرت و فرمول ساخت",TileTone.Mint),
            HomeTile("quick",Icons.Rounded.Calculate,"محاسبه سریع","قیمت‌گیری بدون ذخیره",TileTone.Blue),
            HomeTile("pricebook",Icons.Rounded.ReceiptLong,"لیست قیمت","قیمت نهایی محصولات",TileTone.Lilac)
        )
        items(tiles.chunked(2)){row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                row.forEach{tile->HomeActionCard(tile,Modifier.weight(1f)){onNavigate(tile.route)}}
            }
        }

        item{HomeSectionHeader("نمای کلی کارگاه","خلاصه‌ای از وضعیت فعلی و عملکرد")}
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OverviewCard(Icons.Rounded.PhotoSizeSelectLarge,"تابلو ارسالی",pieces.toString(),OverviewTone.Mint,Modifier.weight(1f))
                OverviewCard(Icons.Rounded.Tune,"متریال فعال",activeMaterials.toString(),OverviewTone.Green,Modifier.weight(1f))
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OverviewCard(Icons.Rounded.History,"تغییر قیمت",history.size.toString(),OverviewTone.Lilac,Modifier.weight(1f))
                OverviewCard(Icons.Rounded.TrendingUp,"سود ثبت‌شده",money(profit),OverviewTone.Gold,Modifier.weight(1f))
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

@Composable private fun HeroStat(label:String,value:String,icon:ImageVector,modifier:Modifier){
    Surface(modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.96f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(38.dp)){
                Box(contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.primary)}
            }
            Spacer(Modifier.width(9.dp))
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
            }
        }
    }
}

private enum class TileTone{Mint,Gold,Blue,Lilac}
private data class HomeTile(val route:String,val icon:ImageVector,val title:String,val subtitle:String,val tone:TileTone)

@Composable private fun HomeActionCard(tile:HomeTile,modifier:Modifier,onClick:()->Unit){
    val bg=when(tile.tone){
        TileTone.Mint->MaterialTheme.colorScheme.primaryContainer.copy(alpha=.58f)
        TileTone.Gold->MaterialTheme.colorScheme.tertiaryContainer.copy(alpha=.52f)
        TileTone.Blue->MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.48f)
        TileTone.Lilac->MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.68f)
    }
    Card(onClick=onClick,modifier=modifier.height(104.dp),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=bg),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)){
        Box(Modifier.fillMaxSize().padding(12.dp)){
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.surface.copy(alpha=.64f),modifier=Modifier.size(28.dp).align(Alignment.TopStart)){
                Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.ChevronLeft,null,Modifier.size(17.dp),tint=MaterialTheme.colorScheme.primary)}
            }
            Column(Modifier.align(Alignment.CenterEnd),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.Center){
                Surface(shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.66f),modifier=Modifier.size(38.dp)){
                    Box(contentAlignment=Alignment.Center){Icon(tile.icon,tile.title,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)}
                }
                Spacer(Modifier.height(5.dp))
                Text(tile.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Right)
                Text(tile.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,textAlign=TextAlign.Right)
            }
        }
    }
}

private enum class OverviewTone{Mint,Green,Lilac,Gold}

@Composable private fun OverviewCard(icon:ImageVector,label:String,value:String,tone:OverviewTone,modifier:Modifier){
    val accent=when(tone){
        OverviewTone.Mint->MaterialTheme.colorScheme.primary
        OverviewTone.Green->MaterialTheme.colorScheme.primary
        OverviewTone.Lilac->MaterialTheme.colorScheme.secondary
        OverviewTone.Gold->MaterialTheme.colorScheme.tertiary
    }
    Card(modifier=modifier.height(76.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)){
        Row(Modifier.fillMaxSize().padding(horizontal=11.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Surface(shape=CircleShape,color=accent.copy(alpha=.10f),modifier=Modifier.size(38.dp)){
                Box(contentAlignment=Alignment.Center){Icon(icon,label,tint=accent,modifier=Modifier.size(21.dp))}
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Text(value,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=1,textAlign=TextAlign.Center)
                Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,maxLines=1)
            }
            MiniTrend(accent)
        }
    }
}

@Composable private fun MiniTrend(accent:androidx.compose.ui.graphics.Color){
    val bars=listOf(8,13,10,19)
    Row(Modifier.width(28.dp).height(24.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.Bottom){
        bars.forEach{h->Surface(modifier=Modifier.weight(1f).height(h.dp),shape=CircleShape,color=accent.copy(alpha=.62f)){}}
    }
}
