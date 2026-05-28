package com.example.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.Transaction
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()

    // Language Toggling State
    var isEnglish by remember { mutableStateOf(sharedPrefs.getString("app_lang", "ar") == "en") }

    // Screen State: "main", "personal_info", "print_options", "security_options", "categories", "currencies", "backup_options", "computer_browse", "notifications", "other"
    var currentSubScreen by remember { mutableStateOf("main") }

    // State Variables
    var personalName by remember { mutableStateOf(sharedPrefs.getString("personal_name", "") ?: "") }
    var personalAddress by remember { mutableStateOf(sharedPrefs.getString("personal_address", "") ?: "") }
    var personalPhone by remember { mutableStateOf(sharedPrefs.getString("personal_phone", "") ?: "") }
    var personalLogoUri by remember { mutableStateOf(sharedPrefs.getString("personal_logo_uri", "") ?: "") }
    
    var personalNameEn by remember { mutableStateOf(sharedPrefs.getString("personal_name_en", "") ?: "") }
    var personalAddressEn by remember { mutableStateOf(sharedPrefs.getString("personal_address_en", "") ?: "") }
    var receiptFooter by remember { mutableStateOf(sharedPrefs.getString("receipt_footer", "نموذج المصادقة على الحساب") ?: "نموذج المصادقة على الحساب") }

    // Print Options States
    var showPersonalDataInPrint by remember { mutableStateOf(sharedPrefs.getBoolean("print_personal_data", true)) }
    var showDateInPrint by remember { mutableStateOf(sharedPrefs.getBoolean("print_show_date", true)) }
    var printSortAscending by remember { mutableStateOf(sharedPrefs.getBoolean("print_sort_ascending", true)) }
    var printAllCurrencies by remember { mutableStateOf(sharedPrefs.getBoolean("print_all_currencies", false)) }
    var printBottomNote by remember { mutableStateOf(sharedPrefs.getString("print_bottom_note", "نشكر حسن تعاملكم معنا") ?: "نشكر حسن تعاملكم معنا") }
    var labelDebtor by remember { mutableStateOf(sharedPrefs.getString("label_debtor", "عليه") ?: "عليه") }
    var labelCreditor by remember { mutableStateOf(sharedPrefs.getString("label_creditor", "له") ?: "له") }

    // Security States
    var usePassword by remember { mutableStateOf(sharedPrefs.getBoolean("use_password", false)) }
    var password by remember { mutableStateOf(sharedPrefs.getString("password", "") ?: "") }

    // Backup States
    var saveDaily by remember { mutableStateOf(sharedPrefs.getBoolean("daily_backup", true)) }
    var backupFolder by remember { mutableStateOf(sharedPrefs.getString("backup_folder", "/storage/emulated/0/Documents/Market_Customers/2026/") ?: "/storage/emulated/0/Documents/Market_Customers/2026/") }
    var backupImages by remember { mutableStateOf(sharedPrefs.getBoolean("backup_images", false)) }
    var backupTimeText by remember { mutableStateOf(sharedPrefs.getString("backup_time", "02:30 م") ?: "02:30 م") }
    var syncGoogleAccount by remember { mutableStateOf(sharedPrefs.getString("recovery_email", "") ?: "") }
    var syncFolderName by remember { mutableStateOf(sharedPrefs.getString("sync_folder_name", "دفتر الحسابات") ?: "دفتر الحسابات") }
    var showNotificationOnAdd by remember { mutableStateOf(sharedPrefs.getBoolean("show_notification_on_add", true)) }

    // Notifications States
    var generalNotifications by remember { mutableStateOf(sharedPrefs.getBoolean("general_notifications", true)) }
    var notifyOnBackupSuccess by remember { mutableStateOf(sharedPrefs.getBoolean("notify_on_backup_success", true)) }

    // Classifications/Categories States
    var categories by remember {
        mutableStateOf(
            sharedPrefs.getString("categories_list", "عام,عملاء,موردين")
                ?.split(",")
                ?.filter { it.isNotBlank() } ?: listOf("عام", "عملاء", "موردين")
        )
    }

    // Currencies States
    var currencies by remember {
        mutableStateOf(
            sharedPrefs.getString("currencies_list", "محلي,دولار,ريال سعودي,ريال يمني")
                ?.split(",")
                ?.filter { it.isNotBlank() } ?: listOf("محلي", "دولار", "ريال سعودي", "ريال يمني")
        )
    }

    // Load Transactions from database to calculate real accounts summaries
    val transactionsFlow = remember { AppDatabase.getDatabase(context).transactionDao().getAllTransactions() }
    val transactions by transactionsFlow.collectAsState(initial = emptyList())

    // Calculations of categories dynamically
    val categoryCountMap = remember(transactions) {
        val distinctAccounts = transactions.map { it.accountName }.distinct()
        distinctAccounts.groupBy { accountName ->
            sharedPrefs.getString("account_category_${accountName}", "عام") ?: "عام"
        }.mapValues { it.value.size }
    }

    // Editing Dialog state
    var editDialogTitle by remember { mutableStateOf<String?>(null) }
    var editDialogKey by remember { mutableStateOf("") }
    var editDialogValue by remember { mutableStateOf("") }

    // Passcode Change Dialog state
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var oldPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }

    // New Category Dialog state
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var customCategoryInput by remember { mutableStateOf("") }

    // New Currency Dialog state
    var showNewCurrencyDialog by remember { mutableStateOf(false) }
    var customCurrencyInput by remember { mutableStateOf("") }

    // General App Bar colors
    val primaryColor = Color(0xFF3F51B5)
    val appHeaderColor = Color(0xFF3949AB)

    // Standard Image Picker launcher
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            personalLogoUri = uri.toString()
            sharedPrefs.edit().putString("personal_logo_uri", uri.toString()).apply()
            Toast.makeText(context, if (isEnglish) "Logo updated successfully" else "تم حفظ الشعار الجديد بنجاح", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEnglish) {
                            when (currentSubScreen) {
                                "main" -> "Settings"
                                "personal_info" -> "Personal Information"
                                "print_options" -> "Print Options"
                                "security_options" -> "Security Options"
                                "categories" -> "Categories"
                                "currencies" -> "Currencies"
                                "backup_options" -> "Backup Options"
                                "computer_browse" -> "Browse from PC"
                                "notifications" -> "Notifications"
                                "other" -> "Other Options"
                                else -> "Settings"
                            }
                        } else {
                            if (currentSubScreen == "categories") "التصنيفات" else "إعدادات"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSubScreen == "main") {
                            onBack()
                        } else {
                            currentSubScreen = "main"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        isEnglish = !isEnglish
                        sharedPrefs.edit().putString("app_lang", if (isEnglish) "en" else "ar").apply()
                        Toast.makeText(context, if (isEnglish) "Language: English" else "اللغة المعتمدة: العربية", Toast.LENGTH_SHORT).show()
                    }) {
                        Text(
                            text = if (isEnglish) "العربية" else "CHANGE LANG E",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appHeaderColor)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            when (currentSubScreen) {
                // ================== MAIN MENU ==================
                "main" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsMenuRow(
                            icon = "📝",
                            title = if (isEnglish) "Personal Info" else "البيانات الشخصية",
                            subtitle = if (isEnglish) "Name, address, phone number and logo" else "الاسم، العنوان، رقم الجوال وتغيير الشعار",
                            onClick = { currentSubScreen = "personal_info" }
                        )
                        SettingsMenuRow(
                            icon = "🖨️",
                            title = if (isEnglish) "Print Options" else "خيارات الطباعة",
                            subtitle = if (isEnglish) "Customize statement layout and print order" else "تحكم في إظهار البيانات، التاريخ، والطباعة التصاعدية",
                            onClick = { currentSubScreen = "print_options" }
                        )
                        SettingsMenuRow(
                            icon = "🔒",
                            title = if (isEnglish) "Security Options" else "خيارات الأمان",
                            subtitle = if (isEnglish) "App locks and secure password setup" else "خيارات تفعيل كلمة المرور وحماية خصوصية الحسابات",
                            onClick = { currentSubScreen = "security_options" }
                        )
                        SettingsMenuRow(
                            icon = "🗂️",
                            title = if (isEnglish) "Categories" else "التصنيفات",
                            subtitle = if (isEnglish) "Define and check categorized accounts count" else "إدارة تصنيفات العملاء والموردين وتوزيع الحسابات",
                            onClick = { currentSubScreen = "categories" }
                        )
                        SettingsMenuRow(
                            icon = "💵",
                            title = if (isEnglish) "Currencies" else "العملات",
                            subtitle = if (isEnglish) "Manage currency units and custom notes" else "إعداد وإدارة العملات الأساسية المستخدمة في الحسابات",
                            onClick = { currentSubScreen = "currencies" }
                        )
                        SettingsMenuRow(
                            icon = "💾",
                            title = if (isEnglish) "Backup Settings" else "خيارات حفظ البيانات",
                            subtitle = if (isEnglish) "Automated backup folders and cloud drive sync" else "المزامنة مع جوجل درايف والنسخ الاحتياطي التلقائي اليومي",
                            onClick = { currentSubScreen = "backup_options" }
                        )
                        SettingsMenuRow(
                            icon = "💻",
                            title = if (isEnglish) "Browse data from PC" else "إستعراض البيانات من الكمبيوتر",
                            subtitle = if (isEnglish) "View, inspect or export data on desktop directly" else "تعليمات تصفح وفتح التقارير والملفات من الحاسوب الشخصي",
                            onClick = { currentSubScreen = "computer_browse" }
                        )
                        SettingsMenuRow(
                            icon = "✉️",
                            title = if (isEnglish) "Notification Options" else "خيارات الإشعارات",
                            subtitle = if (isEnglish) "Transaction alerts and backup updates" else "تخصيص الإشعارات عند إضافة رصيد أو مزامنة النسخ",
                            onClick = { currentSubScreen = "notifications" }
                        )
                        SettingsMenuRow(
                            icon = "🔧",
                            title = if (isEnglish) "Other options" else "خيارات أخرى",
                            subtitle = if (isEnglish) "Database purge and master summaries" else "مسح البيانات بالكامل أو تصدير التقارير الإجمالية الشاملة",
                            onClick = { currentSubScreen = "other" }
                        )
                    }
                }

                // ================== PERSONAL INFORMATION ==================
                "personal_info" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Centered Stack Logo
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8EAF6))
                                .clickable { logoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (personalLogoUri.isNotEmpty()) {
                                AsyncImage(
                                    model = personalLogoUri,
                                    contentDescription = "Logo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("💵", fontSize = 42.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isEnglish) "Corporate Identity" else "هوية كشف الحساب والنشاط",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFEEEEEE))

                        SettingsRow(
                            icon = "👤",
                            title = if (isEnglish) "Arabic Name" else "الإسم",
                            subtitle = personalName.ifBlank { if (isEnglish) "Not Specified" else "لا يوجد اسم شركة مسجل" },
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Corporate Name (AR)" else "تعديل الاسم (بالعربية)"
                                editDialogKey = "personal_name"
                                editDialogValue = personalName
                            }
                        )
                        SettingsRow(
                            icon = "🏠",
                            title = if (isEnglish) "Arabic Address" else "العنوان",
                            subtitle = personalAddress.ifBlank { if (isEnglish) "Not Specified" else "العنوان" },
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Address (AR)" else "تعديل العنوان (بالعربية)"
                                editDialogKey = "personal_address"
                                editDialogValue = personalAddress
                            }
                        )
                        SettingsRow(
                            icon = "📞",
                            title = if (isEnglish) "Phone Number" else "رقم التلفون",
                            subtitle = personalPhone.ifBlank { if (isEnglish) "Not Specified" else "رقم التلفون" },
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Phone Number" else "تعديل رقم التلفون"
                                editDialogKey = "personal_phone"
                                editDialogValue = personalPhone
                            }
                        )
                        SettingsRow(
                            icon = "🖼️",
                            title = if (isEnglish) "Change Logo" else "تغيير الشعار",
                            subtitle = if (isEnglish) "Click to pick new logo" else "تغيير الشعار الخاص بك",
                            onClick = { logoPickerLauncher.launch("image/*") }
                        )
                        SettingsRow(
                            icon = "👤",
                            title = if (isEnglish) "English Name" else "Name",
                            subtitle = personalNameEn.ifBlank { if (isEnglish) "Not Specified" else "اسم الشركة بالإنجليزية" },
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Corporate Name (EN)" else "تعديل الاسم (بالإنجليزية)"
                                editDialogKey = "personal_name_en"
                                editDialogValue = personalNameEn
                            }
                        )
                        SettingsRow(
                            icon = "🏠",
                            title = if (isEnglish) "English Address" else "Address",
                            subtitle = personalAddressEn.ifBlank { if (isEnglish) "Not Specified" else "العنوان بالإنجليزية" },
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Address (EN)" else "تعديل العنوان (بالإنجليزية)"
                                editDialogKey = "personal_address_en"
                                editDialogValue = personalAddressEn
                            }
                        )
                        SettingsRow(
                            icon = "📝",
                            title = if (isEnglish) "Statement Auth Template Footer" else "نموذج المصادقة على الحساب",
                            subtitle = receiptFooter,
                            onClick = {
                                editDialogTitle = if (isEnglish) "Authentication Footer Template" else "تعديل كود تذييل المصادقة الكشف"
                                editDialogKey = "receipt_footer"
                                editDialogValue = receiptFooter
                            }
                        )
                    }
                }

                // ================== PRINT OPTIONS ==================
                "print_options" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsSwitchRow(
                            icon = "📝",
                            title = if (isEnglish) "Show Personal Data" else "إظهار البيانات",
                            subtitle = if (isEnglish) "Toggle personal corporate data on statement prints" else "طباعة البيانات الشخصية والشعار على الوصولات",
                            checked = showPersonalDataInPrint,
                            onCheckedChange = {
                                showPersonalDataInPrint = it
                                sharedPrefs.edit().putBoolean("print_personal_data", it).apply()
                            }
                        )
                        SettingsSwitchRow(
                            icon = "📅",
                            title = if (isEnglish) "Show Date" else "إظهار التاريخ",
                            subtitle = if (isEnglish) "Print current operations date/timestamps" else "إظهار التاريخ والوقت أسفل الوصولات والتقارير المطبوعة",
                            checked = showDateInPrint,
                            onCheckedChange = {
                                showDateInPrint = it
                                sharedPrefs.edit().putBoolean("print_show_date", it).apply()
                            }
                        )
                        SettingsSwitchRow(
                            icon = "az",
                            title = if (isEnglish) "Sort Print Ascending" else "طباعة البيانات تصاعديا",
                            subtitle = if (isEnglish) "Chronological sort: oldest transactions first" else "فرز المعاملات من التاريخ الأقدم للأحدث عند التصدير",
                            checked = printSortAscending,
                            onCheckedChange = {
                                printSortAscending = it
                                sharedPrefs.edit().putBoolean("print_sort_ascending", it).apply()
                            }
                        )
                        SettingsSwitchRow(
                            icon = "🪙",
                            title = if (isEnglish) "All Currencies Statement" else "طباعة الكشف بجميع العملات",
                            subtitle = if (isEnglish) "Include all local & foreign currencies together" else "طباعة كافة الحسابات بمختلف العملات في كشف تقرير جامع",
                            checked = printAllCurrencies,
                            onCheckedChange = {
                                printAllCurrencies = it
                                sharedPrefs.edit().putBoolean("print_all_currencies", it).apply()
                            }
                        )
                        SettingsRow(
                            icon = "📝",
                            title = if (isEnglish) "Invoice Bottom Note" else "إضافة ملاحظة أسفل الكشف",
                            subtitle = printBottomNote,
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Printed Statement Note" else "تعديل الملاحظة القانونية أسفل كشف الحساب والوصول"
                                editDialogKey = "print_bottom_note"
                                editDialogValue = printBottomNote
                            }
                        )
                        SettingsRow(
                            icon = "🔻",
                            title = if (isEnglish) "Debit Title Label" else "مدين",
                            subtitle = labelDebtor,
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Debit (Owed by client) label" else "تعديل مسمى تصنيف (مدين / المطلوب منه)"
                                editDialogKey = "label_debtor"
                                editDialogValue = labelDebtor
                            }
                        )
                        SettingsRow(
                            icon = "🔺",
                            title = if (isEnglish) "Credit Title Label" else "دائن",
                            subtitle = labelCreditor,
                            onClick = {
                                editDialogTitle = if (isEnglish) "Edit Credit (Owed to client) label" else "تعديل مسمى تصنيف (دائن / المستحق له)"
                                editDialogKey = "label_creditor"
                                editDialogValue = labelCreditor
                            }
                        )
                    }
                }

                // ================== SECURITY OPTIONS ==================
                "security_options" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsSwitchRow(
                            icon = "🔑",
                            title = if (isEnglish) "Enable Passcode Lock" else "تفعيل كلمة السر",
                            subtitle = if (isEnglish) "Validate security passcode upon launching app" else "طلب كلمة السر المخصصة لحماية خصوصية بياناتك عند الدخول",
                            checked = usePassword,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showPasswordChangeDialog = true
                                } else {
                                    // Turn off password with safe confirmation mapping
                                    usePassword = false
                                    sharedPrefs.edit().putBoolean("use_password", false).apply()
                                }
                            }
                        )
                        SettingsRow(
                            icon = "⌨️",
                            title = if (isEnglish) "Passcode Lock" else "كلمة السر",
                            subtitle = if (usePassword) "******" else (if (isEnglish) "Disabled" else "مغلق"),
                            onClick = { showPasswordChangeDialog = true },
                            enabled = true
                        )
                    }
                }

                // ================== CATEGORIES ==================
                "categories" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 72.dp)
                        ) {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(primaryColor)
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEnglish) "Account Count" else "عدد الحسابات",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (isEnglish) "Category" else "التصنيف",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            
                            // Category Rows listing
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                categories.forEach { categoryName ->
                                    val count = categoryCountMap[categoryName] ?: 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp, horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = categoryName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                }
                            }
                        }
                        
                        // Styled Middle Floating Plus Action Button inside bottom area matching exact screenshot
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(primaryColor)
                                .align(Alignment.BottomCenter)
                                .clickable { showNewCategoryDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Add classification",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // ================== CURRENCIES ==================
                "currencies" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 72.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(primaryColor)
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEnglish) "Unit" else "مكتمل",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (isEnglish) "Monetary Currency" else "العملة المالية",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                currencies.forEach { currency ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp, horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = Color(0xFF4CAF50)
                                        )
                                        Text(
                                            text = currency,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(primaryColor)
                                .align(Alignment.BottomCenter)
                                .clickable { showNewCurrencyDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Add monetary currency",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // ================== BACKUP SETTINGS ==================
                "backup_options" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsSwitchRow(
                            icon = "⏰",
                            title = if (isEnglish) "Daily Automated Backup" else "حفظ البيانات يوميا",
                            subtitle = if (isEnglish) "Schedule backup tasks once every 24 hours automatically" else "حفظ البيانات تلقائيا مرة واحدة باليوم (بشرط تغير البيانات)",
                            checked = saveDaily,
                            onCheckedChange = {
                                saveDaily = it
                                sharedPrefs.edit().putBoolean("daily_backup", it).apply()
                            }
                        )
                        SettingsRow(
                            icon = "📂",
                            title = if (isEnglish) "Backup Folder Directory" else "مجلد حفظ البيانات",
                            subtitle = backupFolder,
                            onClick = {
                                editDialogTitle = if (isEnglish) "Backup Folder Destination" else "تعديل مسار مجلد تخزين النسخة الاحتياطية محلياً"
                                editDialogKey = "backup_folder"
                                editDialogValue = backupFolder
                            }
                        )
                        SettingsSwitchRow(
                            icon = "📸",
                            title = if (isEnglish) "Include Images" else "إضافة الصور",
                            subtitle = if (isEnglish) "Compress and back up receipts images" else "تضمين الصور الفوتوغرافية للوصولات والفواتير بالنسخة الاحتياطية",
                            checked = backupImages,
                            onCheckedChange = {
                                backupImages = it
                                sharedPrefs.edit().putBoolean("backup_images", it).apply()
                            }
                        )
                        SettingsRow(
                            icon = "🕒",
                            title = if (isEnglish) "Weekly Backup Time" else "وقت حفظ البيانات",
                            subtitle = backupTimeText,
                            onClick = {
                                val cal = Calendar.getInstance()
                                TimePickerDialog(
                                    context,
                                    { _, hr, min ->
                                        val amPm = if (hr >= 12) (if (isEnglish) "PM" else "م") else (if (isEnglish) "AM" else "ص")
                                        val displayHr = if (hr % 12 == 0) 12 else hr % 12
                                        backupTimeText = String.format(Locale.getDefault(), "%02d:%02d %s", displayHr, min, amPm)
                                        sharedPrefs.edit().putString("backup_time", backupTimeText).apply()
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                        )
                        SettingsRow(
                            icon = "☁️",
                            title = if (isEnglish) "Change Google Cloud Backup" else "تغيير الحساب",
                            subtitle = syncGoogleAccount.ifBlank { if (isEnglish) "No Account Configured" else "اضغط لربط حساب جوجل السحابي للمزامنة" },
                            onClick = {
                                editDialogTitle = if (isEnglish) "Cloud Recovery Email" else "تعديل البريد الإلكتروني للنسخة الاحتياطية والتحقق السحابي"
                                editDialogKey = "recovery_email"
                                editDialogValue = syncGoogleAccount
                            }
                        )
                        SettingsRow(
                            icon = "📂",
                            title = if (isEnglish) "Google Drive Folder Name" else "اسم المجلد في جوجل درايف",
                            subtitle = syncFolderName,
                            onClick = {
                                editDialogTitle = if (isEnglish) "Google Drive Backup Folder" else "تعديل اسم مجلد حفظ التقارير السحابية في قوقل درايف"
                                editDialogKey = "sync_folder_name"
                                editDialogValue = syncFolderName
                            }
                        )
                        SettingsSwitchRow(
                            icon = "🔔",
                            title = if (isEnglish) "Show Notification on Receipt Addition" else "إظهار الإشعار عند إضافة مبلغ",
                            subtitle = if (isEnglish) "Toast push system notice automatically" else "إظهار الإشعار السريع في شريط التنبيهات فور تسجيل أي رصيد مالي معتمد",
                            checked = showNotificationOnAdd,
                            onCheckedChange = {
                                showNotificationOnAdd = it
                                sharedPrefs.edit().putBoolean("show_notification_on_add", it).apply()
                            }
                        )
                    }
                }

                // ================== BROWSE DATA FROM COMPUTER ==================
                "computer_browse" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("💻", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isEnglish) "Access Data on PC" else "فتح واستعراض البيانات من الكمبيوتر",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isEnglish) {
                                "To inspect, print or manage your client balances on spreadsheet softwares/Excel from any desktop device:\n\n" +
                                "1. Connect your Android device to your computer via USB cable.\n\n" +
                                "2. Choose 'File Transfer / Android Auto' option on your device notification panel.\n\n" +
                                "3. Open your computer explorer, navigate to your internal memory folder:\n" +
                                "   Documents / Market_Customers\n\n" +
                                "4. Simply open generated Excel/CSV files directly using Microsoft Excel, Google Sheets, or LibreOffice!"
                            } else {
                                "لتسهيل طباعة البيانات وتصفحها ومراجعة كشوفات عملائك من شاشة الكمبيوتر الكبيرة:\n\n" +
                                "1. قم بتوصيل هاتف الجوال الخاص بك بالحاسوب الشخصي بواسطة سلك الشحن USB الموثق.\n\n" +
                                "2. اختر خيار 'نقل الملفات / File Transfer' من إشعار إعدادات منفصل السلك المنبثق على الجوال.\n\n" +
                                "3. افتح متصفح كمبيوتر الحاسب، وتوجه للذاكرة الداخلية للهاتف، وقم بلدخول للمجلد المالي:\n" +
                                "   Documents / Market_Customers\n\n" +
                                "4. ستجد جميع ملفات كشف الحساب والنسخ مفرزة بصيغ Excel / CSV القابلة للمطالعة والتحرير الفوري!"
                            },
                            fontSize = 15.sp,
                            color = Color.DarkGray,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Start
                        )
                    }
                }

                // ================== NOTIFICATIONS ==================
                "notifications" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsSwitchRow(
                            icon = "🔔",
                            title = if (isEnglish) "App Alerts Notifications" else "تنبيهات النظام الأساسية",
                            subtitle = if (isEnglish) "Enable general in-app alerts and security warnings" else "تفعيل الإشعارات وتنبيهات أمان قفل الحساب",
                            checked = generalNotifications,
                            onCheckedChange = {
                                generalNotifications = it
                                sharedPrefs.edit().putBoolean("general_notifications", it).apply()
                            }
                        )
                        SettingsSwitchRow(
                            icon = "🔄",
                            title = if (isEnglish) "Notify on Backup Success" else "تنبيه عند نجاح المزامنة",
                            subtitle = if (isEnglish) "Show notification upon successfully uploading/syncing database" else "إظهار إشعار تأكيد فوري عند اتمام المزامنة السحابية بنجاح",
                            checked = notifyOnBackupSuccess,
                            onCheckedChange = {
                                notifyOnBackupSuccess = it
                                sharedPrefs.edit().putBoolean("notify_on_backup_success", it).apply()
                            }
                        )
                    }
                }

                // ================== OTHER OPTIONS ==================
                "other" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("🔧", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                val pm = context.packageManager
                                val url = "https://play.google.com/store"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Play Store not found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .height(50.dp)
                        ) {
                            Text(if (isEnglish) "Rate Application" else "تقييم التطبيق على المتجر 🌟", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Danger zone delete database logic
                        var showConfirmPurgeDialog by remember { mutableStateOf(false) }
                        if (showConfirmPurgeDialog) {
                            AlertDialog(
                                onDismissRequest = { showConfirmPurgeDialog = false },
                                title = { Text(if (isEnglish) "Master Purge Data?" else "تنبيه: مسح كافة الحسابات والبيانات؟") },
                                text = { Text(if (isEnglish) "This action is final and irreversible. All balances and transactions will be deleted." else "انتبه: سيتم حذف كافة العمليات، المستندات، والصور، ولن تستطيع استعادتها محلياً مطلقاً بدون نسخة احتياطية!") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showConfirmPurgeDialog = false
                                        // Purge all data from db using Coroutine Scope
                                        coroutineScope.launch {
                                            try {
                                                AppDatabase.getDatabase(context).transactionDao().deleteAllTransactions()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        sharedPrefs.edit().clear().apply()
                                        Toast.makeText(context, if (isEnglish) "All Data wiped out!" else "تم مسح كافة البيانات وصفر الحسابات بنجاح!", Toast.LENGTH_LONG).show()
                                        onBack()
                                    }) {
                                        Text(if (isEnglish) "Yes, Delete Everything" else "نعم، حذف نهائي", color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmPurgeDialog = false }) {
                                        Text(if (isEnglish) "Cancel" else "إلغاء")
                                    }
                                }
                            )
                        }

                        Button(
                            onClick = { showConfirmPurgeDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .height(50.dp)
                        ) {
                            Text(if (isEnglish) "Purge All Data & Reset" else "مسح وتصفير كافة البيانات نهائياً 🧹", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ================== EDITING ALERT DIALOG ==================
    if (editDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { editDialogTitle = null },
            title = {
                Text(
                    text = editDialogTitle!!,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = editDialogValue,
                    onValueChange = { editDialogValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPrefs.edit().putString(editDialogKey, editDialogValue).apply()
                        // Synchronize variables
                        when (editDialogKey) {
                            "personal_name" -> personalName = editDialogValue
                            "personal_address" -> personalAddress = editDialogValue
                            "personal_phone" -> personalPhone = editDialogValue
                            "personal_name_en" -> personalNameEn = editDialogValue
                            "personal_address_en" -> personalAddressEn = editDialogValue
                            "receipt_footer" -> receiptFooter = editDialogValue
                            "print_bottom_note" -> printBottomNote = editDialogValue
                            "label_debtor" -> labelDebtor = editDialogValue
                            "label_creditor" -> labelCreditor = editDialogValue
                            "backup_folder" -> backupFolder = editDialogValue
                            "recovery_email" -> syncGoogleAccount = editDialogValue
                            "sync_folder_name" -> syncFolderName = editDialogValue
                        }
                        editDialogTitle = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(if (isEnglish) "Save" else "موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogTitle = null }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }

    // ================== PASSWORD CHANGE DIALOG (Image 5) ==================
    if (showPasswordChangeDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordChangeDialog = false },
            title = {
                Text(
                    text = if (isEnglish) "Change Password" else "تغيير كلمة السر",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (usePassword && password.isNotEmpty()) {
                        Text(
                            text = if (isEnglish) "Old Password" else "كلمة السر القديمة",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                        OutlinedTextField(
                            value = oldPasswordInput,
                            onValueChange = { oldPasswordInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            TextButton(
                                onClick = {
                                    val email = sharedPrefs.getString("recovery_email", "") ?: ""
                                    if (email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                        Toast.makeText(context, if (isEnglish) "Sending recovery email..." else "جاري إرسال الإيميل...", Toast.LENGTH_SHORT).show()
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            val verificationCode = com.example.ui.EmailRecoveryHelper.generateVerificationCode()
                                            sharedPrefs.edit().putString("recovery_code", verificationCode).apply()
                                            val result = com.example.ui.EmailRecoveryHelper.sendRecoveryEmail(email, verificationCode)
                                            kotlinx.coroutines.Dispatchers.Main.let {
                                                if (result.isSuccess) {
                                                    Toast.makeText(context, if (isEnglish) "Code sent to $email" else "تم الإرسال: $email", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, if (isEnglish) "Failed to send email" else "فشل إرسال الإيميل", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, if (isEnglish) "No valid recovery email found." else "بريد الاسترداد غير صالح.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) {
                                Text(if (isEnglish) "Forgot Password?" else "هل نسيت الرمز؟", color = primaryColor)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = if (isEnglish) "New Password (Numbers Only)" else "كلمة السر الجديدة (أرقام فقط)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isEnglish) "Confirm New Password" else "تأكيد كلمة السر الجديدة",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = { confirmPasswordInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (usePassword && password.isNotEmpty()) {
                            val savedCode = sharedPrefs.getString("recovery_code", null)
                            val isRecoveryCode = savedCode != null && oldPasswordInput == savedCode
                            if (oldPasswordInput != password && !isRecoveryCode) {
                                Toast.makeText(context, if (isEnglish) "Incorrect old password" else "كلمة السر القديمة خاطئة!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (isRecoveryCode) {
                                // Once used, clear the recovery code
                                sharedPrefs.edit().remove("recovery_code").apply()
                            }
                        }
                        if (newPasswordInput.isEmpty()) {
                            Toast.makeText(context, if (isEnglish) "Password cannot be empty" else "الرجاء إدخال رمز صحيح!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPasswordInput != confirmPasswordInput) {
                            Toast.makeText(context, if (isEnglish) "Passwords do not match" else "تأكيد كلمة السر غير متطابق!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Save passcode lock
                        password = newPasswordInput
                        usePassword = true
                        sharedPrefs.edit()
                            .putBoolean("use_password", true)
                            .putString("password", newPasswordInput)
                            .apply()
                        Toast.makeText(context, if (isEnglish) "Password set successfully" else "تم تأمين التطبيق وحفظ الرمز السري بنجاح", Toast.LENGTH_SHORT).show()
                        showPasswordChangeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(if (isEnglish) "Save" else "موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordChangeDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }

    // ================== NEW CATEGORY DIALOG (Image 7 + button logic) ==================
    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = {
                Text(
                    text = if (isEnglish) "Add Classification/Category" else "إضافة تصنيف جديد",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = customCategoryInput,
                    onValueChange = { customCategoryInput = it },
                    placeholder = { Text(if (isEnglish) "e.g. Partners, Inquiries..." else "مثال: شركاء، جهات خارجية...") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customCategoryInput.isNotBlank()) {
                            val newCat = customCategoryInput.trim()
                            if (!categories.contains(newCat)) {
                                val newList = categories + newCat
                                categories = newList
                                sharedPrefs.edit().putString("categories_list", newList.joinToString(",")).apply()
                                Toast.makeText(context, if (isEnglish) "Category added" else "تمت إضافة التصنيف الجديد بنجاح", Toast.LENGTH_SHORT).show()
                            }
                            customCategoryInput = ""
                            showNewCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(if (isEnglish) "OK" else "موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }

    // ================== NEW CURRENCY DIALOG ==================
    if (showNewCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showNewCurrencyDialog = false },
            title = {
                Text(
                    text = if (isEnglish) "Add Monetary Currency" else "إضافة عملة مالية جديدة",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = customCurrencyInput,
                    onValueChange = { customCurrencyInput = it },
                    placeholder = { Text(if (isEnglish) "e.g. Euro, Sterling..." else "مثال: يورو، درهم، دينار...") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 16.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customCurrencyInput.isNotBlank()) {
                            val newCurr = customCurrencyInput.trim()
                            if (!currencies.contains(newCurr)) {
                                val newList = currencies + newCurr
                                currencies = newList
                                sharedPrefs.edit().putString("currencies_list", newList.joinToString(",")).apply()
                                Toast.makeText(context, if (isEnglish) "Currency added" else "تمت إضافة العملة الجديدة بنجاح", Toast.LENGTH_SHORT).show()
                            }
                            customCurrencyInput = ""
                            showNewCurrencyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(if (isEnglish) "OK" else "موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCurrencyDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء")
                }
            }
        )
    }
}

// ================== REUSABLE LIST ITEMS ==================

@Composable
fun SettingsMenuRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.End
                )
            }
            Text(
                text = icon,
                fontSize = 24.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .weight(1.0f)
                .padding(end = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.End
            )
        }
        Text(
            text = icon,
            fontSize = 26.sp
        )
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

@Composable
fun SettingsSwitchRow(
    icon: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFE91E63) // Match Pink toggle style from Screenshots
            )
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1.0f)
                .padding(end = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.End
            )
        }
        Text(
            text = icon,
            fontSize = 26.sp
        )
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}
