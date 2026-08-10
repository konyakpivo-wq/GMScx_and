package com.example.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import androidx.sqlite.db.SimpleSQLiteQuery

class AccountContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val db = AppDatabase.getDatabase(context)

        val cursor = MatrixCursor(
            arrayOf(
                "id",
                "accountType",
                "accountEmail",
                "displayName",
                "avatarUrl",
                "accessToken",
                "refreshToken",
                "tokenExpiresAt",
                "scopes",
                "isSyncEnabled"
            )
        )

        try {
            val dbHelper = db.openHelper.readableDatabase
            val rawCursor = dbHelper.query(
                SimpleSQLiteQuery("SELECT * FROM gmscx_accounts ORDER BY createdTimestamp DESC")
            )

            rawCursor.use { c ->
                val idIdx = c.getColumnIndex("id")
                val typeIdx = c.getColumnIndex("accountType")
                val emailIdx = c.getColumnIndex("accountEmail")
                val nameIdx = c.getColumnIndex("displayName")
                val avatarIdx = c.getColumnIndex("avatarUrl")
                val tokenIdx = c.getColumnIndex("accessToken")
                val refreshIdx = c.getColumnIndex("refreshToken")
                val expiresIdx = c.getColumnIndex("tokenExpiresAt")
                val scopesIdx = c.getColumnIndex("scopes")
                val syncIdx = c.getColumnIndex("isSyncEnabled")

                while (c.moveToNext()) {
                    cursor.addRow(
                        arrayOf(
                            if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            if (typeIdx >= 0) c.getString(typeIdx) else "GOOGLE",
                            if (emailIdx >= 0) c.getString(emailIdx) else "",
                            if (nameIdx >= 0) c.getString(nameIdx) else "",
                            if (avatarIdx >= 0) c.getString(avatarIdx) else null,
                            if (tokenIdx >= 0) c.getString(tokenIdx) else "",
                            if (refreshIdx >= 0) c.getString(refreshIdx) else null,
                            if (expiresIdx >= 0) c.getLong(expiresIdx) else 0L,
                            if (scopesIdx >= 0) c.getString(scopesIdx) else "",
                            if (syncIdx >= 0) c.getInt(syncIdx) else 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "getAccounts" || method == "get_accounts") {
            val context = context ?: return null
            val db = AppDatabase.getDatabase(context)
            val bundle = Bundle()

            try {
                val dbHelper = db.openHelper.readableDatabase
                val rawCursor = dbHelper.query(
                    SimpleSQLiteQuery("SELECT * FROM gmscx_accounts ORDER BY createdTimestamp DESC")
                )

                val accountList = ArrayList<Bundle>()
                rawCursor.use { c ->
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
                bundle.putParcelableArrayList("accounts", accountList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return bundle
        }
        return super.call(method, arg, extras)
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.dir/vnd.gmscx.accounts"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
