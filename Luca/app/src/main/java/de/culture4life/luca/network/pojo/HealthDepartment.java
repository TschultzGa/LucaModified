package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

public class HealthDepartment {

    @SerializedName("departmentId")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("signedPublicHDEKP")
    private String encryptionKeyJwt;

    @SerializedName("signedPublicHDSKP")
    private String signingKeyJwt;

    @SerializedName("connectEnabled")
    private boolean connectEnrollmentSupported;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEncryptionKeyJwt() {
        return encryptionKeyJwt;
    }

    public void setEncryptionKeyJwt(String encryptionKeyJwt) {
        this.encryptionKeyJwt = encryptionKeyJwt;
    }

    public String getSigningKeyJwt() {
        return signingKeyJwt;
    }

    public void setSigningKeyJwt(String signingKeyJwt) {
        this.signingKeyJwt = signingKeyJwt;
    }

    public boolean getConnectEnrollmentSupported() {
        return connectEnrollmentSupported;
    }

    public void setConnectEnrollmentSupported(boolean connectEnrollmentSupported) {
        this.connectEnrollmentSupported = connectEnrollmentSupported;
    }

    @Override
    public String toString() {
        return "HealthDepartment{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", encryptionKeyJwt='" + encryptionKeyJwt + '\'' +
                ", signingKeyJwt='" + signingKeyJwt + '\'' +
                ", connectEnrollmentSupported='" + connectEnrollmentSupported + '\'' +
                '}';
    }
}
