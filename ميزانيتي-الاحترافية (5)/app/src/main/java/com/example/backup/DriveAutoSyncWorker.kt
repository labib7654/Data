package com.example.backup

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.util.concurrent.TimeUnit

class DriveAutoSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val prefs = context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)
            val userId = prefs.getString("user_id", null) ?: return Result.failure()
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return Result.failure()
            val drive = DriveBackupManager.getDriveService(context, account) ?: return Result.retry()
            
            val dbFile = context.getDatabasePath("ledger_database")
            if (!dbFile.exists()) return Result.failure()
            
            val fileId = DriveBackupManager.uploadBackup(context, drive, dbFile, userId)
            
            if (fileId != null) {
                prefs.edit()
                    .putLong("last_auto_sync_time", System.currentTimeMillis())
                    .putString("last_sync_file_id", fileId)
                    .apply()
                
                // Show notification for auto sync success if enabled
                com.example.ui.NotificationHelper.notifyBackupSuccess(context, 1)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
    
    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val request = PeriodicWorkRequestBuilder<DriveAutoSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ledger_auto_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
        
        fun scheduleImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val request = OneTimeWorkRequestBuilder<DriveAutoSyncWorker>()
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
