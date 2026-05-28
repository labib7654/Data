package com.example.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_BACKUP = "backup_channel"
    const val CHANNEL_TRANSACTION = "transaction_channel"
    
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val backupChannel = NotificationChannel(
                CHANNEL_BACKUP,
                "النسخ الاحتياطي",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "إشعارات نجاح المزامنة السحابية" }
            
            val transactionChannel = NotificationChannel(
                CHANNEL_TRANSACTION,
                "المعاملات",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "إشعارات إضافة مبالغ جديدة" }
            
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(backupChannel)
            nm.createNotificationChannel(transactionChannel)
        }
    }
    
    fun notifyBackupSuccess(context: Context, fileCount: Int) {
        val prefs = context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notify_on_backup_success", true)) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("✅ تم حفظ بياناتك بنجاح")
            .setContentText("تم رفع نسخة احتياطية ($fileCount نسخة محفوظة على Drive)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(1001, notification)
        } catch (_: SecurityException) {}
    }
    
    fun notifyTransactionAdded(context: Context, accountName: String, amount: Double, currency: String) {
        val prefs = context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("show_notification_on_add", true)) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSACTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💰 تم إضافة مبلغ جديد")
            .setContentText("$accountName — ${"%.2f".format(amount)} $currency")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) {}
    }
}
