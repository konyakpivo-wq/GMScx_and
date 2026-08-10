package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountType(val displayName: String, val brandColorHex: String) {
    GOOGLE("Google Account", "#4285F4"),
    YANDEX("Yandex Account", "#FC3F1D")
}

@Entity(tableName = "gmscx_accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountType: AccountType,
    val accountEmail: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenExpiresAt: Long = System.currentTimeMillis() + 3600000,
    val scopes: String = "",
    val isSyncEnabled: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis()
)
