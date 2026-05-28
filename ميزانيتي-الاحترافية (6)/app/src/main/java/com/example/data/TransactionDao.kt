package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE accountName = :name ORDER BY timestamp DESC")
    fun getTransactionsByAccount(name: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM transactions WHERE accountName = :accountName")
    suspend fun deleteTransactionsByAccount(accountName: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT COUNT(DISTINCT accountName) FROM transactions")
    suspend fun getAccountCount(): Int

    @Query("SELECT SUM(CASE WHEN type = 'ON_HIM' THEN amount ELSE -amount END) FROM transactions WHERE accountName = :name")
    suspend fun getAccountBalance(name: String): Double?
}
