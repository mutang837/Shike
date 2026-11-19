package dev.shike

import android.app.Application
import androidx.work.*
import dev.shike.data.*
import dev.shike.notification.MaintenanceWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

class ShikeApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { Repository(this, ShikeDb.open(this), appScope) }

    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "shike-maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS).build(),
            )
    }
}
