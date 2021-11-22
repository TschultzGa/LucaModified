package de.culture4life.luca.ui.checkin;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;

import de.culture4life.luca.util.SerializationUtil;

/**
 * Model holding necessary details to facilitate checking in at a venue.
 *
 * @see <a href="https://www.luca-app.de/securityoverview/processes/guest_app_checkin.html#qr-code-generation-and-check-in">Security
 * Overview: QR Code Generation and Check-In</a>
 */
public class QrCodeData {

    private static final byte VERSION_CURRENT = 4;

    @IntDef({TYPE_IOS, TYPE_ANDROID, TYPE_STATIC})
    @Retention(SOURCE)
    public @interface Type {

    }

    private static final int TYPE_IOS = 0;
    private static final int TYPE_ANDROID = 1;
    private static final int TYPE_STATIC = 2;

    @IntDef({
            ENTRY_POLICY_NOT_SHARED,
            ENTRY_POLICY_QUICK_TESTED, ENTRY_POLICY_PCR_TESTED,
            ENTRY_POLICY_RECOVERED, ENTRY_POLICY_VACCINATED
    })
    @Retention(SOURCE)
    public @interface EntryPolicy {

    }

    public static final int ENTRY_POLICY_NOT_SHARED = 0x01;
    public static final int ENTRY_POLICY_QUICK_TESTED = 0x02;
    public static final int ENTRY_POLICY_PCR_TESTED = 0x04;
    public static final int ENTRY_POLICY_RECOVERED = 0x08;
    public static final int ENTRY_POLICY_VACCINATED = 0x10;

    private byte version = VERSION_CURRENT;

    private byte deviceType = TYPE_ANDROID;

    private byte entryPolicy = ENTRY_POLICY_NOT_SHARED;

    private byte keyId;

    private byte[] timestamp;

    private byte[] traceId;

    private byte[] encryptedData;

    private byte[] userEphemeralPublicKey;

    private byte[] verificationTag;

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(byte deviceType) {
        this.deviceType = deviceType;
    }

    public byte getEntryPolicy() {
        return entryPolicy;
    }

    public void setEntryPolicy(byte entryPolicy) {
        this.entryPolicy = entryPolicy;
    }

    public void setDeviceType(@Type int deviceType) {
        setDeviceType((byte) deviceType);
    }

    public byte getKeyId() {
        return keyId;
    }

    public void setKeyId(byte keyId) {
        this.keyId = keyId;
    }

    public void setKeyId(int keyId) {
        setKeyId((byte) keyId);
    }

    public byte[] getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(byte[] timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getTraceId() {
        return traceId;
    }

    public void setTraceId(byte[] traceId) {
        this.traceId = traceId;
    }

    public byte[] getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(byte[] encryptedData) {
        this.encryptedData = encryptedData;
    }

    public byte[] getUserEphemeralPublicKey() {
        return userEphemeralPublicKey;
    }

    public void setUserEphemeralPublicKey(byte[] userEphemeralPublicKey) {
        this.userEphemeralPublicKey = userEphemeralPublicKey;
    }

    public byte[] getVerificationTag() {
        return verificationTag;
    }

    public void setVerificationTag(byte[] verificationTag) {
        this.verificationTag = verificationTag;
    }

    @Override
    public String toString() {
        return "QrCodeData{" +
                "version=" + version +
                ", deviceType=" + deviceType +
                ", entryPolicy=" + entryPolicy +
                ", keyId=" + keyId +
                ", timestamp=" + SerializationUtil.serializeToBase64(timestamp).blockingGet() +
                ", traceId=" + SerializationUtil.serializeToBase64(traceId).blockingGet() +
                ", encryptedData=" + SerializationUtil.serializeToBase64(encryptedData).blockingGet() +
                ", userEphemeralPublicKey=" + SerializationUtil.serializeToBase64(userEphemeralPublicKey).blockingGet() +
                ", verificationTag=" + SerializationUtil.serializeToBase64(verificationTag).blockingGet() +
                '}';
    }

}
