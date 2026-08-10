package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == "com.gmscx.services.GET_ACCOUNTS" ||
            action == "com.yt.cx.GET_ACCOUNTS" ||
            action == "com.yt.cx.REQUEST_ACCOUNTS"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dbHelper = db.openHelper.readableDatabase
                    val cursor = dbHelper.query(
                        SimpleSQLiteQuery("SELECT * FROM gmscx_accounts ORDER BY createdTimestamp DESC")
                    )

                    val accountList = ArrayList<Bundle>()
                    cursor.use { c ->
                        val emailIdx = c.getColumnIndex("accountEmail")
                        val nameIdx = c.getColumnIndex("displayName")
                        val tokenIdx = c.getColumnIndex("accessToken")
                        val refreshIdx = c.getColumnIndex("refreshToken")
                        val typeIdx = c.getColumnIndex("accountType")
                        val avatarIdx = c.getColumnIndex("avatarUrl")
                        val scopesIdx = c.getColumnIndex("scopes")

                        while (c.moveToNext()) {
                            val accBundle = Bundle().apply {
                                putString("email", if (emailIdx >= 0) c.getString(emailIdx) else "")
                                putString("accountEmail", if (emailIdx >= 0) c.getString(emailIdx) else "")
                                putString("displayName", if (nameIdx >= 0) c.getString(nameIdx) else "")
                                putString("accessToken", if (tokenIdx >= 0) c.getString(tokenIdx) else "")
                                putString("refreshToken", if (refreshIdx >= 0) c.getString(refreshIdx) else null)
                                putString("accountType", if (typeIdx >= 0) c.getString(typeIdx) else "GOOGLE")
                                putString("avatarUrl", if (avatarIdx >= 0) c.getString(avatarIdx) else null)
                                putString("scopes", if (scopesIdx >= 0) c.getString(scopesIdx) else "")
                            }
                            accountList.add(accBundle)
                        }
                    }

                    val replyIntent = Intent("com.yt.cx.ACCOUNTS_RESPONSE").apply {
                        setPackage("com.yt.cx")
                        putParcelableArrayListExtra("accounts", accountList)
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    }
                    context.sendBroadcast(replyIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
