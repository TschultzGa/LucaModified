package de.culture4life.luca.util

import android.util.Base64
import com.google.gson.Gson
import com.nexenio.rxkeystore.util.RxBase64
import io.reactivex.rxjava3.core.Single
import org.apache.commons.codec.binary.Base32

object SerializationUtil {

    private val GSON = Gson()
    private val BASE_32 = Base32()

    @JvmStatic
    fun toJson(data: Any): Single<String> {
        return Single.fromCallable { GSON.toJson(data) }
    }

    @JvmStatic
    fun <Type : Any> fromJson(json: String, typeClass: Class<Type>): Single<Type> {
        return Single.fromCallable { GSON.fromJson(json, typeClass) }
    }

    @JvmStatic
    fun toHex(bytes: ByteArray): Single<String> {
        return Single.fromCallable {
            bytes.joinToString("") { "%02x".format(it) }
        }
    }

    @JvmStatic
    fun fromHex(hex: String): Single<ByteArray> {
        return Single.fromCallable {
            check(hex.length % 2 == 0) { "Must have an even length" }
            hex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }

    @JvmStatic
    fun toBase64(bytes: ByteArray): Single<String> {
        return toBase64(bytes, Base64.NO_WRAP)
    }

    @JvmStatic
    fun toBase64(bytes: ByteArray, flags: Int = Base64.NO_WRAP): Single<String> {
        return RxBase64.encode(bytes, flags)
    }

    @JvmStatic
    fun fromBase64(base64: String): Single<ByteArray> {
        return fromBase64(base64, Base64.NO_WRAP)
    }

    @JvmStatic
    fun fromBase64(base64: String, flags: Int = Base64.NO_WRAP): Single<ByteArray> {
        return RxBase64.decode(base64, flags)
    }

    @JvmStatic
    fun toBase32(bytes: ByteArray): Single<String> {
        return Single.fromCallable { BASE_32.encodeAsString(bytes) }
    }

    @JvmStatic
    fun fromBase32(base32: String): Single<ByteArray> {
        return Single.fromCallable { BASE_32.decode(base32) }
    }
}

fun Any.serializeToJson(): String {
    return SerializationUtil.toJson(this).blockingGet()
}

fun <Type : Any> String.deserializeFromJson(typeClass: Class<Type>): Type {
    return SerializationUtil.fromJson(this, typeClass).blockingGet()
}

fun ByteArray.encodeToHex(): String {
    return SerializationUtil.toHex(this).blockingGet()
}

fun String.decodeFromHex(): ByteArray {
    return SerializationUtil.fromHex(this).blockingGet()
}

fun ByteArray.encodeToBase64(flags: Int = Base64.NO_WRAP): String {
    return SerializationUtil.toBase64(this, flags).blockingGet()
}

fun String.decodeFromBase64(flags: Int = Base64.NO_WRAP): ByteArray {
    return SerializationUtil.fromBase64(this, flags).blockingGet()
}

fun ByteArray.encodeToBase32(): String {
    return SerializationUtil.toBase32(this).blockingGet()
}

fun String.decodeFromBase32(): ByteArray {
    return SerializationUtil.fromBase32(this).blockingGet()
}
