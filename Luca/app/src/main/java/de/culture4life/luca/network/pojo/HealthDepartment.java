package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HealthDepartment {

    @SerializedName("departmentId")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("signedPublicHDEKP")
    private String encryptionKeyJwt;

    @SerializedName("signedPublicHDSKP")
    private String signingKeyJwt;

    @SerializedName("zipCodes")
    private List<String> zipCodes;

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

    public List<String> getZipCodes() {
        return zipCodes;
    }

    public void setZipCodes(List<String> zipCodes) {
        this.zipCodes = zipCodes;
    }

    @Override
    public String toString() {
        return "HealthDepartment{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", encryptionKeyJwt='" + encryptionKeyJwt + '\'' +
                ", signingKeyJwt='" + signingKeyJwt + '\'' +
                ", zipCodes='" + zipCodes + '\'' +
                '}';
    }
}
