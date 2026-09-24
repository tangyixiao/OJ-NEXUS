package com.ojnexus.judge.luogu.open

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keystore-backed OpenApp storage. The file is deliberately placed in noBackupFilesDir:
 * local credentials must never enter the database export or Android Auto Backup stream.
 */
class AndroidOpenAppCredentialStore(
    context: Context,
    private val alias: String = DEFAULT_ALIAS,
) : OpenAppCredentialStore {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    override suspend fun read(): OpenAppCredential? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        runCatching {
            val encrypted = file.readBytes()
            require(encrypted.size > IV_LENGTH) { "OpenApp credential file is truncated" }
            val buffer = ByteBuffer.wrap(encrypted)
            val iv = ByteArray(IV_LENGTH)
            buffer.get(iv)
            val plaintext = decrypt(iv, buffer.slice().let { remaining ->
                ByteArray(remaining.remaining()).also(remaining::get)
            })
            decode(plaintext)
        }.getOrElse { error ->
            throw IllegalStateException("OpenApp credential could not be read", error)
        }
    }

    override suspend fun write(value: OpenAppCredential) = withContext(Dispatchers.IO) {
        validate(value)
        val iv = ByteArray(IV_LENGTH).also(java.security.SecureRandom()::nextBytes)
        val encrypted = encrypt(iv, encode(value))
        val output = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(output)
        try {
            try {
                Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("OpenApp credential could not be committed", error)
        }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        val keyStore = keyStore()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun encrypt(iv: ByteArray, plaintext: ByteArray): ByteArray =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }.doFinal(plaintext)

    private fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }.doFinal(ciphertext)

    private fun key(): SecretKey {
        val store = keyStore()
        if (!store.containsAlias(alias)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }.generateKey()
        }
        return (store.getKey(alias, null) as? SecretKey)
            ?: error("OpenApp Keystore key is unavailable")
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun encode(value: OpenAppCredential): ByteArray =
        "${value.user}\n${value.secret}".toByteArray(StandardCharsets.UTF_8)

    private fun decode(bytes: ByteArray): OpenAppCredential {
        val value = String(bytes, StandardCharsets.UTF_8)
        val separator = value.indexOf('\n')
        require(separator > 0 && separator < value.lastIndex) { "OpenApp credential payload is invalid" }
        return OpenAppCredential(value.substring(0, separator), value.substring(separator + 1)).also(::validate)
    }

    private fun validate(value: OpenAppCredential) {
        require(value.user.isNotBlank() && value.secret.isNotBlank()) { "OpenApp credential is blank" }
        require('\r' !in value.user && '\n' !in value.user) { "OpenApp user contains a line break" }
        require('\r' !in value.secret && '\n' !in value.secret) { "OpenApp secret contains a line break" }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val DEFAULT_ALIAS = "oj-nexus-luogu-openapp-v1"
        const val FILE_NAME = "luogu-openapp-credential.bin"
    }
}
