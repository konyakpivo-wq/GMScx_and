package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM gmscx_accounts ORDER BY createdTimestamp DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM gmscx_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM gmscx_accounts WHERE accountEmail = :email AND accountType = :type LIMIT 1")
    suspend fun getAccountByEmailAndType(email: String, type: AccountType): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM gmscx_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("UPDATE gmscx_accounts SET isSyncEnabled = :enabled WHERE id = :id")
    suspend fun setAccountSync(id: Long, enabled: Boolean)
}
