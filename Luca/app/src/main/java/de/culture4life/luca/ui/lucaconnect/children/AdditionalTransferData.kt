package de.culture4life.luca.ui.lucaconnect.children

import de.culture4life.luca.registration.RegistrationData

data class AdditionalTransferData(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val email: String?,
    val street: String,
    val houseNumber: String,
    val city: String,
    val postalCode: String,
    val dateOfBirth: Long
) {
    constructor(registrationData: RegistrationData, dateOfBirth: Long) : this(
        firstName = registrationData.firstName!!,
        lastName = registrationData.lastName!!,
        phoneNumber = registrationData.phoneNumber!!,
        email = registrationData.email!!,
        street = registrationData.street!!,
        houseNumber = registrationData.houseNumber!!,
        city = registrationData.city!!,
        postalCode = registrationData.postalCode!!,
        dateOfBirth = dateOfBirth
    )
}
