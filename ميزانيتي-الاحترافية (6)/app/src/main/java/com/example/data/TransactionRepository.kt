package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun insert(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun update(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteById(id: Int) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun deleteByAccount(accountName: String) {
        transactionDao.deleteTransactionsByAccount(accountName)
    }

    fun getTransactionsByAccount(name: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByAccount(name)
    }
}
