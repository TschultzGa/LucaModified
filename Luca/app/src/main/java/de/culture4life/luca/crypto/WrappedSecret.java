package de.culture4life.luca.crypto;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;

import de.culture4life.luca.util.SerializationUtil;

/**
 * Model to store on-device secrets encrypted using the {@link WrappingCipherProvider}. This, among
 * others, includes the BC key store password.
 *
 * @see CryptoManager#getKeyStorePassword()
 */
public class WrappedSecret {

    @Expose
    private String encryptedSecret;

    @Nullable
    @Expose
    private String iv;

    public WrappedSecret() {
    }

    public WrappedSecret(@NonNull byte[] encryptedSecret) {
        this.encryptedSecret = SerializationUtil.toBase64(encryptedSecret).blockingGet();
    }

    public WrappedSecret(@NonNull Pair<byte[], byte[]> encryptedSecretAndIv) {
        this.encryptedSecret = SerializationUtil.toBase64(encryptedSecretAndIv.first).blockingGet();
        if (encryptedSecretAndIv.second != null) {
            this.iv = SerializationUtil.toBase64(encryptedSecretAndIv.second).blockingGet();
        }
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public byte[] getDeserializedEncryptedSecret() {
        return SerializationUtil.fromBase64(encryptedSecret).blockingGet();
    }

    public void setEncryptedSecret(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
    }

    @Nullable
    public String getIv() {
        return iv;
    }

    @Nullable
    public byte[] getDeserializedIv() {
        if (iv == null) {
            return null;
        }
        return SerializationUtil.fromBase64(iv).blockingGet();
    }

    public void setIv(@Nullable String iv) {
        this.iv = iv;
    }

}
