package com.example.backup

import android.content.Context
import android.content.SharedPreferences
import com.example.data.AppDatabase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DriveBackupManager {

    // 1. Build GoogleSignInClient to request Drive scopes
    fun getSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    // 2. Build Drive service from the signed-in account
    fun getDriveService(context: Context, account: GoogleSignInAccount): Drive? {
        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account.account
            }
            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("دفتر الحسابات الذكي").build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 3. SECURE SHA-256 USER ID GENERATION (Problem 1)
    fun generateUserId(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(email.lowercase().trim().toByteArray())
        return "user_" + hash.take(16).joinToString("") { "%02x".format(it) }
    }

    // 4. VALIDATE SQLITE FILE MAGIC HEADER (Problem 1 - Requirement 4)
    fun isValidSqliteFile(bytes: ByteArray): Boolean {
        val header = "SQLite format 3\u0000"
        if (bytes.size < 16) return false
        val prefix = bytes.take(16).toByteArray()
        return prefix.decodeToString() == header
    }

    // 5. CLEAN OLD BACKUPS - Keep only latest 10 (Mيزات 4)
    suspend fun cleanOldBackups(drive: Drive, userId: String, keepCount: Int = 10) = withContext(Dispatchers.IO) {
        try {
            val allBackups = listBackups(drive, userId)
            if (allBackups.size > keepCount) {
                val toDelete = allBackups.drop(keepCount)
                toDelete.forEach { file ->
                    try {
                        drive.files().delete(file.id).execute()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    // 6. STORAGE QUOTA CHECK (Mيزات 4)
    suspend fun getAvailableStorageBytes(drive: Drive): Long = withContext(Dispatchers.IO) {
        try {
            val about = drive.about().get().setFields("storageQuota").execute()
            val quota = about.storageQuota
            (quota.limit ?: Long.MAX_VALUE) - (quota.usage ?: 0L)
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    // 7. UPLOAD DATABASE FILE TO GOOGLE DRIVE WITH RICH METADATA
    suspend fun uploadBackup(context: Context, drive: Drive, dbFile: java.io.File, userId: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!dbFile.exists()) return@withContext null
            
            // Re-initialize metadata properly
            val transactionCount = AppDatabase.getDatabase(context).transactionDao().getAccountCount()
            val formattedDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ledger_${formattedDate}_${transactionCount}accts.db"
            
            val folderId = getOrCreateBackupFolder(drive, userId)
            
            val friendlyDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date())
            val descText = "دفتر الحسابات - $transactionCount حساب - $friendlyDate"
            
            val fileMetadata = DriveFile().apply {
                name = fileName
                parents = listOf(folderId)
                mimeType = "application/octet-stream"
                description = descText
            }
            
            val mediaContent = com.google.api.client.http.FileContent("application/octet-stream", dbFile)
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size, createdTime")
                .execute()
            
            // Clean up to keep only latest 10 backups
            cleanOldBackups(drive, userId)
            
            uploadedFile.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 8. RETRIEVE LIST OF BACKUPS FROM GOOGLE DRIVE
    suspend fun listBackups(drive: Drive, userId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        try {
            val folderId = getOrCreateBackupFolder(drive, userId)
            drive.files().list()
                .setQ("'$folderId' in parents and trashed=false")
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, size, createdTime, description)")
                .execute()
                .files ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 9. DOWNLOAD AND RESTORE A DATABASE BACKUP (Problem 1)
    suspend fun downloadAndRestore(
        context: Context,
        drive: Drive,
        fileId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            
            val bytes = outputStream.toByteArray()
            if (!isValidSqliteFile(bytes)) {
                return@withContext false
            }

            // Close the current database connection safely first
            AppDatabase.closeDatabase()
            delay(200)
            
            // Overwrite database file
            val dbFile = context.getDatabasePath("ledger_database")
            dbFile.parentFile?.mkdirs()
            dbFile.writeBytes(bytes)
            
            // Re-initialize database immediately and safely
            AppDatabase.getDatabase(context)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 10. AUTO RESTORE AT LAUNCH IF FIRST LAUNCH (Problem 1 & Mيزة 1)
    suspend fun autoRestoreIfFirstLaunch(context: Context, drive: Drive, userId: String): Boolean {
        val prefs = context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("data_restored_once", false)) return false
        val backups = listBackups(drive, userId)
        if (backups.isEmpty()) return false
        val latestBackup = backups.first()
        val success = downloadAndRestore(context, drive, latestBackup.id)
        if (success) {
            prefs.edit().putBoolean("data_restored_once", true).apply()
        }
        return success
    }

    // Helper functions
    private suspend fun getOrCreateBackupFolder(drive: Drive, userId: String): String = withContext(Dispatchers.IO) {
        val rootFolderName = "AppBackup"
        val rootId = getOrCreateFolder(drive, rootFolderName, "root")
        getOrCreateFolder(drive, "user_$userId", rootId)
    }

    private suspend fun getOrCreateFolder(drive: Drive, folderName: String, parentId: String = "root"): String = withContext(Dispatchers.IO) {
        val query = if (parentId == "root") {
            "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
        } else {
            "mimeType='application/vnd.google-apps.folder' and name='$folderName' and '$parentId' in parents and trashed=false"
        }
        val result = drive.files().list()
            .setQ(query)
            .setFields("files(id)")
            .execute()
        
        if (!result.files.isNullOrEmpty()) {
            result.files[0].id
        } else {
            val folderMetadata = DriveFile().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                if (parentId != "root") {
                    parents = listOf(parentId)
                }
            }
            drive.files().create(folderMetadata).setFields("id").execute().id
        }
    }
}
