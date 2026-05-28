package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: TransactionType,
    val amount: Double,
    val accountName: String,
    val category: String = "عام",
    val details: String,
    val currency: String = "محلي",
    val phoneNumber: String = "",
    val imageUri: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class TransactionType {
    FOR_HIM, // له
    ON_HIM // عليه
}
