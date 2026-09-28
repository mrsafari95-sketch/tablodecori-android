package com.tablodecori.app

import android.app.Application
import com.tablodecori.app.data.BackupManager
import com.tablodecori.app.data.SeedData
import com.tablodecori.app.data.WorkshopRepository
import com.tablodecori.app.data.PlannerScheduler
import com.tablodecori.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TablodecoriApp : Application() {
    lateinit var database: AppDatabase; private set
    lateinit var repository: WorkshopRepository; private set
    lateinit var backupManager: BackupManager; private set
    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.create(this)
        repository = WorkshopRepository(database, this)
        backupManager = BackupManager(database)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { SeedData.seedIfNeeded(database);PlannerScheduler.refreshAll(this@TablodecoriApp,database.plannerDao().tasks(),database.plannerDao().settings()) }
    }
}
