package de.culture4life.luca.attestation

open class AttestationException : Exception {
    constructor(message: String) : super(message)
    constructor(cause: Throwable?) : super(cause)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
