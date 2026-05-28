package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Transaction
import com.example.data.TransactionRepository
import com.example.data.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountSummary(
    val accountName: String,
    val totalBalance: Double,
    val transactionCount: Int,
    val isOwedToUs: Boolean, // true -> عليه (We gave him money, he owes us), false -> له (He gave us money, we owe him)
    val phoneNumber: String = "",
    val balancesByCurrency: Map<String, Double> = emptyMap(),
    val primaryCurrency: String = "محلي"
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TransactionRepository
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = TransactionRepository(database.transactionDao())
    }

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val accountsSummary: StateFlow<List<AccountSummary>> = repository.allTransactions.map { list ->
        list.groupBy { it.accountName }.map { (name, txs) ->
            val balancesByCurrency = txs.groupBy { it.currency }.mapValues { (_, currencyTxs) ->
                currencyTxs.sumOf { if (it.type == TransactionType.ON_HIM) it.amount else -it.amount }
            }
            val primaryCurrency = txs.maxByOrNull { it.timestamp }?.currency ?: "محلي"
            
            var balanceAsBefore = 0.0
            for (tx in txs) {
                if (tx.type == TransactionType.ON_HIM) {
                    balanceAsBefore += tx.amount
                } else {
                    balanceAsBefore -= tx.amount
                }
            }
            val phone = txs.firstOrNull { it.phoneNumber.isNotBlank() }?.phoneNumber ?: ""
            AccountSummary(
                accountName = name,
                totalBalance = kotlin.math.abs(balanceAsBefore),
                transactionCount = txs.size,
                isOwedToUs = balanceAsBefore > 0,
                phoneNumber = phone,
                balancesByCurrency = balancesByCurrency,
                primaryCurrency = primaryCurrency
            )
        }.sortedByDescending { it.totalBalance }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalOwedToUs: StateFlow<Double> = repository.allTransactions.map { list ->
        list.groupBy { it.accountName }.map { (name, txs) ->
            txs.sumOf { if (it.type == TransactionType.ON_HIM) it.amount else -it.amount }
        }.filter { it > 0 }.sum()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val totalOwedByUs: StateFlow<Double> = repository.allTransactions.map { list ->
        list.groupBy { it.accountName }.map { (name, txs) ->
            txs.sumOf { if (it.type == TransactionType.ON_HIM) it.amount else -it.amount }
        }.filter { it < 0 }.sumOf { kotlin.math.abs(it) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insert(transaction)
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}
