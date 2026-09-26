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
    val packageOpen: Boolean = false
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
    val materials = repo.materials.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val products = repo.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pricedProducts = repo.pricedProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val orders = repo.orders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val profitRules = repo.profitRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _message=MutableSharedFlow<String>(extraBufferCapacity=8); val messages=_message.asSharedFlow()
    init { viewModelScope.launch { repo.ensurePricingStructure() } }

    fun saveMaterial(e: MaterialEntity)=launch { val n=repo.saveMaterial(e); _message.emit(if(n>0) "قیمت تغییر کرد؛ $n محصول تحت تأثیر قرار گرفت." else "متغیر ذخیره شد.") }
    fun sizePrices(id:String)=repo.sizePrices(id)
    fun saveSizePrice(e:SizePriceEntity)=launch { repo.saveSizePrice(e); _message.emit("قیمت ابعاد ذخیره شد.") }
    fun deleteSizePrice(id:String)=launch { repo.deleteSizePrice(id); _message.emit("قیمت ابعاد حذف شد.") }
    fun toggleMaterial(id:String,on:Boolean)=launch { repo.toggleMaterial(id,on) }
    fun deleteMaterial(id:String)=launch { val n=repo.deleteMaterial(id); _message.emit(if(n>0) "متغیر غیرفعال شد؛ در $n محصول استفاده شده بود." else "متغیر حذف شد.") }
    fun saveProduct(id:String?,name:String,pieces:List<PieceInput>,ids:Set<String>,active:Boolean=true,manualProfit:Long=0L,profitMode:String="MANUAL",profitFormula:String="",packagingSizeKey:String="")=launch { repo.saveProduct(id,name,pieces,ids,active,manualProfit,profitMode,profitFormula,packagingSizeKey); _message.emit("محصول ذخیره شد.") }
    fun duplicateProduct(id:String)=launch { repo.duplicateProduct(id); _message.emit("یک کپی از محصول ساخته شد.") }
    fun toggleProduct(id:String,on:Boolean)=launch { repo.toggleProduct(id,on) }
    fun deleteProduct(id:String)=launch { repo.deleteProduct(id); _message.emit("محصول حذف شد.") }
    fun saveOrder(productId:String,date:Long,customer:String,instagram:String,phone:String,province:String,city:String,addressDetails:String,postalCode:String,shipping:Long,received:Long,note:String)=launch {
        repo.createOrder(productId,date,customer,instagram,phone,province,city,addressDetails,postalCode,shipping,received,note); _message.emit("سفارش ارسالی ثبت شد.")
    }
    fun updateOrder(orderId:String,date:Long,customer:String,instagram:String,phone:String,province:String,city:String,addressDetails:String,postalCode:String,shipping:Long,received:Long,note:String)=launch {
        repo.updateOrder(orderId,date,customer,instagram,phone,province,city,addressDetails,postalCode,shipping,received,note); _message.emit("سفارش ویرایش شد.")
    }
    fun deleteOrder(orderId:String)=launch {
        repo.deleteOrder(orderId); _message.emit("سفارش حذف شد.")
    }
    fun saveSettings(rounding:Long,shipping:Long,dark:Boolean)=launch { repo.updateSettings(rounding,shipping,dark); _message.emit("تنظیمات ذخیره شد.") }
    suspend fun exportFullBackup():String = repo.exportFullBackup()
    suspend fun importFullBackup(json:String) { repo.importFullBackup(json); _message.emit("بکاپ کامل با موفقیت بازیابی شد.") }
    fun saveProfit(piece:Int,amount:Long)=launch { repo.updateProfitRule(piece,amount) }
    fun resetDefaults()=launch { repo.resetDefaults(); _message.emit("اطلاعات به حالت اولیه بازگردانده شد.") }
    suspend fun calculate(pieces:List<PieceInput>,ids:Set<String>?=null,packagingSizeKey:String=""): PricingResult = repo.calculate(pieces,ids,packagingSizeKey)
    fun notify(text:String){ _message.tryEmit(text) }
    private fun launch(block:suspend()->Unit)=viewModelScope.launch { try { block() } catch (t: Throwable) { _message.emit(t.message ?: "خطای نامشخص") } }
}
