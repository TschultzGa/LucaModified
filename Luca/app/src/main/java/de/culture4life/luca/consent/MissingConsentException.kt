package de.culture4life.luca.consent

class MissingConsentException(id: String) : Exception("Consent not approved for $id")