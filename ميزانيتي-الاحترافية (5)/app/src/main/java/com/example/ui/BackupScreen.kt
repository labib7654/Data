package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.backup.DriveBackupManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class UnifiedBackupFile(
    val id: String,
    val name: String,
    val size: Long,
    val formattedDate: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE) }
    
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    
    // States for backup tracking
    var backupFiles by remember { mutableStateOf<List<UnifiedBackupFile>>(emptyList()) }
    var signedInAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    
    var showRestoreConfirm by remember { mutableStateOf<String?>(null) }
    var showLocalRestoreConfirm by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val activeEmail = signedInAccount?.email
    val userId = remember { sharedPrefs.getString("user_id", "default_user") ?: "default_user" }
    
    // Auto sync recovery email to active Google Account email if connected
    LaunchedEffect(activeEmail) {
        if (!activeEmail.isNullOrBlank()) {
            sharedPrefs.edit().putString("recovery_email", activeEmail).apply()
        }
    }
    
    // Load backups list automatically based on Google Drive account
    LaunchedEffect(signedInAccount) {
        if (signedInAccount != null) {
            isLoading = true
            val drive = DriveBackupManager.getDriveService(context, signedInAccount!!)
            if (drive != null) {
                try {
                    val googleFiles = DriveBackupManager.listBackups(drive, userId)
                    backupFiles = googleFiles.map { googleFile ->
                        val sizeBytes = googleFile.getSize()?.toLong() ?: 0L
                        val dateStr = try {
                            val googleTime = googleFile.createdTime
                            if (googleTime != null) {
                                val date = Date(googleTime.value)
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
                            } else {
                                ""
                            }
                        } catch (e: Exception) {
                            ""
                        }
                        UnifiedBackupFile(
                            id = googleFile.id ?: "",
                            name = googleFile.name ?: "نسخة احتياطية سحابية",
                            size = sizeBytes,
                            formattedDate = dateStr
                        )
                    }
                } catch (e: Exception) {
                    statusMessage = "❌ فشل تحميل النسخ الاحتياطية سحابياً"
                    backupFiles = emptyList()
                }
            } else {
                statusMessage = "❌ تعذر تفعيل اتصال Google Drive"
                backupFiles = emptyList()
            }
            isLoading = false
        } else {
            backupFiles = emptyList()
        }
    }

    // Google Sign In Launcher
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                signedInAccount = task.getResult(ApiException::class.java)
                statusMessage = "تم تسجيل الدخول بنجاح: ${signedInAccount?.email}"
            } catch (e: ApiException) {
                statusMessage = "عذرًا، فشل تسجيل الدخول المباشر: ${e.localizedMessage}"
            }
        } else {
            statusMessage = "تم إلغاء تسجيل الدخول أو فشلت الخدمة"
        }
    }

    // Local Restore Launcher
    val localRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showLocalRestoreConfirm = uri
        }
    }

    val themeColor = Color(0xFF3949AB)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text("النسخ الاحتياطي والمزامنة السحابية", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // -- SECTION 1: LOCAL BACKUP / RESTORE --
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "النسخ الاحتياطي المحلي والأوفلاين",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يمكنك حفظ نسخة احتياطية محلية لمشاركتها عبر الواتساب أو تخزينها بأمان وعمل استرجاع منها في أي وقت.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                localRestoreLauncher.launch(arrayOf("*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Text("استرجاع محلي", color = Color.White)
                        }

                        Button(
                            onClick = {
                                val dbFile = context.getDatabasePath("ledger_database")
                                if (dbFile.exists()) {
                                    val tempDir = File(context.cacheDir, "backup")
                                    tempDir.mkdirs()
                                    val backupFile = File(tempDir, "ledger_backup.db")
                                    dbFile.copyTo(backupFile, overwrite = true)
                                    
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", backupFile)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "حفظ قواعد البيانات"))
                                } else {
                                    statusMessage = "❌ لم يتم العثور على قاعدة البيانات لتصديرها"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Text("مشاركة وحفظ", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -- SECTION 2: GOOGLE DRIVE BACKUP (CLOUD ONLY) --
            if (activeEmail == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "سجّل دخولك بحساب Google لتفعيل المزامنة التلقائية والنسخ الاحتياطي الآمن والمستمر للبيانات على السحاب بمجلدك الخاص.",
                            fontSize = 13.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val client = DriveBackupManager.getSignInClient(context)
                                signInLauncher.launch(client.signInIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                        ) {
                            Text("تسجيل الدخول بـ Google ☁️", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                DriveBackupManager.getSignInClient(context).signOut().addOnCompleteListener {
                                    signedInAccount = null
                                    backupFiles = emptyList()
                                    statusMessage = "تم تسجيل الخروج بنجاح"
                                }
                            }
                        ) { 
                            Text("تسجيل الخروج", color = Color.Red) 
                        }
                        
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("✅ متصل بـ Google Drive", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(activeEmail, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            statusMessage = "جاري حفظ وتشفير النسخة الاحتياطية سحابياً..."
                            val drive = DriveBackupManager.getDriveService(context, signedInAccount!!)
                            if (drive != null) {
                                val dbFile = context.getDatabasePath("ledger_database")
                                if (dbFile.exists()) {
                                    val fileId = DriveBackupManager.uploadBackup(context, drive, dbFile, userId)
                                    if (fileId != null) {
                                        statusMessage = "✅ تم حفظ ومزامنة النسخة بنجاح على Google Drive (مجلد دفتر الحسابات)"
                                        
                                        // Trigger Notification
                                        NotificationHelper.notifyBackupSuccess(context, backupFiles.size + 1)
                                        
                                        val googleFiles = DriveBackupManager.listBackups(drive, userId)
                                        backupFiles = googleFiles.map { googleFile ->
                                            val sizeBytes = googleFile.getSize()?.toLong() ?: 0L
                                            val dateStr = try {
                                                val googleTime = googleFile.createdTime
                                                if (googleTime != null) {
                                                    val date = Date(googleTime.value)
                                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
                                                } else {
                                                    ""
                                                }
                                            } catch (e: Exception) {
                                                ""
                                            }
                                            UnifiedBackupFile(
                                                id = googleFile.id ?: "",
                                                name = googleFile.name ?: "نسخة احتياطية",
                                                size = sizeBytes,
                                                formattedDate = dateStr
                                            )
                                        }
                                    } else {
                                        statusMessage = "❌ فشل الرفع السحابي، يرجى التحقق من مساحة التخزين أو الاتصال"
                                    }
                                } else {
                                    statusMessage = "❌ خطأ: قاعدة البيانات فارغة أو غير متوفرة للتصدير"
                                }
                            } else {
                                statusMessage = "❌ تعثر الاتصال الآمن بسيرفرات Google Drive"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    enabled = !isLoading
                ) {
                    Text("☁️ حفظ ومزامنة نسخة سحابية الآن", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            if (statusMessage.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (statusMessage.contains("✅")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                ) {
                    Text(
                        text = statusMessage, 
                        color = if (statusMessage.contains("✅")) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = themeColor)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (activeEmail != null) {
                Text(
                    text = "النسخ الاحتياطية المتوفرة بالسحاب (${backupFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.End
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(backupFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                              ) {
                                Button(
                                    onClick = { showRestoreConfirm = file.id },
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("استرجاع ومزامنة", fontSize = 11.sp, color = Color.White)
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = file.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.defaultMinSize(minWidth = 100.dp),
                                        textAlign = TextAlign.End
                                    )
                                    val sizeKb = file.size / 1024
                                    Text(
                                        text = "${file.formattedDate} | $sizeKb KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Text(
                text = "*** تلميح أمني: يتم تشفير دفاتر حساباتك السحابية بمجلد آمن باسم التطبيق لضمان خصوصيتك بالكامل، ويرتبط تلقائياً ببريد استرداد كلمة المرور الخاص بك.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // Cloud Restore Confirmation Dialog
    showRestoreConfirm?.let { fileId ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text("تأكيد استرجاع البيانات السحابية", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Red, textAlign = TextAlign.Right)
                }
            },
            text = {
                Text(
                    text = "⚠️ تنبيه هام: سيتم حذف واستبدال جميع بيانات الحسابات والمعاملات الحالية بالكامل وتثبيت نسخة الحسابات المختارة من السحاب! هل تود الاستمرار والمزامنة؟",
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            val targetFileId = showRestoreConfirm!!
                            showRestoreConfirm = null
                            statusMessage = "جاري تحميل وتثبيت نسخة قاعدة البيانات..."
                            
                            val drive = DriveBackupManager.getDriveService(context, signedInAccount!!)
                            if (drive != null) {
                                val success = DriveBackupManager.downloadAndRestore(context, drive, targetFileId)
                                if (success) {
                                    statusMessage = "✅ تم استرجاع ومزامنة البيانات بنجاح! يرجى إعادة تشغيل التطبيق لتحديث جميع الأرصدة."
                                    android.widget.Toast.makeText(context, "تم استرجاع البيانات بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    statusMessage = "❌ فشل تحميل وتثبيت قاعدة البيانات، تحقق من اتصال الانترنت."
                                }
                            } else {
                                statusMessage = "❌ تعثر الاتصال بمجلد Google Drive لمزامنة الاستيراد."
                            }
                            isLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text("نعم، استرجاع الآن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }

    // Local Restore Confirmation Dialog
    showLocalRestoreConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showLocalRestoreConfirm = null },
            title = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text("تأكيد استرجاع البيانات المحلية", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Red, textAlign = TextAlign.Right)
                }
            },
            text = {
                Text(
                    text = "⚠️ تنبيه هام: سيتم استبدال وحذف جميع المعاملات الحالية المثبتة والمسجلة لتثبيت نسخة المعاملات المحلية التي قمت باختيارها! هل تود الاستمرار؟",
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            val targetUri = showLocalRestoreConfirm!!
                            showLocalRestoreConfirm = null
                            statusMessage = "جاري تهيئة وتعمير ملفات قاعدة البيانات..."
                            try {
                                com.example.data.AppDatabase.closeDatabase()
                                
                                val dbFile = context.getDatabasePath("ledger_database")
                                dbFile.parentFile?.mkdirs()
                                
                                context.contentResolver.openInputStream(targetUri)?.use { inputStream ->
                                    FileOutputStream(dbFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                statusMessage = "✅ تم استيراد وتثبيت قاعدة البيانات المحلية بنجاح! الرجاء إعادة تشغيل التطبيق لتفعيل التغيير."
                                android.widget.Toast.makeText(context, "تم استيراد قاعدة البيانات بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                statusMessage = "❌ فشل الاسترجاع والترميم المحلي: ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text("استبدال وترميم الحسابات", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalRestoreConfirm = null }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }
}
