package de.culture4life.luca.document.provider.baercode;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.testtools.rules.FixedTimeRule;

public class BaercodeCertificateTest extends LucaUnitTest {

    @Rule
    public FixedTimeRule fixedTimeRule = new FixedTimeRule("2022-01-15T15:30:00");

    private X509Certificate letsEncryptCert;

    @Before
    public void setUp() throws CertificateException, IOException {
        letsEncryptCert = BaercodeCertificate.createCertificate(Files.newInputStream(Paths.get("src/main/assets/le_root.crt")));
    }

    @Test
    @Ignore("certificate only valid until 4. April 2022, has to be regenerated or fixed by fixed datetime solution")
    public void verify_baercodeCertificateWithLetsEncrypt_succeeds() throws GeneralSecurityException, IOException {
        byte[] fileContent = getFileContent("src/test/assets/baercode.crt");
        List<X509Certificate> baercodeChain = BaercodeCertificate.createCertificateChain(fileContent);
        BaercodeCertificate.checkServerTrusted(letsEncryptCert, baercodeChain);
    }

    @Test(expected = CertPathValidatorException.class)
    public void verify_lucaCertificateWithLetsEncrypt_fails() throws GeneralSecurityException, IOException {
        byte[] fileContent = getFileContent("src/test/assets/luca.cer");
        List<X509Certificate> lucaChain = BaercodeCertificate.createCertificateChain(fileContent);
        BaercodeCertificate.checkServerTrusted(letsEncryptCert, lucaChain);
    }

    @Test(expected = CertPathValidatorException.class)
    public void verify_revokedLetsEncryptCertificate_fails() throws GeneralSecurityException, IOException {
        byte[] fileContent = getFileContent("src/test/assets/revoked-letsencrypt.cer");
        List<X509Certificate> revokedLetsEncryptChain = BaercodeCertificate.createCertificateChain(fileContent);
        BaercodeCertificate.checkServerTrusted(letsEncryptCert, revokedLetsEncryptChain);
    }

    public static byte[] getFileContent(String fileName) throws IOException {
        // TODO: 25.02.22 extract to test util class
        return Files.readAllBytes(Paths.get(fileName));
    }

}
