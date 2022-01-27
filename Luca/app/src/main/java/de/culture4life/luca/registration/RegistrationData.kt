package de.culture4life.luca.registration

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.network.pojo.ContactData
import java.util.*

/**
 * Model of user-provided contact data, entered during registration.
 * Will be transferred into [ContactData] and encrypted before leaving the device.
 *
 * @see [Security
 * Overview: Guest Registration](https://www.luca-app.de/securityoverview/processes/guest_registration.html)
 */
data class RegistrationData(

    @SerializedName("id")
    @Expose
    var id: UUID? = null,

    @SerializedName("registrationTimestamp")
    @Expose
    var registrationTimestamp: Long = 0,

    @SerializedName("firstName")
    @Expose
    var firstName: String? = null,

    @SerializedName("lastName")
    @Expose
    var lastName: String? = null,

    @SerializedName("phoneNumber")
    @Expose
    var phoneNumber: String? = null,

    @SerializedName("email")
    @Expose
    var email: String? = null,

    @SerializedName("street")
    @Expose
    var street: String? = null,

    @SerializedName("houseNumber")
    @Expose
    var houseNumber: String? = null,

    @SerializedName("city")
    @Expose
    var city: String? = null,

    @SerializedName("postalCode")
    @Expose
    var postalCode: String? = null,

    ) {

    val fullName: String
        get() = person.getFullName()

    val person: Person
        get() = Person(firstName!!, lastName!!)

    val address: String
        get() = "$street $houseNumber, $postalCode $city"


    fun hasSameName(data: RegistrationData) = data.firstName == firstName && data.lastName == lastName
    fun hasSamePostalCode(data: RegistrationData) = data.postalCode == postalCode
}