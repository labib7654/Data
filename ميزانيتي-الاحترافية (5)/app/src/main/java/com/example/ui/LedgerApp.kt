package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.example.backup.DriveBackupManager

val PrimaryBlue = Color(0xFF3F51B5)
val RedTriangle = Color(0xFFE53935)
val GreenTriangle = Color(0xFF43A047)
val LightBlueBubble = Color(0xFF90CAF9)
val TopBarBlue = Color(0xFF3949AB)

@Composable
fun LedgerAppHost(activity: FragmentActivity) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE) }
    
    var useDeviceLock by remember { mutableStateOf(sharedPrefs.getBoolean("use_device_lock", false)) }
    var usePassword by remember { mutableStateOf(sharedPrefs.getBoolean("use_password", false)) }
    var appPassword by remember { mutableStateOf(sharedPrefs.getString("password", "") ?: "") }
    
    var isDeviceAuthenticated by remember { mutableStateOf(!sharedPrefs.getBoolean("use_device_lock", false)) }
    var isPasswordAuthenticated by remember { mutableStateOf(!sharedPrefs.getBoolean("use_password", false)) }
    var authError by remember { mutableStateOf<String?>(null) }

    // Recovery, Email SMTP and OTP states
    val coroutineScope = rememberCoroutineScope()
    var recoveryEmail by remember { mutableStateOf(sharedPrefs.getString("recovery_email", "") ?: "") }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showOtpVerificationScreen by remember { mutableStateOf(false) }
    var showResetPasswordScreen by remember { mutableStateOf(false) }
    
    var isSendingResetCode by remember { mutableStateOf(false) }
    var sentVerificationCode by remember { mutableStateOf("") }
    var enteredOtpCode by remember { mutableStateOf("") }
    var otpErrorMsg by remember { mutableStateOf<String?>(null) }
    
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmNewPasswordInput by remember { mutableStateOf("") }
    var resetPasswordErrorMsg by remember { mutableStateOf<String?>(null) }

    // First launch - onboarding state variables
    var onboardingUserId by remember { mutableStateOf(sharedPrefs.getString("user_id", "") ?: "") }
    var onboardingStep by remember { mutableStateOf(1) } // 1: choose account, 2: choose passcode lock
    var onboardingError by remember { mutableStateOf<String?>(null) }
    var onboardingEmailInput by remember { mutableStateOf("") }
    
    var onboardingPassword by remember { mutableStateOf("") }
    var onboardingPasswordConfirm by remember { mutableStateOf("") }
    var onboardingPasswordError by remember { mutableStateOf<String?>(null) }

    var isOnboardingLoading by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestoreFileId by remember { mutableStateOf("") }
    var showEmailOnboardingWarning by remember { mutableStateOf(false) }

    // Schedule Daily Sync and Create Notification Channels on Launcher Load
    LaunchedEffect(Unit) {
        com.example.ui.NotificationHelper.createChannels(context)
        com.example.backup.DriveAutoSyncWorker.schedule(context)
    }

    // Stay synchronized in real-time with settings changes
    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "use_device_lock") {
                useDeviceLock = prefs.getBoolean("use_device_lock", false)
                isDeviceAuthenticated = !useDeviceLock
            }
            if (key == "use_password") {
                usePassword = prefs.getBoolean("use_password", false)
                isPasswordAuthenticated = !usePassword
            }
            if (key == "password") {
                appPassword = prefs.getString("password", "") ?: ""
            }
            if (key == "recovery_email") {
                recoveryEmail = prefs.getString("recovery_email", "") ?: ""
            }
            if (key == "user_id") {
                onboardingUserId = prefs.getString("user_id", "") ?: ""
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // App background/foreground observer to prompt locked states and safely request authentication
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // If device biometric lock option is enabled, lock application upon exit
                if (sharedPrefs.getBoolean("use_device_lock", false)) {
                    isDeviceAuthenticated = false
                }
                // If passcode security option is enabled, lock application upon exit
                if (sharedPrefs.getBoolean("use_password", false)) {
                    isPasswordAuthenticated = false
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Trigger biometric prompt only when the app is fully resumed and in the foreground
                if (useDeviceLock && !isDeviceAuthenticated) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (useDeviceLock && !isDeviceAuthenticated && !activity.isFinishing && !activity.isDestroyed) {
                            BiometricAuth.authenticate(
                                activity = activity,
                                onSuccess = { isDeviceAuthenticated = true },
                                onError = { error -> authError = error }
                            )
                        }
                    }, 300) // Small safety delay to let the window settle and prevent state saving/transition crashes
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (onboardingUserId.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            WatermarkLedgerBackground(alpha = 0.05f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LedgerLogo(size = 110.dp)
                Spacer(modifier = Modifier.height(24.dp))
                
                if (onboardingStep == 1) {
                    Text(
                        text = "مرحباً بك في دفتر الحسابات",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "نظام حسابات ذكي بمزامنة تلقائية. للبدء وضمان عدم ضياع بياناتك مطلقاً، يرجى تهيئة حسابك الآمن سحابياً ومزامنته بمجلد خاص بك:",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    val onboardingSignInLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            try {
                                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                                val account = task.getResult(ApiException::class.java)
                                if (account != null) {
                                    val email = account.email ?: ""
                                    val genId = com.example.backup.DriveBackupManager.generateUserId(email)
                                    sharedPrefs.edit()
                                        .putString("recovery_email", email)
                                        .putString("user_id", genId)
                                        .apply()
                                    
                                    recoveryEmail = email
                                    
                                    val drive = com.example.backup.DriveBackupManager.getDriveService(context, account)
                                    if (drive != null) {
                                        isOnboardingLoading = true
                                        onboardingError = null
                                        coroutineScope.launch {
                                            try {
                                                val backups = com.example.backup.DriveBackupManager.listBackups(drive, genId)
                                                isOnboardingLoading = false
                                                if (backups.isNotEmpty()) {
                                                    pendingRestoreFileId = backups.first().id
                                                    showRestoreDialog = true
                                                } else {
                                                    onboardingUserId = genId
                                                    onboardingStep = 2
                                                }
                                            } catch (e: Exception) {
                                                isOnboardingLoading = false
                                                onboardingUserId = genId
                                                onboardingStep = 2
                                            }
                                        }
                                    } else {
                                        onboardingUserId = genId
                                        onboardingStep = 2
                                    }
                                }
                            } catch (e: Exception) {
                                onboardingError = "فشل ربط حساب Google تلقائياً: ${e.localizedMessage}"
                            }
                        }
                    }

                    if (isOnboardingLoading) {
                        CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            val client = DriveBackupManager.getSignInClient(context)
                            onboardingSignInLauncher.launch(client.signInIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isOnboardingLoading
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الربط بحساب Google السحابي ☁️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(
                        text = "ـ أو الربط السريع بالبريد المباشر ـ",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = onboardingEmailInput,
                        onValueChange = { onboardingEmailInput = it },
                        placeholder = { Text("أدخل بريدك الإلكتروني هنا", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp),
                        singleLine = true,
                        enabled = !isOnboardingLoading
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (onboardingEmailInput.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(onboardingEmailInput).matches()) {
                                showEmailOnboardingWarning = true
                            } else {
                                onboardingError = "الرجاء إدخال بريد إلكتروني صالح وموثق لاستعادة الكود!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isOnboardingLoading
                    ) {
                        Text("إنشاء وربط الحساب بالبريد ✉️", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    if (onboardingError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = onboardingError!!,
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (onboardingStep == 2) {
                    Text(
                        text = "🔒 تأمين قفل الحماية للخصوصية",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "لتأمين خصوصية حساباتك المالية محلياً ومنع أي متطفل من تصفحها عند فتح جوالك. يمكنك تعيين رمز PIN الآن للخصوصية أو تخطيه والدخول مباشرة:",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = onboardingPassword,
                        onValueChange = { onboardingPassword = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("رمز المرور السري (أرقام فقط)", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = onboardingPasswordConfirm,
                        onValueChange = { onboardingPasswordConfirm = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("تأكيد رمز المرور السري", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    
                    if (onboardingPasswordError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = onboardingPasswordError!!,
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    Button(
                        onClick = {
                            if (onboardingPassword.isBlank()) {
                                onboardingPasswordError = "الرجاء كود الرمز السري"
                            } else if (onboardingPassword != onboardingPasswordConfirm) {
                                onboardingPasswordError = "رمز التأكيد غير متطابق!"
                            } else {
                                sharedPrefs.edit()
                                    .putBoolean("use_password", true)
                                    .putString("password", onboardingPassword)
                                    .putString("user_id", onboardingUserId)
                                    .apply()
                                
                                usePassword = true
                                appPassword = onboardingPassword
                                isPasswordAuthenticated = true
                                onboardingUserId = onboardingUserId
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تفعيل كلمة السر وقفل الحساب 🚀", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TextButton(
                        onClick = {
                            sharedPrefs.edit()
                                .putBoolean("use_password", false)
                                .remove("password")
                                .putString("user_id", onboardingUserId)
                                .apply()
                            
                            usePassword = false
                            isPasswordAuthenticated = true
                            onboardingUserId = onboardingUserId
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("الدخول مباشرة بدون رمز حماية 🔓", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showRestoreDialog) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                AlertDialog(
                    onDismissRequest = { 
                        showRestoreDialog = false 
                        onboardingUserId = sharedPrefs.getString("user_id", "") ?: ""
                        onboardingStep = 2
                    },
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text("تم العثور على بيانات سابقة", fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Right)
                        }
                    },
                    text = {
                        Text(
                            text = "وُجدت نسخة احتياطية سحابية محفوظة على حسابك في Google Drive. هل تريد استعادة بياناتك بالكامل الآن لمواصلة حساباتك؟",
                            fontSize = 14.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showRestoreDialog = false
                                if (account != null && pendingRestoreFileId.isNotBlank()) {
                                    val drive = com.example.backup.DriveBackupManager.getDriveService(context, account)
                                    if (drive != null) {
                                        isOnboardingLoading = true
                                        coroutineScope.launch {
                                            val success = com.example.backup.DriveBackupManager.downloadAndRestore(context, drive, pendingRestoreFileId)
                                            isOnboardingLoading = false
                                            if (success) {
                                                val recoveredUserId = sharedPrefs.getString("user_id", "") ?: ""
                                                onboardingUserId = recoveredUserId
                                                android.widget.Toast.makeText(context, "تم استعادة البيانات السحابية بالكامل وتحديث الأرصدة بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "فشل استعادة الملف المكتشف. سنبدأ كحساب جديد.", android.widget.Toast.LENGTH_SHORT).show()
                                                onboardingUserId = sharedPrefs.getString("user_id", "") ?: ""
                                                onboardingStep = 2
                                            }
                                        }
                                    } else {
                                        onboardingUserId = sharedPrefs.getString("user_id", "") ?: ""
                                        onboardingStep = 2
                                    }
                                } else {
                                    onboardingUserId = sharedPrefs.getString("user_id", "") ?: ""
                                    onboardingStep = 2
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("نعم، استعادة البيانات")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { 
                                showRestoreDialog = false 
                                onboardingUserId = sharedPrefs.getString("user_id", "") ?: ""
                                onboardingStep = 2
                            }
                        ) {
                            Text("لا، ابدأ كحساب جديد", color = Color.Gray)
                        }
                    }
                )
            }

            if (showEmailOnboardingWarning) {
                AlertDialog(
                    onDismissRequest = { showEmailOnboardingWarning = false },
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text("تنبيه المزامنة السحابية", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFF57C00), textAlign = TextAlign.Right)
                        }
                    },
                    text = {
                        Text(
                            text = "⚠️ المزامنة السحابية الحقيقية وتفادي ضياع الحسابات تتطلب تسجيل دخول Google.\n\nسيتم حفظ البيانات محلياً فقط في الوقت الحالي حتى تُكمل ربط حسابك بـ Google Drive من شاشة النسخ الاحتياطي.",
                            fontSize = 14.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showEmailOnboardingWarning = false
                                val email = onboardingEmailInput.trim()
                                val genId = com.example.backup.DriveBackupManager.generateUserId(email)
                                sharedPrefs.edit()
                                    .putString("custom_cloud_email", email)
                                    .putString("recovery_email", email)
                                    .putString("user_id", genId)
                                    .apply()
                                
                                recoveryEmail = email
                                onboardingUserId = genId
                                onboardingStep = 2
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("موافق، استمر")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEmailOnboardingWarning = false }) {
                            Text("إلغاء", color = Color.Gray)
                        }
                    }
                )
            }
        }
    } else if (useDeviceLock && !isDeviceAuthenticated) {
        // Biometric / Device Lock screen
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("تطبيق دفتر الحسابات مقفل", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("الرجاء التحقق من هويتك باستخدام قفل الهاتف لفتح التطبيق", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                if (authError != null) {
                    Text(authError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
                }
                Button(
                    onClick = {
                        BiometricAuth.authenticate(
                            activity = activity,
                            onSuccess = { isDeviceAuthenticated = true },
                            onError = { error -> authError = error }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("إعادة المحاولة / فتح الهاتف", color = Color.White)
                }
            }
        }
    } else if (usePassword && !isPasswordAuthenticated) {
        if (isSendingResetCode) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WatermarkLedgerBackground(alpha = 0.05f)
                SpinningLedgerLoadingIndicator(
                    size = 140.dp,
                    loadingText = "جاري إنشاء وإرسال الرمز السري الآمن إلى البريد..."
                )
            }
        } else if (showResetPasswordScreen) {
            // New password screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                WatermarkLedgerBackground(alpha = 0.05f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LedgerLogo(size = 110.dp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "تعيين رمز المرور الجديد",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "قم بإدخال رمز الحماية الجديد الخاص بالتطبيق للمتابعة بأمان",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("رمز المرور الجديد (أرقام فقط)", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmNewPasswordInput,
                        onValueChange = { confirmNewPasswordInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("تأكيد رمز المرور الجديد", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    
                    if (resetPasswordErrorMsg != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = resetPasswordErrorMsg!!,
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            if (newPasswordInput.isBlank()) {
                                resetPasswordErrorMsg = "الرجاء إدخال رمز مرور جديد"
                            } else if (newPasswordInput != confirmNewPasswordInput) {
                                resetPasswordErrorMsg = "كلمتا السر غير متطابقتين!"
                            } else {
                                sharedPrefs.edit().putString("password", newPasswordInput).apply()
                                isPasswordAuthenticated = true
                                showResetPasswordScreen = false
                                showOtpVerificationScreen = false
                                newPasswordInput = ""
                                confirmNewPasswordInput = ""
                                android.widget.Toast.makeText(context, "تم تحديث كلمة المرور وفتح التطبيق بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ الكود وإلغاء القفل", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TextButton(
                        onClick = {
                            showResetPasswordScreen = false
                            showOtpVerificationScreen = false
                        }
                    ) {
                        Text("إلغاء والرجوع", color = Color.Gray)
                    }
                }
            }
        } else if (showOtpVerificationScreen) {
            // OTP verification screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                WatermarkLedgerBackground(alpha = 0.05f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LedgerLogo(size = 110.dp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "التحقق من الرمز الآمن",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لقد أرسلنا رمز أمان مؤقت مكون من 6 أرقام إلى بريدك الإلكتروني المعتمد:\n$recoveryEmail\nالرجاء التحقق من صندوق الوارد أو البريد المهمل (Spam).",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = enteredOtpCode,
                        onValueChange = { enteredOtpCode = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("أدخل الرمز (6 أرقام)", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    )
                    
                    if (otpErrorMsg != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = otpErrorMsg!!,
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            if (enteredOtpCode.trim() == sentVerificationCode) {
                                showResetPasswordScreen = true
                                otpErrorMsg = null
                                enteredOtpCode = ""
                            } else {
                                otpErrorMsg = "الرمز المدخل غير صحيح! الرجاء التحقق من الرسالة الواردة وإعادة المحاولة."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("التحقق والمتابعة", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showOtpVerificationScreen = false
                                enteredOtpCode = ""
                                otpErrorMsg = null
                            }
                        ) {
                            Text("الرجوع للقفل", color = Color.Gray)
                        }
                        
                        TextButton(
                            onClick = {
                                isSendingResetCode = true
                                otpErrorMsg = null
                                val code = EmailRecoveryHelper.generateVerificationCode()
                                sentVerificationCode = code
                                coroutineScope.launch {
                                    val result = EmailRecoveryHelper.sendRecoveryEmail(recoveryEmail, code)
                                    isSendingResetCode = false
                                    if (result.isSuccess) {
                                        android.widget.Toast.makeText(context, "تم إعادة إرسال الرمز بنجاح.", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        try {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("OTP Code", code)
                                            clipboard.setPrimaryClip(clip)
                                        } catch (ignored: Exception) {}
                                        android.widget.Toast.makeText(context, "تعذر الإرسال الآمن للبريد: كود الاسترداد للفحص هو: $code (تم نسخه للجهاز تلقائياً)", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("إعادة إرسال الرمز", color = Color(0xFFE91E63))
                        }
                    }
                }
            }
        } else {
            // NORMAL PASSCODE LOCK SCREEN
            var enteredPasscode by remember { mutableStateOf("") }
            var incorrectPasswordError by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                // Background Watermark of Logo for beautiful branding experience
                WatermarkLedgerBackground(alpha = 0.05f)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Custom Branding Logo instead of simple Lock Icon
                    LedgerLogo(size = 110.dp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "رمز حماية التطبيق",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "أدخل كلمة السر الخاصة بالتطبيق للمتابعة",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Show standard PIN entry indicators (dots)
                    val requiredLen = appPassword.length.coerceAtLeast(1)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until requiredLen) {
                            val isFilled = i < enteredPasscode.length
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilled) PrimaryBlue else Color.LightGray)
                            )
                        }
                    }
                    
                    if (incorrectPasswordError) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "كلمة السر خطأ، يرجى المحاولة مرة أخرى",
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Standard visual numeric PIN pad
                    Column(
                        modifier = Modifier.widthIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("مسح", "0", "موافق")
                        )
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                row.forEach { digit ->
                                    Button(
                                        onClick = {
                                            incorrectPasswordError = false
                                            if (digit == "مسح") {
                                                if (enteredPasscode.isNotEmpty()) {
                                                    enteredPasscode = enteredPasscode.dropLast(1)
                                                }
                                            } else if (digit == "موافق") {
                                                if (enteredPasscode == appPassword) {
                                                    isPasswordAuthenticated = true
                                                } else {
                                                    incorrectPasswordError = true
                                                    enteredPasscode = ""
                                                }
                                            } else {
                                                if (enteredPasscode.length < requiredLen) {
                                                    enteredPasscode += digit
                                                }
                                                // Auto-submit if reaches exact password limit
                                                if (enteredPasscode == appPassword) {
                                                    isPasswordAuthenticated = true
                                                } else if (enteredPasscode.length == appPassword.length) {
                                                    incorrectPasswordError = true
                                                    enteredPasscode = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.5f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (digit == "موافق") Color(0xFF4CAF50) else if (digit == "مسح") Color(0xFFF44336) else Color(0xFFEEEEEE),
                                            contentColor = if (digit == "موافق" || digit == "مسح") Color.White else Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = digit,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { showForgotPasswordDialog = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "نسيت كلمة السر؟",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            style = androidx.compose.ui.text.TextStyle(
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        )
                    }
                }
            }
        }

        // Show prompt dialog for forgot password verification
        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = {
                    Text(
                        text = "استعادة كلمة المرور لدفتر الحسابات",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PrimaryBlue,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "سيتم إرسال رمز تحقق سري ومؤقت مكون من 6 أرقام إلى بريدك المعتمد والمربوط بنسختك الاحتياطية سحابياً لتأمين الدخول وتعيين كود جديد:",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                        ) {
                            Text(
                                text = recoveryEmail.takeIf { it.isNotBlank() } ?: "لا يوجد بريد مسجل للمزامنة سحابياً!",
                                fontWeight = FontWeight.Bold,
                                color = if (recoveryEmail.isNotBlank()) PrimaryBlue else Color.Red,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (recoveryEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(recoveryEmail).matches()) {
                                android.widget.Toast.makeText(context, "الرجاء تسجيل الدخول أولاً أو ربط الحساب ببريد صحيح", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                showForgotPasswordDialog = false
                                isSendingResetCode = true
                                otpErrorMsg = null
                                
                                val code = EmailRecoveryHelper.generateVerificationCode()
                                sentVerificationCode = code
                                
                                coroutineScope.launch {
                                    val result = EmailRecoveryHelper.sendRecoveryEmail(recoveryEmail, code)
                                    isSendingResetCode = false
                                    if (result.isSuccess) {
                                        android.widget.Toast.makeText(context, "تم إرسال الرمز بنجاح إلى البريد الإلكتروني.", android.widget.Toast.LENGTH_LONG).show()
                                        showOtpVerificationScreen = true
                                    } else {
                                        try {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("OTP Code", code)
                                            clipboard.setPrimaryClip(clip)
                                        } catch (ignored: Exception) {}
                                        android.widget.Toast.makeText(context, "تعذر الإرسال الآمن للبريد: كود الاسترداد للفحص هو: $code (تم نسخه للجهاز تلقائياً)", android.widget.Toast.LENGTH_LONG).show()
                                        showOtpVerificationScreen = true
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = recoveryEmail.isNotBlank()
                    ) {
                        Text("إرسال الرمز")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("إلغاء", color = Color.Gray)
                    }
                }
            )
        }
    } else {
        // App is unlocked successfully
        LedgerDashboard()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDashboard(viewModel: LedgerViewModel = viewModel()) {
    val accountsSummary by viewModel.accountsSummary.collectAsState()
    val totalOwedToUs by viewModel.totalOwedToUs.collectAsState()
    val totalOwedByUs by viewModel.totalOwedByUs.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedAccountForAdd by remember { mutableStateOf("") }
    var selectedAccountDetails by remember { mutableStateOf<String?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    
    var sortOption by remember { mutableStateOf("اسم") } // name, date, balance
    var filterOption by remember { mutableStateOf("الكل") } // all, on_him, for_him
    var showSortFilterSheet by remember { mutableStateOf(false) }

    var currentScreen by remember { mutableStateOf("dashboard") }

    if (currentScreen == "settings") {
        com.example.ui.SettingsScreen(onBack = { currentScreen = "dashboard" })
        return
    }
    if (currentScreen == "reports") {
        com.example.ui.ReportsScreen(accountsSummary = accountsSummary, transactions = transactions, onBack = { currentScreen = "dashboard" })
        return
    }
    if (currentScreen == "backup") {
        com.example.ui.BackupScreen(onBack = { currentScreen = "dashboard" })
        return
    }

    var showContactDialog by remember { mutableStateOf(false) }

    val filteredAccounts = accountsSummary.filter { 
        if (isSearching && searchQuery.isNotBlank()) {
            it.accountName.contains(searchQuery, ignoreCase = true)
        } else true
    }.filter {
        when (filterOption) {
            "عليه فقط" -> it.isOwedToUs && it.totalBalance > 0
            "له فقط" -> !it.isOwedToUs && it.totalBalance > 0 // Wait, what if totalBalance == 0?
            else -> true
        }
    }.sortedWith(Comparator { a, b ->
        when (sortOption) {
            "رصيد" -> b.totalBalance.compareTo(a.totalBalance)
            "تاريخ" -> 0 // Needs timestamp in summary, let's just ignore or sort by id
            else -> a.accountName.compareTo(b.accountName)
        }
    })
    
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("القائمة", modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                NavigationDrawerItem(label = { Text("التقارير") }, icon = { Icon(Icons.Default.DateRange, contentDescription = null) }, selected = false, onClick = { coroutineScope.launch { drawerState.close() }; currentScreen = "reports" })
                NavigationDrawerItem(label = { Text("حفظ نسخة احتياطية") }, icon = { Icon(Icons.Default.Save, contentDescription = null) }, selected = false, onClick = { coroutineScope.launch { drawerState.close() }; currentScreen = "backup" })
                NavigationDrawerItem(label = { Text("الإعدادات") }, icon = { Icon(Icons.Default.Settings, contentDescription = null) }, selected = false, onClick = { coroutineScope.launch { drawerState.close() }; currentScreen = "settings" })
                NavigationDrawerItem(label = { Text("للتواصل والدعم") }, icon = { Icon(Icons.Default.Phone, contentDescription = null) }, selected = false, onClick = { coroutineScope.launch { drawerState.close() }; showContactDialog = true })
                NavigationDrawerItem(label = { Text("حول التطبيق") }, icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) }, selected = false, onClick = { coroutineScope.launch { drawerState.close() }; showHelpDialog = true })
            }
        }
    ) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = { 
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedTextColor = Color.White,
                                focusedTextColor = Color.White
                            ),
                            placeholder = { Text("بحث بالحساب...", color = Color.White.copy(alpha=0.7f)) }
                        )
                    } else {
                        Text("عام", color = Color.White) 
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "القائمة", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { isSearching = !isSearching; if(!isSearching) searchQuery = "" }) { 
                        Icon(Icons.Default.Search, "بحث", tint = Color.White) 
                    }
                    
                    var showExportMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        IconButton(onClick = { showExportMenu = true }) { 
                            Icon(Icons.Default.Share, "مشاركة", tint = Color.White) 
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تصدير Excel (CSV)") },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val uri = com.example.export.ExportUtils.exportToCSV(context, transactions)
                                        if (uri != null) com.example.export.ExportUtils.shareFile(context, uri, "text/csv")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تصدير PDF") },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val uri = com.example.export.ExportUtils.exportToPDF(context, transactions)
                                        if (uri != null) com.example.export.ExportUtils.shareFile(context, uri, "application/pdf")
                                    }
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showSortFilterSheet = true }) { Icon(Icons.Default.FilterList, "ترتيب", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBlue)
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                modifier = Modifier.height(60.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Help, "مساعدة", tint = Color.White)
                    }
                    
                    Text(
                        text = "عليك: ${formatAmount(totalOwedByUs)} = لك: ${formatAmount(totalOwedToUs)} محلي",
                        color = Color.White,
                        fontSize = 14.sp
                    )

                    IconButton(onClick = { 
                        selectedAccountForAdd = ""
                        showAddDialog = true 
                    }) {
                        Icon(Icons.Default.AddCircleOutline, "إضافة مبلغ", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (filteredAccounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد حسابات مسجلة حاليا")
                }
            } else {
                LazyColumn {
                    items(filteredAccounts) { account ->
                        AccountItemRow(
                            account = account,
                            sharedPrefs = sharedPrefs,
                            onAddClick = {
                                selectedAccountForAdd = account.accountName
                                showAddDialog = true
                            },
                            onRowClick = {
                                selectedAccountDetails = account.accountName
                            }
                        )
                        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(8.dp), color = Color.White) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(TopBarBlue).padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مساعدة - دفتر الحسابات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showHelpDialog = false }) {
                            Icon(Icons.Default.Close, "إغلاق", tint = Color.White)
                        }
                    }
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        item {
                            Text("مقدمة", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            HorizontalDivider(color = PrimaryBlue)
                            Text("مرحباً بك مع برنامج دفتر الحسابات: دقة، سهولة، أمان. يتيح لك التطبيق إدارة ديونك وحساباتك بكل مرونة وسهولة.", modifier = Modifier.padding(vertical = 8.dp))
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("الرموز والألوان", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            HorizontalDivider(color = PrimaryBlue)
                            Text("🔴 مثلث أحمر يشير للأسفل (عليه): يعني أن القيمة عبارة عن دين على هذا الشخص لصالحك، أي لك عنده مال.")
                            Text("🟢 مثلث أخضر يشير للأعلى (له): يعني أن القيمة عبارة عن رصيد له عندك، أو أنه قام بالدفع لك (دين عليك).")
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("إضافة مبلغ", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            HorizontalDivider(color = PrimaryBlue)
                            Text("يمكنك إضافة مبلغ عبر زر (+) في الصفحة الرئيسية أو زر (+) بجانب اسم العميل. عند التعديل، اضغط مطولاً على العملية المراد تعديلها.", modifier = Modifier.padding(vertical = 8.dp))
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("طباعة ومشاركة البيانات", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            HorizontalDivider(color = PrimaryBlue)
                            Text("يمكنك تحويل كشف الحساب إلى PDF أو جدول إكسل (CSV) ومشاركته عبر الواتساب أو الإيميل أو تطبيقات أخرى عبر زر المشاركة.", modifier = Modifier.padding(vertical = 8.dp))
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("حماية قاعدة البيانات / النسخ الاحتياطي", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            HorizontalDivider(color = PrimaryBlue)
                            Text("يمكنك تصدير نسخة احتياطية من قائمة النسخ الاحتياطي. *** تأكد بشكل دوري من فعالية الحفظ بحفظ النسخة في مكان آمن.", modifier = Modifier.padding(vertical = 8.dp), color = Color.Red)
                        }
                    }
                }
            }
        }
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            title = { Text("للتواصل والدعم (Ver D1.293)", color = PrimaryBlue, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("أرقام التواصل / الدعم والواتساب:", fontWeight = FontWeight.Bold, color = PrimaryBlue, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // First Number row
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row {
                                IconButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+967771172888"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }) {
                                    Icon(Icons.Default.Phone, "اتصال", tint = Color(0xFF3F51B5))
                                }
                                IconButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=967771172888&text=" + Uri.encode("السلام عليكم، أريد دعم فني بخصوص تطبيق دفتر الحسابات")))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }) {
                                    Icon(Icons.Default.Chat, "واتساب", tint = Color(0xFF4CAF50))
                                }
                            }
                            Text("+967 771172888", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Second Number row
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row {
                                IconButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+967770939359"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }) {
                                    Icon(Icons.Default.Phone, "اتصال", tint = Color(0xFF3F51B5))
                                }
                                IconButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=967770939359&text=" + Uri.encode("السلام عليكم، أريد دعم فني بخصوص تطبيق دفتر الحسابات")))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }) {
                                    Icon(Icons.Default.Chat, "واتساب", tint = Color(0xFF4CAF50))
                                }
                            }
                            Text("+967 770939359", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("البريد الإلكتروني للدعم:", fontWeight = FontWeight.Bold, color = PrimaryBlue, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    // Email row
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:labibradaan@gmail.com")).apply {
                                        putExtra(Intent.EXTRA_SUBJECT, "طلب دعم فني - تطبيق دفتر الحسابات")
                                        putExtra(Intent.EXTRA_TEXT, "السلام عليكم ورحمة الله وبركاته،\nأواجه استفساراً بخصوص...")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }) {
                                Icon(Icons.Default.Email, "إيميل", tint = Color(0xFFE53935))
                            }
                            Text("labibradaan@gmail.com", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactDialog = false }) { Text("إغلاق", fontWeight = FontWeight.Bold) } }
        )
    }

    if (showSortFilterSheet) {
        AlertDialog(
            onDismissRequest = { showSortFilterSheet = false },
            title = { Text("ترتيب وتصفية") },
            text = {
                Column {
                    Text("الترتيب حسب:", fontWeight = FontWeight.Bold)
                    Row {
                        RadioButton(selected = sortOption == "اسم", onClick = { sortOption = "اسم" })
                        Text("الاسم", modifier = Modifier.align(Alignment.CenterVertically))
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(selected = sortOption == "رصيد", onClick = { sortOption = "رصيد" })
                        Text("الرصيد", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    Text("تصفية الرصيد:", fontWeight = FontWeight.Bold)
                    Row {
                        RadioButton(selected = filterOption == "الكل", onClick = { filterOption = "الكل" })
                        Text("الكل", modifier = Modifier.align(Alignment.CenterVertically))
                        RadioButton(selected = filterOption == "عليه فقط", onClick = { filterOption = "عليه فقط" })
                        Text("عليه", modifier = Modifier.align(Alignment.CenterVertically))
                        RadioButton(selected = filterOption == "له فقط", onClick = { filterOption = "له فقط" })
                        Text("له", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortFilterSheet = false }) { Text("تطبيق") } }
        )
    }

    if (showAddDialog) {
        AddTransactionScreen(
            initialName = selectedAccountForAdd,
            onDismiss = { showAddDialog = false },
            onSave = { type, amount, name, details, currency, imageUri, phone ->
                viewModel.addTransaction(
                    Transaction(
                        type = type,
                        amount = amount,
                        accountName = name,
                        details = details,
                        currency = currency,
                        imageUri = imageUri,
                        phoneNumber = phone
                    )
                )
                com.example.ui.NotificationHelper.notifyTransactionAdded(context, name, amount, currency)
                showAddDialog = false
            }
        )
    }

    if (selectedAccountDetails != null) {
        val accountTransactions = transactions.filter { it.accountName == selectedAccountDetails }
        val otherAccounts = accountsSummary.map { it.accountName }.filter { it != selectedAccountDetails }
        AccountDetailsDialog(
            accountName = selectedAccountDetails!!,
            transactions = accountTransactions,
            otherAccounts = otherAccounts,
            sharedPrefs = sharedPrefs,
            onDismiss = { selectedAccountDetails = null },
            onDelete = { id -> viewModel.deleteTransaction(id) },
            onAddTransaction = { t -> 
                viewModel.addTransaction(t)
                com.example.ui.NotificationHelper.notifyTransactionAdded(context, t.accountName, t.amount, t.currency)
            },
            onExportPdf = {
                coroutineScope.launch {
                    val uri = com.example.export.ExportUtils.exportToPDF(context, accountTransactions)
                    if (uri != null) com.example.export.ExportUtils.shareFile(context, uri, "application/pdf")
                }
            },
            onExportCsv = {
                coroutineScope.launch {
                    val uri = com.example.export.ExportUtils.exportToCSV(context, accountTransactions)
                    if (uri != null) com.example.export.ExportUtils.shareFile(context, uri, "text/csv")
                }
            }
        )
    }
}
}

@Composable
fun AccountDetailsDialog(
    accountName: String,
    transactions: List<Transaction>,
    otherAccounts: List<String>,
    sharedPrefs: android.content.SharedPreferences,
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onAddTransaction: (Transaction) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    val context = LocalContext.current
    val phone = transactions.maxByOrNull { it.timestamp }?.phoneNumber ?: ""
    var showMenu by remember { mutableStateOf(false) }
    
    // Quick Add Properties
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var quickAddAmount by remember { mutableStateOf("") }
    var quickAddDetails by remember { mutableStateOf("") }
    var quickAddCurrency by remember { mutableStateOf("محلي") }
    var quickAddImageUriStr by remember { mutableStateOf("") }
    var quickAddShowCalcDialog by remember { mutableStateOf(false) }
    
    val quickAddCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val file = java.io.File(context.cacheDir, "img_${System.currentTimeMillis()}.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            quickAddImageUriStr = file.absolutePath
        }
    }
    
    // Filter properties
    var filterQuery by remember { mutableStateOf("") }
    var showAdvancedSearch by remember { mutableStateOf(false) }
    
    // Transfer
    var showTransferDialog by remember { mutableStateOf(false) }
    var targetTransferAccount by remember { mutableStateOf("") }
    var transferAmount by remember { mutableStateOf("") }
    
    // Other dialogs
    var showThermalPrinterDialog by remember { mutableStateOf(false) }
    var showCeilingDialog by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var showCategorySelectDialog by remember { mutableStateOf(false) }

    // Close balance
    var showCloseBalanceDialog by remember { mutableStateOf(false) }

    // Prepare list with running balances
    val sortedTransactions = transactions.sortedBy { it.timestamp }
    var runningBalance = 0.0
    val listWithBalances = sortedTransactions.map { t ->
        if (t.type == TransactionType.ON_HIM) {
            runningBalance += t.amount
        } else {
            runningBalance -= t.amount
        }
        Pair(t, runningBalance)
    }.reversed()

    val filteredList = listWithBalances.filter { 
        filterQuery.isBlank() || it.first.details.contains(filterQuery, ignoreCase = true)
    }

    val currentBalance = runningBalance
    val finalBalanceText = if (currentBalance >= 0) "عليه: ${formatAmount(currentBalance)}" else "له: ${formatAmount(kotlin.math.abs(currentBalance))}"

    val generateStatement = {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("كشف حساب: $accountName\n")
        sb.append("الرصيد: $finalBalanceText محلي\n\n")
        sb.append("اخر العمليات:\n")
        listWithBalances.take(10).forEach { (t, _) ->
            val typeStr = if (t.type == TransactionType.ON_HIM) "عليه" else "له"
            sb.append("${dateFormat.format(Date(t.timestamp))} | ${t.amount} ($typeStr) | ${t.details.ifBlank { "بدون تفاصيل" }}\n")
        }
        sb.toString()
    }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text(accountName, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAdvancedSearch = true }) {
                            Icon(Icons.Default.Search, "بحث", tint = Color.White)
                        }
                        IconButton(onClick = { onExportPdf() }) {
                            Icon(Icons.Default.PictureAsPdf, "PDF", tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "خيارات", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(text = { Text("بحث متقدم") }, onClick = { showMenu = false; showAdvancedSearch = true })
                                DropdownMenuItem(text = { Text("طابعة حرارية") }, onClick = { showMenu = false; showThermalPrinterDialog = true })
                                DropdownMenuItem(text = { Text("مشاركة") }, onClick = {
                                    showMenu = false
                                    val intent = Intent(Intent.ACTION_SEND)
                                    intent.type = "text/plain"
                                    intent.putExtra(Intent.EXTRA_TEXT, generateStatement())
                                    context.startActivity(Intent.createChooser(intent, "مشاركة كشف الحساب"))
                                })
                                DropdownMenuItem(text = { Text("إكسل") }, onClick = { showMenu = false; onExportCsv() })
                                DropdownMenuItem(text = { Text("إرسال رسالة نصية") }, onClick = {
                                    showMenu = false
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                                    intent.putExtra("sms_body", generateStatement())
                                    context.startActivity(intent)
                                })
                                DropdownMenuItem(text = { Text("إرسال رسالة واتساب") }, onClick = {
                                    showMenu = false
                                    val numberParam = if (phone.isNotBlank()) "&phone=${phone.replace(Regex("[^0-9+]"), "")}" else ""
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(generateStatement())}$numberParam"))
                                    context.startActivity(intent)
                                })
                                DropdownMenuItem(text = { Text("إرسال رسالة نصية+واتساب") }, onClick = {
                                    showMenu = false
                                    val statement = generateStatement()
                                    val intent = Intent(Intent.ACTION_SEND)
                                    intent.type = "text/plain"
                                    intent.putExtra(Intent.EXTRA_TEXT, statement)
                                    context.startActivity(Intent.createChooser(intent, "رسالة نصية أو واتساب"))
                                })
                                DropdownMenuItem(text = { Text("مصادقة على الحساب واتساب") }, onClick = { 
                                    showMenu = false 
                                    val stmt = "أرجو مراجعة كشف الحساب والمصادقة عليه:\n\n${generateStatement()}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(stmt)}"))
                                    context.startActivity(intent)
                                })
                                DropdownMenuItem(text = { Text("إغلاق الرصيد") }, onClick = { showMenu = false; showCloseBalanceDialog = true })
                                DropdownMenuItem(text = { Text("تحويل من حساب إلى حساب") }, onClick = { showMenu = false; showTransferDialog = true })
                                DropdownMenuItem(text = { Text("التنبيهات") }, onClick = { showMenu = false; showAlertDialog = true })
                                DropdownMenuItem(text = { Text("سقف الحساب") }, onClick = { showMenu = false; showCeilingDialog = true })
                                DropdownMenuItem(text = { Text("تصنيف الحساب") }, onClick = { showMenu = false; showCategorySelectDialog = true })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBlue)
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = PrimaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier.height(60.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            IconButton(onClick = {
                                val uriStr = if (phone.isNotBlank()) "smsto:$phone" else "smsto:"
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse(uriStr))
                                intent.putExtra("sms_body", generateStatement())
                                context.startActivity(intent)
                            }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Email, "رسالة", tint = androidx.compose.ui.graphics.Color.White)
                            }
                            IconButton(onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                                if (phone.isNotBlank()) {
                                    intent.data = android.net.Uri.parse("tel:$phone")
                                }
                                context.startActivity(intent)
                            }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Phone, "اتصال", tint = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                        
                        Text(
                            text = "$finalBalanceText محلي",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            IconButton(onClick = { showTransferDialog = true }) {
                                Icon(Icons.Default.CompareArrows, "تحويل", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = { 
                                showQuickAddDialog = true
                            }) {
                                Icon(Icons.Default.AddCircleOutline, "إضافة", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)) {
                
                // Header
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue)
                    .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الرصيد", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("التفاصيل", color = Color.White, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                    Text("المبلغ", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("التاريخ", color = Color.White, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredList) { (t, runBal) ->
                        val isOwedToUs = t.type == TransactionType.ON_HIM
                        val iconTint = if (isOwedToUs) RedTriangle else GreenTriangle
                        
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("حذف معاملة") },
                                text = { Text("هل أنت متأكد من حذف هذه المعاملة؟") },
                                confirmButton = {
                                    Button(onClick = { showDeleteDialog = false; onDelete(t.id) }) { Text("حذف", color = Color.White) }
                                },
                                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") } }
                            )
                        }

                        // We can use Foundation's combinedClickable if we opt in to ExperimentalFoundationApi.
                        // For simplicity, we just add a delete icon or let standard clickable do it.
                        // Wait, I will just add an IconButton for delete in the row to keep it transparent to the user.
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatAmount(runBal), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 14.sp)
                            
                            Text(t.details.ifBlank { "بدون تفاصيل" }, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, fontSize = 14.sp)
                            
                            Row(modifier = Modifier.weight(1.2f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(formatAmount(t.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Icon(if (isOwedToUs) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp, null, tint = iconTint, modifier = Modifier.size(16.dp))
                            }
                            
                            val dateFormat = SimpleDateFormat("HH:mm\ndd-MM-yyyy", Locale.getDefault())
                            Text(dateFormat.format(Date(t.timestamp)), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            
                            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, "حذف", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    // Dialogs implementations
    if (showAdvancedSearch) {
        AlertDialog(
            onDismissRequest = { showAdvancedSearch = false },
            title = { Text("بحث متقدم") },
            text = {
                Column {
                    TextField(
                        value = filterQuery, 
                        onValueChange = { filterQuery = it },
                        label = { Text("كلمة البحث (التفاصيل)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ملاحظة: تصفية بالتاريخ غير مدعومة حاليا، جار البحث في التفاصيل فقط.", color = Color.Gray, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdvancedSearch = false }) { Text("تم") }
            },
            dismissButton = {
                TextButton(onClick = { filterQuery = ""; showAdvancedSearch = false }) { Text("إلغاء الفلتر") }
            }
        )
    }

    if (showCloseBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showCloseBalanceDialog = false },
            title = { Text("إغلاق الرصيد") },
            text = { Text("هل تريد إضافة معاملة لتصفير هذا الرصيد بالكامل ($finalBalanceText)؟") },
            confirmButton = {
                Button(onClick = { 
                    if (currentBalance != 0.0) {
                        val closeType = if (currentBalance > 0) TransactionType.FOR_HIM else TransactionType.ON_HIM
                        onAddTransaction(Transaction(
                            type = closeType,
                            amount = kotlin.math.abs(currentBalance),
                            accountName = accountName,
                            details = "إغلاق رصيد / تصفية حساب"
                        ))
                    }
                    showCloseBalanceDialog = false 
                }) { Text("تأكيد والإغلاق") }
            },
            dismissButton = { TextButton(onClick = { showCloseBalanceDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("تحويل لحساب آخر") },
            text = {
                Column {
                    var expandedList by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expandedList = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (targetTransferAccount.isBlank()) "اختر الحساب المحول إليه" else targetTransferAccount)
                        }
                        DropdownMenu(expanded = expandedList, onDismissRequest = { expandedList = false }) {
                            otherAccounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc) }, onClick = { targetTransferAccount = acc; expandedList = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = transferAmount,
                        onValueChange = { transferAmount = it },
                        label = { Text("المبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { 
                    val amt = transferAmount.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && targetTransferAccount.isNotBlank()) {
                        // Subtract from this account (- له) because we are transferring his debt or his money
                        // Actually a transfer means we tell this account they paid (FOR_HIM) 
                        // and we tell the other account they owe us (ON_HIM)
                        onAddTransaction(Transaction(type = TransactionType.FOR_HIM, amount = amt, accountName = accountName, details = "تحويل إلى $targetTransferAccount"))
                        onAddTransaction(Transaction(type = TransactionType.ON_HIM, amount = amt, accountName = targetTransferAccount, details = "تحويل من $accountName"))
                        showTransferDialog = false 
                    }
                }) { Text("تحويل") }
            },
            dismissButton = { TextButton(onClick = { showTransferDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showThermalPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showThermalPrinterDialog = false },
            title = { Text("طباعة حرارية") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    SpinningLedgerLoadingIndicator(
                        size = 110.dp,
                        loadingText = "جاري البحث عن طابعة بلوتوث..."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showThermalPrinterDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showAlertDialog) {
        var note by remember { mutableStateOf(sharedPrefs.getString("alert_$accountName", "") ?: "") }
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            title = { Text("إعداد تنبيه / ملاحظة") },
            text = { 
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("الملاحظة") }
                )
            },
            confirmButton = { 
                Button(onClick = { 
                    sharedPrefs.edit().putString("alert_$accountName", note).apply()
                    showAlertDialog = false 
                }) { Text("حفظ التنبيه") } 
            }
        )
    }

    if (showCeilingDialog) {
        var limit by remember { mutableStateOf(sharedPrefs.getString("ceiling_$accountName", "") ?: "") }
        AlertDialog(
            onDismissRequest = { showCeilingDialog = false },
            title = { Text("تحديد سقف الحساب") },
            text = { 
                TextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("الحد الأقصى للديون") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                ) 
            },
            confirmButton = { 
                Button(onClick = { 
                    sharedPrefs.edit().putString("ceiling_$accountName", limit).apply()
                    showCeilingDialog = false 
                }) { Text("حفظ السقف") } 
            }
        )
    }

    if (showCategorySelectDialog) {
        val categories = remember {
            sharedPrefs.getString("categories_list", "عام,عملاء,موردين")
                ?.split(",")
                ?.filter { it.isNotBlank() } ?: listOf("عام", "عملاء", "موردين")
        }
        var currentSelectedCategory by remember {
            mutableStateOf(sharedPrefs.getString("account_category_${accountName}", "عام") ?: "عام")
        }
        AlertDialog(
            onDismissRequest = { showCategorySelectDialog = false },
            title = { Text("تصنيف الحساب: $accountName", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentSelectedCategory = cat }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentSelectedCategory == cat),
                                onClick = { currentSelectedCategory = cat }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    sharedPrefs.edit().putString("account_category_${accountName}", currentSelectedCategory).apply()
                    showCategorySelectDialog = false
                    android.widget.Toast.makeText(context, "تم حفظ تصنيف الحساب كـ $currentSelectedCategory", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategorySelectDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showQuickAddDialog) {
        Dialog(
            onDismissRequest = { showQuickAddDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(Color.White)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title: accountName
                    Text(
                        text = accountName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "إضافة تفاصيل العملية الحالية",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Row with Date / Calculator on the Left, and Amount TextField on the Right (Pinkish underline/accent)
                    val currentDate = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Date + Calculator Icon on the Left
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentDate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = { quickAddShowCalcDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = "حاسبة",
                                    tint = Color(0xFF4CAF50), // Green calculator icon as in the image
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Amount Enter input textfield on the Right
                        Box(modifier = Modifier.weight(1.2f)) {
                            TextField(
                                value = quickAddAmount,
                                onValueChange = { quickAddAmount = it },
                                placeholder = { 
                                    Text(
                                        text = "المبلغ", 
                                        textAlign = TextAlign.End, 
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    ) 
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color(0xFFE91E63), // Pinkish underline accent like the image
                                    focusedIndicatorColor = Color(0xFFE91E63)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row for Details text field, with Camera icon on its left
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { quickAddCameraLauncher.launch(null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "أخذ صورة",
                                tint = if (quickAddImageUriStr.isNotBlank()) PrimaryBlue else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        TextField(
                            value = quickAddDetails,
                            onValueChange = { quickAddDetails = it },
                            placeholder = { 
                                Text(
                                    text = "التفاصيل", 
                                    textAlign = TextAlign.End, 
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Gray
                                ) 
                            },
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.LightGray,
                                focusedIndicatorColor = PrimaryBlue
                            ),
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End, fontSize = 15.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Currency selection row (محلي, سعودي, دولار)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = quickAddCurrency == "محلي",
                                onClick = { quickAddCurrency = "محلي" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE91E63))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("محلي", fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = quickAddCurrency == "دولار",
                                onClick = { quickAddCurrency = "دولار" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE91E63))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("دولار", fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = quickAddCurrency == "سعودي",
                                onClick = { quickAddCurrency = "سعودي" },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE91E63))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("سعودي", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 'له' and 'عليه' buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // له Button (Green arrow up, colored background)
                        Button(
                            onClick = {
                                val parsed = quickAddAmount.toDoubleOrNull() ?: evaluateMathExpression(quickAddAmount)
                                if (!parsed.isNaN() && parsed > 0) {
                                    onAddTransaction(
                                        Transaction(
                                            type = TransactionType.FOR_HIM,
                                            amount = parsed,
                                            accountName = accountName,
                                            details = quickAddDetails,
                                            currency = quickAddCurrency,
                                            imageUri = quickAddImageUriStr,
                                            phoneNumber = phone,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    // Reset state and dismiss
                                    quickAddAmount = ""
                                    quickAddDetails = ""
                                    quickAddImageUriStr = ""
                                    showQuickAddDialog = false
                                } else {
                                    android.widget.Toast.makeText(context, "الرجاء إدخال مبلغ صحيح", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropUp,
                                    contentDescription = "له",
                                    tint = GreenTriangle,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("له", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        // عليه Button (Red arrow down, colored background)
                        Button(
                            onClick = {
                                val parsed = quickAddAmount.toDoubleOrNull() ?: evaluateMathExpression(quickAddAmount)
                                if (!parsed.isNaN() && parsed > 0) {
                                    onAddTransaction(
                                        Transaction(
                                            type = TransactionType.ON_HIM,
                                            amount = parsed,
                                            accountName = accountName,
                                            details = quickAddDetails,
                                            currency = quickAddCurrency,
                                            imageUri = quickAddImageUriStr,
                                            phoneNumber = phone,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                    // Reset state and dismiss
                                    quickAddAmount = ""
                                    quickAddDetails = ""
                                    quickAddImageUriStr = ""
                                    showQuickAddDialog = false
                                } else {
                                    android.widget.Toast.makeText(context, "الرجاء إدخال مبلغ صحيح", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "عليه",
                                    tint = RedTriangle,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("عليه", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            showQuickAddDialog = false
                        }
                    ) {
                        Text("إلغاء", color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (quickAddShowCalcDialog) {
        var calcInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { quickAddShowCalcDialog = false },
            title = { Text("الآلة الحاسبة") },
            text = {
                Column {
                    OutlinedTextField(
                        value = calcInput,
                        onValueChange = { calcInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("مثال: 5000+300") },
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // grid calculation keys
                    val keys = listOf(
                        listOf("7", "8", "9", "/"),
                        listOf("4", "5", "6", "*"),
                        listOf("1", "2", "3", "-"),
                        listOf("0", ".", "=", "+")
                    )
                    keys.forEach { rowKeys ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowKeys.forEach { key ->
                                Button(
                                    onClick = {
                                        if (key == "=") {
                                            val res = evaluateMathExpression(calcInput)
                                            if (!res.isNaN()) {
                                                calcInput = res.toString()
                                            }
                                        } else {
                                            calcInput += key
                                        }
                                    },
                                    modifier = Modifier.weight(1f).padding(2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5), contentColor = Color.Black)
                                ) {
                                    Text(key, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val res = evaluateMathExpression(calcInput)
                    if (!res.isNaN()) {
                        quickAddAmount = res.toString()
                    } else if (calcInput.toDoubleOrNull() != null) {
                        quickAddAmount = calcInput
                    }
                    quickAddShowCalcDialog = false
                }) {
                    Text("استخدام النتيجة")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickAddShowCalcDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun AccountItemRow(
    account: AccountSummary,
    sharedPrefs: android.content.SharedPreferences,
    onAddClick: () -> Unit,
    onRowClick: () -> Unit
) {
    val limitStr = sharedPrefs.getString("ceiling_${account.accountName}", "") ?: ""
    val limit = limitStr.toDoubleOrNull() ?: 0.0
    val isExceeded = limit > 0 && account.totalBalance > limit && account.isOwedToUs
    val rowBackground = if (isExceeded) Color(0xFFFFEBEE) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable { onRowClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Arrow
        if (account.isOwedToUs) {
            Icon(Icons.Default.ArrowDropDown, "عليه", tint = RedTriangle, modifier = Modifier.size(32.dp))
        } else {
            Icon(Icons.Default.ArrowDropUp, "له", tint = GreenTriangle, modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Balance Segmented by Currency
        Column(
            modifier = Modifier.width(110.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (account.balancesByCurrency.size <= 1) {
                Text(
                    text = "${formatAmount(account.totalBalance)} ${account.primaryCurrency}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray
                )
            } else {
                account.balancesByCurrency.forEach { (currency, bal) ->
                    val isOwed = bal > 0
                    val amountColor = if (isOwed) Color(0xFFC62828) else Color(0xFF2E7D32)
                    Text(
                        text = "${formatAmount(kotlin.math.abs(bal))} $currency",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Transaction Count
        Box(
            modifier = Modifier
                .size(36.dp, 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LightBlueBubble),
            contentAlignment = Alignment.Center
        ) {
            Text("${account.transactionCount}", color = Color.White, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Name
        Text(
            text = account.accountName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Add Button
        IconButton(onClick = onAddClick) {
            Icon(Icons.Default.Add, "إضافة", tint = PrimaryBlue, modifier = Modifier.size(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (TransactionType, Double, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(TransactionType.ON_HIM) } // Red, عليه
    var amount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initialName) }
    var details by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("محلي") }
    var imageUriStr by remember { mutableStateOf("") }
    
    var phone by remember { mutableStateOf("") }
    
    var showCalcDialog by remember { mutableStateOf(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri ?: return@rememberLauncherForActivityResult
        try {
            val contactProjection = arrayOf(
                android.provider.ContactsContract.Contacts._ID,
                android.provider.ContactsContract.Contacts.DISPLAY_NAME,
                android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            context.contentResolver.query(contactUri, contactProjection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.Contacts._ID))
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.Contacts.DISPLAY_NAME))
                    val hasPhone = cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER))
                    
                    name = displayName ?: ""
                    
                    if (hasPhone > 0) {
                        val phoneProjection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        context.contentResolver.query(
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            phoneProjection,
                            "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id.toString()),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                phone = phoneCursor.getString(
                                    phoneCursor.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                               ) ?: ""
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            try {
                contactPickerLauncher.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            android.widget.Toast.makeText(context, "تم رفض الإذن للوصول إلى جهات الإتصال.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val file = java.io.File(context.cacheDir, "img_${System.currentTimeMillis()}.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            imageUriStr = file.absolutePath
        }
    }

    val currentDate = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("إضافة مبلغ", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBlue)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Name and contacts
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val permStatus = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
                        if (permStatus == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            try {
                                contactPickerLauncher.launch(null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            requestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                        }
                    }) {
                        Icon(Icons.Default.Contacts, "جهات الاتصال", tint = PrimaryBlue)
                    }
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("الإسم", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Phone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (phone.isNotBlank() && name.isNotBlank()) {
                        IconButton(onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                    setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE)
                                    putExtra(android.provider.ContactsContract.Intents.Insert.NAME, name)
                                    putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, phone)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }) {
                            Icon(Icons.Default.PersonAdd, "حفظ في جهات اتصال الهاتف", tint = PrimaryBlue)
                        }
                    }
                    TextField(
                        value = phone,
                        onValueChange = { phone = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        placeholder = { Text("رقم التلفون (مطلوب)", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount and calc
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCalcDialog = true }) { Icon(Icons.Default.Calculate, "حاسبة", tint = PrimaryBlue) }
                    
                    TextField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = { Text("المبلغ", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Details
                TextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = { Text("التفاصيل", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Currency Radio Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("محلي")
                    RadioButton(selected = currency == "محلي", onClick = { currency = "محلي" })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("دولار")
                    RadioButton(selected = currency == "دولار", onClick = { currency = "دولار" })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("سعودي")
                    RadioButton(selected = currency == "سعودي", onClick = { currency = "سعودي" })
                }

                Spacer(modifier = Modifier.height(16.dp))

                // له / عليه Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = { type = TransactionType.ON_HIM },
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == TransactionType.ON_HIM) Color(0xFF283593) else Color.LightGray),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("عليه", color = Color.White)
                        Icon(Icons.Default.ArrowDropDown, "عليه", tint = RedTriangle)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { type = TransactionType.FOR_HIM },
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == TransactionType.FOR_HIM) Color(0xFF283593) else Color.LightGray),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("له", color = Color.White)
                        Icon(Icons.Default.ArrowDropUp, "له", tint = GreenTriangle)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(" $currentDate ", color = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(onClick = { cameraLauncher.launch(null) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "صورة", tint = if (imageUriStr.isNotBlank()) PrimaryBlue else Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val parsedAmount = amount.toDoubleOrNull() ?: evaluateMathExpression(amount)
                        if (!parsedAmount.isNaN() && parsedAmount > 0 && name.isNotBlank() && phone.isNotBlank()) {
                            onSave(type, parsedAmount, name, details, currency, imageUriStr, phone)
                        } else {
                            // Can show error (e.g. required phone)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("حفظ", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
    
    if (showCalcDialog) {
        var calcInput by remember { mutableStateOf(amount) }
        AlertDialog(
            onDismissRequest = { showCalcDialog = false },
            title = { Text("الحاسبة") },
            text = {
                Column {
                    TextField(
                        value = calcInput,
                        onValueChange = { calcInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, textAlign = TextAlign.End)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val calcButtons = listOf(
                        listOf("7", "8", "9", "+"),
                        listOf("4", "5", "6", "-"),
                        listOf("1", "2", "3", "*"),
                        listOf("C", "0", ".", "/")
                    )
                    calcButtons.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { btn ->
                                Button(
                                    onClick = {
                                        if (btn == "C") calcInput = ""
                                        else calcInput += btn
                                    },
                                    modifier = Modifier.weight(1f).padding(4.dp)
                                ) {
                                    Text(btn, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val res = evaluateMathExpression(calcInput)
                            if (!res.isNaN()) {
                                calcInput = if (res % 1 == 0.0) res.toLong().toString() else res.toString()
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(4.dp)
                    ) { Text("=") }
                }
            },
            confirmButton = { 
                Button(onClick = { 
                    val res = evaluateMathExpression(calcInput)
                    amount = if (!res.isNaN()) {
                         if (res % 1 == 0.0) res.toLong().toString() else res.toString()
                    } else calcInput
                    showCalcDialog = false 
                }) { Text("موافق") } 
            }
        )
    }
}

fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        String.format(java.util.Locale.US, "%,d", amount.toLong())
    } else {
        String.format(java.util.Locale.US, "%,.2f", amount)
    }
}

fun getContactName(context: Context, contactUri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(contactUri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}

fun getContactNameAndPhone(context: Context, contactUri: Uri): Pair<String?, String?> {
    var name: String? = null
    var phone: String? = null
    var contactId: String? = null
    
    try {
        val cursor = context.contentResolver.query(contactUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                val hasPhoneIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                
                if (nameIndex != -1) name = it.getString(nameIndex)
                if (idIndex != -1) contactId = it.getString(idIndex)
                
                val hasPhone = hasPhoneIndex != -1 && it.getInt(hasPhoneIndex) > 0
                
                if (hasPhone && contactId != null) {
                    val phones = context.contentResolver.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(contactId),
                        null
                    )
                    phones?.use { pCursor ->
                        if (pCursor.moveToFirst()) {
                            val numIndex = pCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numIndex != -1) {
                                phone = pCursor.getString(numIndex)
                            }
                        }
                    }
                }
            }
        }
    } catch (e: SecurityException) {
        // Fallback for missing permission
        e.printStackTrace()
    }
    return Pair(name, phone)
}

