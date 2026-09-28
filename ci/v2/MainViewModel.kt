package com.tablodecori.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tablodecori.app.data.*
import com.tablodecori.app.data.db.*
import com.tablodecori.app.pricing.PieceInput
import com.tablodecori.app.pricing.PricingResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QuickDraft(
    val pieces: List<PieceInput> = listOf(PieceInput(40,60,1)),
    val enabledIds: Map<String,Boolean> = emptyMap(),
    val selectedPackage: String = "",
    val packageOpen: Boolean = false,
    val manualProfitToman: Long = 0L,
    val productType: String = "STANDARD",
    val designMaterialsCostToman: Long = 0L,
)

class MainViewModel(private val repo: WorkshopRepository) : ViewModel() {
    private val _quickDraft = MutableStateFlow(QuickDraft())
    val quickDraft = _quickDraft.asStateFlow()
    fun initializeQuickIds(defaults: Map<String,Boolean>) {
        _quickDraft.update { d -> d.copy(enabledIds = defaults + d.enabledIds) }
    }
    fun updateQuickPiece(index:Int,piece:PieceInput) { _quickDraft.update { d -> d.copy(pieces=d.pieces.toMutableList().also{if(index in it.indices)it[index]=piece}) } }
    fun addQuickPiece() { _quickDraft.update { d -> d.copy(pieces=d.pieces + PieceInput(20,30,1)) } }
    fun removeQuickPiece(index:Int) { _quickDraft.update { d -> if(d.pieces.size<=1)d else d.copy(pieces=d.pieces.filterIndexed{i,_->i!=index}) } }
    fun setQuickId(id:String,on:Boolean) { _quickDraft.update { d -> d.copy(enabledIds=d.enabledIds + (id to on)) } }
    fun setQuickPackage(key:String) { _quickDraft.update { d -> d.copy(selectedPackage=key,packageOpen=false) } }
    fun toggleQuickPackage() { _quickDraft.update { d -> d.copy(packageOpen=!d.packageOpen) } }
    fun setQuickProfit(amount:Long) { _quickDraft.update { d -> d.copy(manualProfitToman=amount) } }
    fun setQuickProductType(type:String) { _quickDraft.update { d -> d.copy(productType=type) } }
    fun setQuickDesignCost(amount:Long) { _quickDraft.update { d -> d.copy(designMaterialsCostToman=amount) } }
    val materials = repo.materials.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val products = repo.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pricedProducts = repo.pricedProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val orders = repo.orders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val profitRules = repo.profitRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val stockItems = repo.stockItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val expenses = repo.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val plannerTasks=repo.plannerTasks.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val plannerOccurrences=repo.plannerOccurrences.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val plannerSettings=repo.plannerSettings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val plannerRewards=repo.plannerRewards.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val plannerXp=repo.plannerXp.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val plannerRestDays=repo.plannerRestDays.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _message=MutableSharedFlow<String>(extraBufferCapacity=8); val messages=_message.asSharedFlow()
    init { viewModelScope.launch { repo.ensurePricingStructure() } }

    fun saveMaterial(e: MaterialEntity)=launch { val n=repo.saveMaterial(e); _message.emit(if(n>0) "قیمت تغییر کرد؛ $n محصول تحت تأثیر قرار گرفت." else "متغیر ذخیره شد.") }
    suspend fun createReliefMaterial(name:String,price:Long,type:String):String? = runCatching { repo.createReliefMaterial(name,price,type) }
        .fold(onSuccess={_message.emit("متغیر تابلو برجسته افزوده شد.");it},onFailure={_message.emit(it.message?:"افزودن متغیر انجام نشد.");null})
    suspend fun createStockMaterial(name:String,unit:String,onHand:Long,target:Long):String? = runCatching { repo.createStockMaterial(name,unit,onHand,target) }
        .fold(onSuccess={_message.emit("قلم سفارشی به پایش انبار افزوده شد.");it},onFailure={_message.emit(it.message?:"افزودن قلم انجام نشد.");null})
    fun sizePrices(id:String)=repo.sizePrices(id)
    fun saveSizePrice(e:SizePriceEntity)=launch { repo.saveSizePrice(e); _message.emit("قیمت ابعاد ذخیره شد.") }
    fun deleteSizePrice(id:String)=launch { repo.deleteSizePrice(id); _message.emit("قیمت ابعاد حذف شد.") }
    fun toggleMaterial(id:String,on:Boolean)=launch { repo.toggleMaterial(id,on) }
    fun deleteMaterial(id:String)=launch { val n=repo.deleteMaterial(id); _message.emit(if(n>0) "متغیر غیرفعال شد؛ در $n محصول استفاده شده بود." else "متغیر حذف شد.") }
    fun saveProduct(id:String?,name:String,pieces:List<PieceInput>,ids:Set<String>,active:Boolean=true,manualProfit:Long=0L,profitMode:String="MANUAL",profitFormula:String="",packagingSizeKey:String="",productType:String="STANDARD",designMaterialsCostToman:Long=0L)=launch { repo.saveProduct(id,name,pieces,ids,active,manualProfit,profitMode,profitFormula,packagingSizeKey,productType,designMaterialsCostToman); _message.emit("محصول ذخیره شد.") }
    fun duplicateProduct(id:String)=launch { repo.duplicateProduct(id); _message.emit("یک کپی از محصول ساخته شد.") }
    fun toggleProduct(id:String,on:Boolean)=launch { repo.toggleProduct(id,on) }
    fun deleteProduct(id:String)=launch { repo.deleteProduct(id); _message.emit("محصول حذف شد.") }
    suspend fun saveOrder(input:OrderFormInput):Boolean = runCatching { repo.createOrder(input) }
        .fold(onSuccess={ _message.emit("سفارش ارسالی ثبت شد."); true },onFailure={ _message.emit(it.message?:"ثبت سفارش ناموفق بود."); false })
    suspend fun updateOrder(orderId:String,input:OrderFormInput):Boolean = runCatching { repo.updateOrder(orderId,input) }
        .fold(onSuccess={ _message.emit("سفارش ویرایش شد."); true },onFailure={ _message.emit(it.message?:"ویرایش سفارش ناموفق بود."); false })
    fun deleteOrder(orderId:String)=launch {
        repo.deleteOrder(orderId); _message.emit("سفارش حذف شد.")
    }
    fun saveSettings(rounding:Long,shipping:Long,dark:Boolean)=launch { repo.updateSettings(rounding,shipping,dark); _message.emit("تنظیمات ذخیره شد.") }
    fun addStock(materialId:String,variant:String,onHand:Long,target:Long)=launch { repo.addStock(materialId,variant,onHand,target);_message.emit("قلم انبار ثبت شد.") }
    fun saveStock(id:String,onHand:Long,target:Long)=launch { repo.saveStock(id,onHand,target);_message.emit("موجودی انبار ذخیره شد.") }
    fun trackStock(materialId:String,variant:String)=launch { repo.trackStock(materialId,variant);_message.emit("قلم به پایش انبار اضافه شد؛ موجودی واقعی آن را ثبت کنید.") }
    fun hideStock(id:String)=launch { repo.hideStock(id);_message.emit("قلم از پایش انبار برداشته شد.") }
    fun dismissStockSuggestion(materialId:String,variant:String)=launch { repo.dismissStockSuggestion(materialId,variant);_message.emit("قلم از پیشنهادهای انبار پنهان شد.") }
    fun saveExpense(id:String?,dateMillis:Long,category:String,title:String,payeeName:String,amount:Long,note:String,receiptUri:String?)=launch { repo.saveExpense(id,dateMillis,category,title,payeeName,amount,note,receiptUri);_message.emit("هزینه ثبت شد.") }
    fun deleteExpense(id:String)=launch { repo.deleteExpense(id);_message.emit("هزینه حذف شد.") }
    fun saveOpeningCash(amount:Long)=launch { repo.updateOpeningCash(amount);_message.emit("موجودی آغازین ذخیره شد.") }
    fun savePlannerTask(task:PlannerTaskEntity)=launch { repo.savePlannerTask(task);_message.emit("کار ذخیره شد.") }
    fun deletePlannerTask(id:String)=launch { repo.deletePlannerTask(id);_message.emit("کار از برنامه برداشته شد.") }
    fun plannerStatus(id:String,dayMillis:Long,status:String,reason:String="")=launch { repo.setPlannerStatus(id,dayMillis,status,reason);_message.emit("وضعیت کار ثبت شد.") }
    fun plannerFocus(id:String,dayMillis:Long,minutes:Int)=launch { repo.addPlannerFocus(id,dayMillis,minutes);_message.emit("زمان تمرکز ثبت شد.") }
    fun plannerRest(day:Int,rest:Boolean)=launch { repo.setPlannerRestDay(day,rest) }
    fun savePlannerSettings(value:PlannerSettingsEntity)=launch { repo.savePlannerSettings(value);_message.emit("تنظیمات پلنر ذخیره شد.") }
    fun addPlannerReward(title:String,cost:Int)=launch { repo.addPlannerReward(title,cost);_message.emit("پاداش افزوده شد.") }
    fun redeemPlannerReward(id:String)=launch { repo.redeemPlannerReward(id);_message.emit("پاداش دریافت شد 🌿") }
    fun saveStockThreshold(percent:Int)=launch { repo.updateStockThreshold(percent);_message.emit("آستانه هشدار انبار ذخیره شد.") }
    fun refreshStockAlerts()=launch { repo.refreshStockAlerts() }
    fun saveOrderPreferences(depositPercent:Int,defaultFrameColor:String,frameColorOptions:String,suggestCodRemainder:Boolean,defaultShippingPayer:String,defaultOrderStatus:String)=launch {
        repo.updateOrderPreferences(depositPercent,defaultFrameColor,frameColorOptions,suggestCodRemainder,defaultShippingPayer,defaultOrderStatus)
        _message.emit("تنظیمات سفارش ذخیره شد.")
    }
    suspend fun exportFullBackup():String = repo.exportFullBackup()
    suspend fun importFullBackup(json:String) { repo.importFullBackup(json); _message.emit("بکاپ کامل با موفقیت بازیابی شد.") }
    fun saveProfit(piece:Int,amount:Long)=launch { repo.updateProfitRule(piece,amount) }
    fun resetDefaults()=launch { repo.resetDefaults(); _message.emit("اطلاعات به حالت اولیه بازگردانده شد.") }
    suspend fun calculate(pieces:List<PieceInput>,ids:Set<String>?=null,packagingSizeKey:String="",profitFormula:String="",manualProfitToman:Long=0L,designMaterialsCostToman:Long=0L): PricingResult = repo.calculate(pieces,ids,packagingSizeKey,profitFormula,manualProfitToman,designMaterialsCostToman)
    fun notify(text:String){ _message.tryEmit(text) }
    private fun launch(block:suspend()->Unit)=viewModelScope.launch { try { block() } catch (t: Throwable) { _message.emit(t.message ?: "خطای نامشخص") } }
}
