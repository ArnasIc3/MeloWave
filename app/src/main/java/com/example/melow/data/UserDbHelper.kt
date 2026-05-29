package com.example.melow.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Patterns
import java.security.MessageDigest
import java.util.UUID

data class User(val id: Long, val username: String, val email: String)

class UserDbHelper(context: Context) : SQLiteOpenHelper(context, "melowave_users.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT    NOT NULL,
                email         TEXT    UNIQUE NOT NULL,
                password_hash TEXT    NOT NULL,
                salt          TEXT    NOT NULL,
                created_at    INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    sealed class RegisterResult {
        object Success                : RegisterResult()
        object EmailInvalid           : RegisterResult()
        object EmailTaken             : RegisterResult()
        object UsernameTooShort       : RegisterResult()
        object PasswordTooShort       : RegisterResult()
        object PasswordNoDigit        : RegisterResult()
    }

    sealed class LoginResult {
        data class Success(val user: User) : LoginResult()
        object NotFound                    : LoginResult()
        object WrongPassword               : LoginResult()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun register(username: String, email: String, password: String): RegisterResult {
        if (username.trim().length < 2)                       return RegisterResult.UsernameTooShort
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return RegisterResult.EmailInvalid
        if (password.length < 6)                              return RegisterResult.PasswordTooShort
        if (password.none { it.isDigit() })                   return RegisterResult.PasswordNoDigit

        return try {
            val salt = UUID.randomUUID().toString()
            val values = ContentValues().apply {
                put("username",      username.trim())
                put("email",         email.trim().lowercase())
                put("password_hash", hashPassword(password, salt))
                put("salt",          salt)
                put("created_at",    System.currentTimeMillis())
            }
            writableDatabase.insertOrThrow("users", null, values)
            RegisterResult.Success
        } catch (_: Exception) {
            RegisterResult.EmailTaken
        }
    }

    fun login(email: String, password: String): LoginResult {
        val cursor = readableDatabase.query(
            "users",
            arrayOf("id", "username", "email", "password_hash", "salt"),
            "email = ?", arrayOf(email.trim().lowercase()),
            null, null, null
        )
        cursor.use {
            if (!it.moveToFirst()) return LoginResult.NotFound
            val storedHash = it.getString(it.getColumnIndexOrThrow("password_hash"))
            val salt       = it.getString(it.getColumnIndexOrThrow("salt"))
            if (hashPassword(password, salt) != storedHash) return LoginResult.WrongPassword
            return LoginResult.Success(User(
                id       = it.getLong(it.getColumnIndexOrThrow("id")),
                username = it.getString(it.getColumnIndexOrThrow("username")),
                email    = it.getString(it.getColumnIndexOrThrow("email"))
            ))
        }
    }

    fun allUsers(): List<Triple<Long, String, String>> {
        val cursor = readableDatabase.query(
            "users", arrayOf("id", "username", "email"),
            null, null, null, null, "created_at ASC"
        )
        val list = mutableListOf<Triple<Long, String, String>>()
        cursor.use {
            while (it.moveToNext()) {
                list += Triple(
                    it.getLong(it.getColumnIndexOrThrow("id")),
                    it.getString(it.getColumnIndexOrThrow("username")),
                    it.getString(it.getColumnIndexOrThrow("email"))
                )
            }
        }
        return list
    }

    fun dbFilePath(context: Context): String =
        context.getDatabasePath("melowave_users.db").absolutePath

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((salt + password).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
