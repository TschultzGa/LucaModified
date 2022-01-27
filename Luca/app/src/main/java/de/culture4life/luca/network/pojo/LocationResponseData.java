package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class LocationResponseData implements Serializable {

    public enum EntryPolicy {
        @SerializedName("2G") POLICY_2G,
        @SerializedName("3G") POLICY_3G
    }

    @SerializedName("locationId")
    private String locationId;

    @SerializedName("locationName")
    private String areaName;

    @SerializedName("groupName")
    private String groupName;

    @SerializedName("lat")
    private double latitude;

    @SerializedName("lng")
    private double longitude;

    @SerializedName("radius")
    private long radius;

    @SerializedName("isPrivate")
    private boolean isPrivate;

    @SerializedName("isContactDataMandatory")
    private boolean isContactDataMandatory = true;

    @SerializedName("entryPolicy")
    private EntryPolicy entryPolicy = null;

    @SerializedName("averageCheckinTime")
    private long averageCheckInDuration;

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getRadius() {
        return radius;
    }

    public void setRadius(long radius) {
        this.radius = radius;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public boolean isContactDataMandatory() {
        return isContactDataMandatory;
    }

    public void setIsContactDataMandatory(boolean isMandatory) {
        this.isContactDataMandatory = isMandatory;
    }

    public EntryPolicy getEntryPolicy() {
        return entryPolicy;
    }

    public void setEntryPolicy(EntryPolicy entryPolicy) {
        this.entryPolicy = entryPolicy;
    }

    public long getAverageCheckInDuration() {
        return averageCheckInDuration;
    }

    public void setAverageCheckInDuration(long averageCheckInDuration) {
        this.averageCheckInDuration = averageCheckInDuration;
    }

    @Override
    public String toString() {
        return "LocationResponseData{" +
                "locationId='" + locationId + '\'' +
                ", areaName='" + areaName + '\'' +
                ", groupName='" + groupName + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", radius=" + radius +
                ", isPrivate=" + isPrivate +
                ", isContactDataMandatory=" + isContactDataMandatory +
                ", entryPolicy=" + entryPolicy +
                ", averageCheckInDuration=" + averageCheckInDuration +
                '}';
    }
}
