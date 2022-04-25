package de.culture4life.luca.attestation

class SafetyNetAttestationException(cause: Throwable) : AttestationException("SafetyNet attestation failed", cause)
