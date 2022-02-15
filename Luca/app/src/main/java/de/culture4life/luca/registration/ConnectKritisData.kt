package de.culture4life.luca.registration

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.util.StringSanitizeUtil

data class ConnectKritisData(
    @Expose
    @SerializedName("isCriticalInfrastructure")
    val isCriticalInfrastructure: Boolean?,

    @Expose
    @SerializedName("isWorkingWithVulnerableGroup")
    val isWorkingWithVulnerableGroup: Boolean?,

    @Expose
    @SerializedName("industry")
    val industry: String? = null,

    @Expose
    @SerializedName("company")
    val company: String? = null
) {
    companion object {
        fun create(
            isCriticalInfrastructure: Boolean?,
            isWorkingWithVulnerableGroup: Boolean?,
            industry: String?,
            company: String?
        ): ConnectKritisData {
            return ConnectKritisData(
                isCriticalInfrastructure = isCriticalInfrastructure,
                isWorkingWithVulnerableGroup = isWorkingWithVulnerableGroup,
                industry = industry?.let { StringSanitizeUtil.sanitize(it).trim().ifEmpty { null } },
                company = company?.let { StringSanitizeUtil.sanitize(it).trim().ifEmpty { null } }
            )
        }
    }
}