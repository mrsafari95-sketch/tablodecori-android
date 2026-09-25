package com.tablodecori.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.tablodecori.app.ui.screens.*
import com.tablodecori.app.ui.theme.TablodecoriTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app=application as TablodecoriApp
        setContent {
            val vm:MainViewModel=viewModel(factory=MainViewModelFactory(app.repository))
            val settings by vm.settings.collectAsState()
            TablodecoriTheme(darkMode = settings?.darkMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var splash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(650)
                        splash = false
                    }
                    if (splash) Splash() else MainShell(vm)
                }
            }
        }
    }
}

@Composable private fun Splash(){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Image(painterResource(R.drawable.tablodecori_logo),"لوگوی tablodecori",Modifier.size(132.dp).clip(RoundedCornerShape(34.dp)))
            Spacer(Modifier.height(16.dp))
            Text("tablodecori",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)
            Text("مدیریت هوشمند قیمت و سفارش",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class NavItem(val route:String,val icon:ImageVector,val label:String)
private val bottomItems=listOf(
    NavItem("home",Icons.Rounded.Home,"خانه"),
    NavItem("variables",Icons.Rounded.Inventory2,"متریال"),
    NavItem("products",Icons.Rounded.Widgets,"محصولات"),
    NavItem("orders",Icons.Rounded.LocalShipping,"سفارش‌ها"),
    NavItem("more",Icons.Rounded.GridView,"بیشتر")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MainShell(vm:MainViewModel){
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route?:"home"
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(Unit){vm.messages.collect{snackbar.showSnackbar(it)}}
    Scaffold(
        containerColor=MaterialTheme.colorScheme.background,
        snackbarHost={SnackbarHost(snackbar)},
        topBar={
            CenterAlignedTopAppBar(
                title={
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
                        Image(painterResource(R.drawable.tablodecori_logo),null,Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(9.dp))
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Text("tablodecori",fontWeight=FontWeight.ExtraBold)
                            Text(titleFor(route),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions={IconButton(onClick={nav.navigate("settings"){launchSingleTop=true}}){Icon(Icons.Rounded.Settings,"تنظیمات")}}
            )
        },
        floatingActionButton={
            if(route=="home") ExtendedFloatingActionButton(
                onClick={nav.navigate("quick"){launchSingleTop=true}},
                icon={Icon(Icons.Rounded.Calculate,null)},
                text={Text("محاسبه سریع")}
            )
        },
        bottomBar={
            NavigationBar(tonalElevation=2.dp){
                bottomItems.forEach{i->
                    NavigationBarItem(
                        selected=route==i.route,
                        onClick={
                            if(route!=i.route){
                                nav.navigate(i.route){
                                    popUpTo("home"){inclusive=false;saveState=false}
                                    launchSingleTop=true
                                    restoreState=false
                                }
                            }
                        },
                        icon={Icon(i.icon,i.label)},
                        label={Text(i.label,maxLines=1)}
                    )
                }
            }
        }
    ){pad->
        NavHost(nav,"home",Modifier.padding(pad)){
            composable("home"){HomeScreen(vm){target->nav.navigate(target){launchSingleTop=true}}}
            composable("variables"){VariablesScreen(vm)}
            composable("products"){ProductsScreen(vm)}
            composable("quick"){QuickScreen(vm)}
            composable("pricebook"){PricebookScreen(vm)}
            composable("orders"){OrdersScreen(vm)}
            composable("reports"){ReportsScreen(vm)}
            composable("settings"){SettingsScreen(vm)}
            composable("more"){MoreScreen{target->nav.navigate(target){launchSingleTop=true}}}
        }
    }
}

private fun titleFor(r:String)=when(r){
    "variables"->"متریال‌ها و هزینه‌ها"
    "products"->"محصولات و ست‌ها"
    "quick"->"محاسبه سریع"
    "pricebook"->"لیست قیمت"
    "orders"->"سفارش‌ها"
    "reports"->"گزارش‌ها"
    "settings"->"تنظیمات"
    "more"->"دسترسی‌ها"
    else->"مدیریت کارگاه"
}


private data class MoreAction(val route:String,val icon:ImageVector,val title:String,val subtitle:String)

@Composable
private fun MoreScreen(onNavigate:(String)->Unit){
    val actions=listOf(
        MoreAction("quick",Icons.Rounded.Calculate,"محاسبه سریع","قیمت‌گیری فوری بدون ذخیره"),
        MoreAction("pricebook",Icons.Rounded.ReceiptLong,"لیست قیمت","قیمت زنده محصولات"),
        MoreAction("reports",Icons.Rounded.BarChart,"گزارش‌ها","عملکرد و تغییرات قیمت"),
        MoreAction("settings",Icons.Rounded.Settings,"تنظیمات","ظاهر، قیمت‌گذاری و داده‌ها")
    )
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        item{
            Text("دسترسی‌های بیشتر",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("ابزارهای مدیریتی و تنظیمات برنامه",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(actions.size){index->
            val action=actions[index]
            Card(
                onClick={onNavigate(action.route)},
                shape=MaterialTheme.shapes.large,
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
            ){
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(shape=androidx.compose.foundation.shape.CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(48.dp)){
                        Box(contentAlignment=Alignment.Center){Icon(action.icon,null,tint=MaterialTheme.colorScheme.primary)}
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text(action.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text(action.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronLeft,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
