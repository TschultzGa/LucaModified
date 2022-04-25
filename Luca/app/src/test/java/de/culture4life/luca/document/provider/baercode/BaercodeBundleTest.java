package de.culture4life.luca.document.provider.baercode;

import org.junit.Assert;
import org.junit.Test;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.document.DocumentParsingException;

public class BaercodeBundleTest extends LucaUnitTest {

    @Test
    public void createPublicKey_fromBundleFile_isNotNull() throws Exception {
        BaercodeBundle baercodeBundle = BaercodeBundle.getTestBundle();
        BaercodeKey baercodeKey = baercodeBundle.getKey("Oh5xf/y3TSgFUGRSEYv1nw==");
        Assert.assertEquals(2, baercodeKey.getCredType());
        Assert.assertTrue(baercodeBundle.isExpired());

        ECPublicKey publicKey = BaercodeDocumentProvider.createPublicKey(baercodeKey);
        Assert.assertNotNull(publicKey);
    }

    @Test
    public void validateBundle_withBaCert_succeeds() throws Exception {
        BaercodeBundle baercodeBundle = BaercodeBundle.getTestBundle();
        PublicKey publicKey = new BaercodeCertificate(BaercodeCertificateTest.getFileContent("src/test/assets/baercode.crt")).getPublicKey();
        baercodeBundle.verify(publicKey);
    }

    @Test(expected = DocumentParsingException.class)
    public void validateBundle_withLetsEncryptCert_fails() throws Exception {
        BaercodeBundle baercodeBundle = BaercodeBundle.getTestBundle();
        PublicKey publicKey = new BaercodeCertificate(BaercodeCertificateTest.getFileContent("src/main/assets/le_root.crt")).getPublicKey();
        baercodeBundle.verify(publicKey);
    }

}
