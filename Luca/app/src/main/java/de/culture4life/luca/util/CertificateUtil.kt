package de.culture4life.luca.util

import android.annotation.SuppressLint
import android.content.Context
import de.culture4life.luca.LucaApplication
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.IETFUtils
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.cert.*


object CertificateUtil {

    private const val TYPE_X509 = "X509"
    private const val ALGORITHM_PKIX = "PKIX"

    @JvmStatic
    @SuppressLint("NewApi") // only used in testing
    fun loadCertificate(fileName: String, context: Context): X509Certificate {
        val stream: InputStream = if (LucaApplication.isRunningUnitTests()) {
            Files.newInputStream(Paths.get("src/main/assets/$fileName"))
        } else {
            context.assets.open(fileName)
        }
        return loadCertificate(stream)
    }

    @JvmStatic
    fun loadCertificate(inputStream: InputStream): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance(TYPE_X509)
        return certificateFactory.generateCertificate(inputStream) as X509Certificate
    }

    fun checkCertificateChain(rootCertificate: X509Certificate, certificateChain: List<X509Certificate>) {
        rootCertificate.checkValidity()
        certificateChain.forEach(X509Certificate::checkValidity)

        val certPathValidator = CertPathValidator.getInstance(ALGORITHM_PKIX)
        val certificateFactory = CertificateFactory.getInstance(TYPE_X509)
        val certPath = certificateFactory.generateCertPath(certificateChain)

        // validate the cert path
        val parameters = PKIXParameters(setOf(TrustAnchor(rootCertificate, null)))
        parameters.isRevocationEnabled = false
        certPathValidator.validate(certPath, parameters)
    }

}

fun X509Certificate.getX500Name(): X500Name {
    return X500Name.getInstance(this.subjectX500Principal.encoded)
}

fun X500Name.getRdnAsString(attributeType: ASN1ObjectIdentifier): String {
    val rdn = this.getRDNs(attributeType).first()
    return IETFUtils.valueToString(rdn.first.value)
}