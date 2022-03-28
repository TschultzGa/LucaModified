package de.culture4life.luca.util

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.cert.CertPathValidatorException

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class CertificateUtilTest : LucaUnitTest() {

    @Test
    fun loadCertificate_validFile_loadsCertificate() {
        val certificate = CertificateUtil.loadCertificate("staging_root_ca.pem", application)
        assertEquals("CN=luca Dev Cluster Root CA, O=luca Dev, L=Berlin, ST=Berlin, C=DE", certificate.issuerDN.name)
    }

    @Test
    fun checkCertificateChain_validChain_completes() {
        val rootCertificate = CertificateUtil.loadCertificate("production_root_ca.pem", application)
        val intermediateCertificate = CertificateUtil.loadCertificate("production_intermediate_ca.pem", application)
        CertificateUtil.checkCertificateChain(rootCertificate, listOf(intermediateCertificate))
    }

    @Test(expected = CertPathValidatorException::class)
    fun checkCertificateChain_invalidChain_throws() {
        val rootCertificate = CertificateUtil.loadCertificate("production_root_ca.pem", application)
        val intermediateCertificate = CertificateUtil.loadCertificate("staging_intermediate_ca.pem", application)
        CertificateUtil.checkCertificateChain(rootCertificate, listOf(intermediateCertificate))
    }

    @Test
    fun checkCertificateChain_revokedRootCertificate_throws() {
        // TODO: 01.07.21 implement
    }
}
