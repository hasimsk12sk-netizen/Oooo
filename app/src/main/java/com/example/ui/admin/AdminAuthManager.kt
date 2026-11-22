package com.example.ui.admin

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseUser
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AdminAuthManager:
 * Provides salted PBKDF2-HMAC-SHA256 credential verification, persistent admin session
 * management, and Google Admin account verification matching Firestore security rules.
 *
 * Raw passwords are never stored in code, resources, Firebase documents, or logs.
 */
object AdminAuthManager {

    private const val PREFS_NAME = "storepro_admin_session"
    private const val KEY_IS_ADMIN = "is_admin_authenticated"
    private const val KEY_ADMIN_USER = "admin_username"
    private const val KEY_LOGIN_TIME = "admin_login_timestamp"

    const val AUTHORIZED_ADMIN_EMAIL = "hasimsk12345678sk@gmail.com"
    const val TARGET_ADMIN_USERNAME = AUTHORIZED_ADMIN_EMAIL

    private const val PBKDF2_ITERATIONS = 10_000
    private const val ADMIN_SALT_HEX = "26a0d3c8c97044cc81d89b6c77fae013"
    private const val ADMIN_HASH_HEX = "330b9272941060f2e146278472d9e4cefeecd8b8ff0c6ec4b1d25b21cb2c7bf0"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Verifies administrator email/username and password via constant-time PBKDF2-HMAC-SHA256.
     */
    fun verifyCredentials(usernameInput: String, passwordInput: String): Boolean {
        if (usernameInput.isBlank() || passwordInput.isEmpty()) return false
        val normalizedInput = usernameInput.trim()
        if (!normalizedInput.equals(AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)) {
            return false
        }

        val saltBytes = hexToBytes(ADMIN_SALT_HEX)
        val expectedHash = hexToBytes(ADMIN_HASH_HEX)
        val derived = pbkdf2HmacSha256(
            password = passwordInput.toByteArray(Charsets.UTF_8),
            salt = saltBytes,
            iterations = PBKDF2_ITERATIONS,
            keyLengthBytes = expectedHash.size
        )
        return MessageDigest.isEqual(derived, expectedHash)
    }

    /**
     * Persists an active admin session after both credential and Google identity verification succeed.
     */
    fun markSessionAuthenticated(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_ADMIN, true)
            putString(KEY_ADMIN_USER, AUTHORIZED_ADMIN_EMAIL)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
        }
    }

    /**
     * Authenticates administrator credentials and persists the active admin session
     * across app restarts until logout() is called.
     */
    fun authenticate(context: Context, usernameInput: String, passwordInput: String): Boolean {
        val isValid = verifyCredentials(usernameInput, passwordInput)
        if (isValid) {
            markSessionAuthenticated(context)
        }
        return isValid
    }

    /**
     * Checks whether the local admin credential session is active for the authorized administrator.
     */
    fun isAdminLoggedIn(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isActive = prefs.getBoolean(KEY_IS_ADMIN, false)
        val storedUser = prefs.getString(KEY_ADMIN_USER, null)
        return isActive && storedUser.equals(AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)
    }

    /**
     * Checks whether the Firebase user holds the authorized administrator Google identity.
     */
    fun isAuthorizedAdminGoogleUser(user: FirebaseUser?): Boolean {
        if (user == null) return false
        val email = user.email ?: return false
        return email.equals(AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)
    }

    /**
     * Full admin authorization requires both an active admin session AND the verified
     * admin Google account enforced by Firestore security rules.
     */
    fun isFullyAuthorizedAdmin(context: Context, user: FirebaseUser?): Boolean {
        return isAdminLoggedIn(context) && isAuthorizedAdminGoogleUser(user)
    }

    fun getAdminUsername(context: Context): String {
        val stored = getPrefs(context).getString(KEY_ADMIN_USER, AUTHORIZED_ADMIN_EMAIL)
        return if (stored.equals(AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)) {
            AUTHORIZED_ADMIN_EMAIL
        } else {
            AUTHORIZED_ADMIN_EMAIL
        }
    }

    fun logout(context: Context) {
        getPrefs(context).edit { clear() }
    }

    private fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hashLen = mac.macLength
        val blocks = (keyLengthBytes + hashLen - 1) / hashLen
        val output = ByteArray(blocks * hashLen)

        for (blockIndex in 1..blocks) {
            val blockInput = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, blockInput, 0, salt.size)
            blockInput[salt.size] = ((blockIndex ushr 24) and 0xFF).toByte()
            blockInput[salt.size + 1] = ((blockIndex ushr 16) and 0xFF).toByte()
            blockInput[salt.size + 2] = ((blockIndex ushr 8) and 0xFF).toByte()
            blockInput[salt.size + 3] = (blockIndex and 0xFF).toByte()

            var u = mac.doFinal(blockInput)
            val t = u.copyOf()
            for (iter in 2..iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            System.arraycopy(t, 0, output, (blockIndex - 1) * hashLen, hashLen)
        }
        return output.copyOf(keyLengthBytes)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
