package com.tablodecori.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=18.dp,bottom=24.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        item{
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.surface)),
                        RoundedCornerShape(30.dp)
                    )
                    .padding(horizontal=22.dp,vertical=26.dp)
            ){
                Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
                    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary.copy(alpha=.10f)){
                        Icon(Icons.Rounded.AutoAwesome,"",Modifier.padding(10.dp).size(24.dp),tint=MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("قیمت‌ها همیشه به‌روز، کارگاه همیشه مرتب",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("قیمت متریال را یک‌بار تغییر بده؛ قیمت محصولات مرتبط همان لحظه دوباره محاسبه می‌شود.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        HeroKpi("محصول فعال",activeProducts.toString(),Modifier.weight(1f))
                        HeroKpi("سفارش ثبت‌شده",orders.size.toString(),Modifier.weight(1f))
                    }
                }
            }
        }
        item{SectionTitle("دسترسی سریع","کارهای روزمره کارگاه، یک‌جا و در دسترس")}
        val tiles=listOf(
            HomeTile("variables",Icons.Rounded.Inventory2,"متریال‌ها","قیمت، پرت و فرمول ساخت"),
            HomeTile("products",Icons.Rounded.Widgets,"محصولات","ست‌ها و قیمت زنده"),
            HomeTile("quick",Icons.Rounded.Calculate,"محاسبه سریع","قیمت‌گیری بدون ذخیره"),
            HomeTile("pricebook",Icons.Rounded.ReceiptLong,"لیست قیمت","قیمت نهایی محصولات"),
            HomeTile("orders",Icons.Rounded.LocalShipping,"سفارش‌ها","ثبت ارسال، هزینه و سود"),
            HomeTile("reports",Icons.Rounded.BarChart,"گزارش‌ها","تغییرات قیمت و عملکرد")
        )
        items(tiles.chunked(2)){row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                row.forEach{tile->HomeActionCard(tile,Modifier.weight(1f)){onNavigate(tile.route)}}
                if(row.size==1)Spacer(Modifier.weight(1f))
            }
        }
        item{SectionTitle("نمای کلی کارگاه","خلاصه‌ای از وضعیت فعلی")}
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OverviewCard(Icons.Rounded.Tune,"متریال فعال",materials.count{it.enabled}.toString(),Modifier.weight(1f))
                OverviewCard(Icons.Rounded.PhotoSizeSelectLarge,"تابلو ارسالی",orders.sumOf{it.order.pieceCountSnapshot}.toString(),Modifier.weight(1f))
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OverviewCard(Icons.Rounded.TrendingUp,"سود ثبت‌شده",money(orders.sumOf{maxOf(0,it.order.actualProfitToman)}),Modifier.weight(1f))
                OverviewCard(Icons.Rounded.History,"تغییر قیمت",history.size.toString(),Modifier.weight(1f))
            }
        }
    }
}

private data class HomeTile(val route:String,val icon:ImageVector,val title:String,val subtitle:String)

@Composable private fun HeroKpi(label:String,value:String,modifier:Modifier){
    Surface(modifier,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.82f)){
        Column(Modifier.padding(vertical=12.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(value,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
        }
    }
}

@Composable private fun HomeActionCard(tile:HomeTile,modifier:Modifier,onClick:()->Unit){
    ElevatedCard(onClick=onClick,modifier=modifier.height(142.dp),shape=RoundedCornerShape(24.dp),elevation=CardDefaults.elevatedCardElevation(defaultElevation=1.dp)){
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween){
            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer){
                Icon(tile.icon,tile.title,Modifier.padding(9.dp).size(23.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Column{
                Text(tile.title,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium)
                Text(tile.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
            }
        }
    }
}

@Composable private fun OverviewCard(icon:ImageVector,label:String,value:String,modifier:Modifier){
    ElevatedCard(modifier,shape=RoundedCornerShape(22.dp),elevation=CardDefaults.elevatedCardElevation(defaultElevation=1.dp)){
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(icon,label,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
            Text(value,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
