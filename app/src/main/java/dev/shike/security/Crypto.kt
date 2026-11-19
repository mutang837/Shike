package dev.shike.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private val random = SecureRandom()
    private val magic = "SHIKEBK1".toByteArray()

    fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    fun derive(password: CharArray, salt: ByteArray, iterations: Int = 210000): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(data)
    }

    fun open(data: ByteArray, key: SecretKey): ByteArray {
        require(data.size >= 28) { "文件不完整" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size))
    }

    fun backup(data: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "备份密码至少 8 位" }
        val salt = randomBytes(16)
        val key = derive(password, salt)
        return try {
            magic + salt + seal(data, SecretKeySpec(key, "AES"))
        } finally {
            key.fill(0)
        }
    }

    fun restore(data: ByteArray, password: CharArray): ByteArray {
        require(data.size in 52..52_428_800 && data.take(8).toByteArray().contentEquals(magic)) {
            "不是有效的拾刻加密备份"
        }
        val key = derive(password, data.copyOfRange(8, 24))
        return try {
            open(data.copyOfRange(24, data.size), SecretKeySpec(key, "AES"))
        } finally {
            key.fill(0)
        }
    }

    fun passwordHash(password: String): String {
        val salt = randomBytes(16)
        return Base64.getEncoder().encodeToString(salt) +
            ":" +
            Base64.getEncoder().encodeToString(derive(password.toCharArray(), salt))
    }

    fun verify(password: String, hash: String): Boolean =
        runCatching {
                val p = hash.split(":")
                java.security.MessageDigest.isEqual(
                    derive(password.toCharArray(), Base64.getDecoder().decode(p[0])),
                    Base64.getDecoder().decode(p[1]),
                )
            }
            .getOrDefault(false)
}

class KeyVault(private val context: Context) {
    private fun master(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("shike_database_wrapping_v1", null) as? SecretKey)?.let {
            return it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                    "shike_database_wrapping_v1",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    fun databaseKey(): ByteArray {
        val file = File(context.noBackupFilesDir, "database.key")
        val key = master()
        if (file.exists()) return Crypto.open(file.readBytes(), key)
        require(!context.getDatabasePath("shike.db").exists()) { "本机密钥丢失。请从独立密码备份恢复；原数据库已保留。" }
        val pass = Crypto.randomBytes(32)
        atomicWrite(file, Crypto.seal(pass, key))
        return pass
    }

    fun saveBackupPassword(password: CharArray) {
        atomicWrite(
            File(context.noBackupFilesDir, "portable.key"),
            Crypto.seal(String(password).toByteArray(), master()),
        )
    }

    fun backupPassword(): CharArray? =
        File(context.noBackupFilesDir, "portable.key")
            .takeIf { it.exists() }
            ?.let { String(Crypto.open(it.readBytes(), master())).toCharArray() }

    fun clearBackupPassword() {
        File(context.noBackupFilesDir, "portable.key").delete()
    }

    companion object {
        fun atomicWrite(file: File, bytes: ByteArray) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            java.io.FileOutputStream(tmp).use {
                it.write(bytes)
                it.fd.sync()
            }
            check(tmp.renameTo(file)) { "无法安全写入文件" }
        }
    }
}
