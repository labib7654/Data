package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import com.example.data.Transaction
import com.example.data.TransactionType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    accountsSummary: List<AccountSummary>,
    transactions: List<Transaction>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE) }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "الملخص المالي",
        "التقرير الشهري",
        "كشف الحساب",
        "تقرير العملات",
        "تعدي السقوف"
    )
    
    val themeColor = Color(0xFF3949AB)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text("لوحة تقارير ميزانيتي الاحترافية", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Material 3 Scrollable Tab Row for elegant and comfortable navigation across the 5 screens
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFFF5F5F5),
                contentColor = themeColor,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Render tabs from right-to-left manually by reversing the list or displaying normally
                tabs.onEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> GeneralSummaryReport(accountsSummary, transactions)
                    1 -> MonthlyPeriodicReport(transactions)
                    2 -> ClientLedgerReport(accountsSummary, transactions)
                    3 -> CurrenciesClassificationReport(transactions)
                    4 -> CreditCeilingReport(accountsSummary, sharedPrefs)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 1: General Summary Report
// ----------------------------------------------------------------------------
@Composable
fun GeneralSummaryReport(accountsSummary: List<AccountSummary>, transactions: List<Transaction>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
    ) {
        val totalOwedToUs = accountsSummary.filter { it.isOwedToUs }.sumOf { it.totalBalance }
        val totalOwedByUs = accountsSummary.filter { !it.isOwedToUs }.sumOf { it.totalBalance }
        val netBalance = totalOwedToUs - totalOwedByUs

        Text(
            text = "الخلاصة المالية العامة للدفاتر",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            color = Color(0xFF1E293B)
        )
        Text(
            text = "مراجعة سريعة لجميع الذمم المدينة والدائنة المسجلة حالياً.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = TextAlign.End
        )

        // General Stats Cards
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text("إجمالي ما عليك للآخرين (عليه)", fontSize = 12.sp, color = Color(0xFFC62828))
                Spacer(modifier = Modifier.height(4.dp))
                Text(formatAmount(totalOwedByUs), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text("إجمالي ما لك عند الآخرين (له)", fontSize = 12.sp, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(4.dp))
                Text(formatAmount(totalOwedToUs), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text("الرصيد الصافي الموحد لقواعدك", fontSize = 12.sp, color = Color(0xFF3F51B5))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatAmount(netBalance),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (netBalance >= 0) Color(0xFF303F9F) else Color(0xFFD32F2F)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Activity and density metrics
        Text(
            text = "مؤشرات النشاط والعملاء",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF1E293B)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
            ) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("العمليات", fontSize = 12.sp, color = Color.Gray)
                    Text("${transactions.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
            ) {
                Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الحسابات النشطة", fontSize = 12.sp, color = Color.Gray)
                    Text("${accountsSummary.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 2: Monthly / Periodic Analysis Report
// ----------------------------------------------------------------------------
@Composable
fun MonthlyPeriodicReport(transactions: List<Transaction>) {
    // Group transactions by month-year
    val dateFormat = SimpleDateFormat("MMMM yyyy", Locale("ar"))
    val monthlyData = remember(transactions) {
        transactions.groupBy { tx ->
            dateFormat.format(Date(tx.timestamp))
        }.mapValues { (_, txList) ->
            val totalForHim = txList.filter { it.type == TransactionType.FOR_HIM }.sumOf { it.amount }
            val totalOnHim = txList.filter { it.type == TransactionType.ON_HIM }.sumOf { it.amount }
            Pair(totalForHim, totalOnHim)
        }.toList().sortedByDescending { it.first } // sort by dates if parseable
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "التقرير المالي والتحليل الشهري",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )
        Text(
            text = "متابعة أداء وحجم الحركة المدخلة شهرياً لمقارنة الحسابات.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textAlign = TextAlign.End
        )

        if (monthlyData.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد بيانات حركة لعرض التقرير الشهري.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(monthlyData) { (monthName, totals) ->
                    val (forHim, onHim) = totals
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(
                                text = monthName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF3949AB),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatAmount(onHim),
                                    color = Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text("إجمالي القيود الدائنة (عليه):", color = Color.Gray, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatAmount(forHim),
                                    color = Color(0xFF388E3C),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text("إجمالي القيود المدينة (له):", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 3: Interactive Client Ledger Report
// ----------------------------------------------------------------------------
@Composable
fun ClientLedgerReport(accountsSummary: List<AccountSummary>, transactions: List<Transaction>) {
    var selectedAccountName by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    // Use first account if none is selected
    LaunchedEffect(accountsSummary) {
        if (selectedAccountName.isEmpty() && accountsSummary.isNotEmpty()) {
            selectedAccountName = accountsSummary.first().accountName
        }
    }

    val accountTransactions = remember(selectedAccountName, transactions) {
        transactions.filter { it.accountName == selectedAccountName }.sortedBy { it.timestamp }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "كشف حساب عملاء تفصيلي (حركة الحساب)",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )
        Text(
            text = "اختر الحساب لاستعراض تفاصيل حركة القيود والترصيد السجل تاريخياً.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textAlign = TextAlign.End
        )

        // Dropdown selector
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedAccountName,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("اختر العميل لاستعراض كشفه") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDropdown = true },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3949AB),
                    unfocusedBorderColor = Color.LightGray
                )
            )

            DropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                accountsSummary.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.accountName, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            selectedAccountName = account.accountName
                            expandedDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedAccountName.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("الرجاء إضافة عملاء أولاً لعرض الحسابات", color = Color.Gray)
            }
        } else {
            // Display transactions
            Text(
                text = "سجل الكشف لـ ($selectedAccountName) - ${accountTransactions.size} معامـلة",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                textAlign = TextAlign.End
            )

            // Header of ledger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(4.dp))
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("المتفقيات والبيان", modifier = Modifier.weight(2f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.DarkGray)
                Text("عليه (دائن)", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFD32F2F))
                Text("له (مدين)", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF388E3C))
                Text("التاريخ", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.DarkGray)
            }

            if (accountTransactions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد حركة مسجلة لهذا الحساب.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(accountTransactions) { tx ->
                        val dateString = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(tx.timestamp))
                        val isForHim = tx.type == TransactionType.FOR_HIM

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Description & Currency
                            Column(modifier = Modifier.weight(2f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tx.details.ifBlank { "بدون بيان" },
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.End
                                )
                                Text(text = "عملة: ${tx.currency}", fontSize = 9.sp, color = Color.Gray)
                            }
                            
                            // On him
                            Text(
                                text = if (!isForHim) formatAmount(tx.amount) else "-",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = if (!isForHim) Color(0xFFD32F2F) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (!isForHim) FontWeight.Bold else FontWeight.Normal
                            )

                            // For him
                            Text(
                                text = if (isForHim) formatAmount(tx.amount) else "-",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = if (isForHim) Color(0xFF388E3C) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (isForHim) FontWeight.Bold else FontWeight.Normal
                            )

                            // Date
                            Text(
                                text = dateString,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                color = Color.DarkGray,
                                fontSize = 11.sp
                            )
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 4: Currencies and Multi-currency Distribution Report
// ----------------------------------------------------------------------------
@Composable
fun CurrenciesClassificationReport(transactions: List<Transaction>) {
    val currencySummary = remember(transactions) {
        transactions.groupBy { it.currency.ifBlank { "محلي" } }.mapValues { (_, txList) ->
            val totalForHim = txList.filter { it.type == TransactionType.FOR_HIM }.sumOf { it.amount }
            val totalOnHim = txList.filter { it.type == TransactionType.ON_HIM }.sumOf { it.amount }
            val net = totalForHim - totalOnHim
            Triple(totalForHim, totalOnHim, net)
        }.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "توزيع الحسابات وإجمالي الأرصدة حسب العملة",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )
        Text(
            text = "إحصائيات إجماليات الدفاتر مصنفة لكل عملة معرفة بشكل مستقل.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textAlign = TextAlign.End
        )

        if (currencySummary.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد فئات عملة مسجلة في قاعدة البيانات.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(currencySummary) { (currencyName, data) ->
                    val (forHim, onHim, net) = data
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text(
                                text = "فئة العملة: $currencyName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF3949AB),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatAmount(onHim), color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                                Text("إجمالي عليه (مدينين لك):", color = Color.Gray, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatAmount(forHim), color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                                Text("إجمالي لـه (أنت مدين لهم):", color = Color.Gray, fontSize = 12.sp)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatAmount(net),
                                    color = if (net >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Bold
                                )
                                Text("صافي رصيد العملة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 5: Ceilings & Debts Limit Violation Report
// ----------------------------------------------------------------------------
@Composable
fun CreditCeilingReport(accountsSummary: List<AccountSummary>, sharedPrefs: android.content.SharedPreferences) {
    val violatedAccounts = remember(accountsSummary) {
        accountsSummary.mapNotNull { account ->
            val limitStr = sharedPrefs.getString("ceiling_${account.accountName}", "") ?: ""
            val limit = limitStr.toDoubleOrNull() ?: 0.0
            if (limit > 0.0 && account.totalBalance > limit && account.isOwedToUs) {
                Pair(account, limit)
            } else {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "العملاء المتجاوزين لسقف الائتمان والدين المقدر",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )
        Text(
            text = "يعرض هذا التقرير كشفاً بكافة العملاء والذمم التي تجاوزت سقف الدين المسموح لها.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textAlign = TextAlign.End
        )

        if (violatedAccounts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFF388E3C), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ممتاز! لا يوجد عملاء متجاوزين للرصيد المسجل أو سقف ائتمانهم حالياً.", 
                        color = Color.Gray, 
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(violatedAccounts) { (account, limit) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A))
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFD32F2F))
                                Text(
                                    text = account.accountName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFC62828)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "الرصيد القائم حالياً: ${formatAmount(account.totalBalance)} (عليه)",
                                color = Color.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "السقف الائتماني المحدد: ${formatAmount(limit)}",
                                color = Color.DarkGray,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val diff = account.totalBalance - limit
                            Text(
                                text = "الزيادة المتجاوزة بسقف الحساب: ${formatAmount(diff)}",
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
