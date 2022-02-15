package de.culture4life.luca.document;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.culture4life.luca.document.Document.TYPE_FAST;
import static de.culture4life.luca.document.Document.TYPE_PCR;
import static de.culture4life.luca.document.Document.TYPE_RECOVERY;
import static de.culture4life.luca.document.Document.TYPE_VACCINATION;
import static de.culture4life.luca.document.DocumentManager.HasDocumentCheckResult.INVALID_DOCUMENT;
import static de.culture4life.luca.document.DocumentManager.HasDocumentCheckResult.NO_DOCUMENT;
import static de.culture4life.luca.document.DocumentManager.HasDocumentCheckResult.VALID_DOCUMENT;
import static de.culture4life.luca.document.provider.appointment.AppointmentProviderTest.VALID_APPOINTMENT;
import static de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocumentProviderTest.EXPIRED_TEST_RESULT_TICKET_IO;
import static de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocumentProviderTest.UNVERIFIED_TEST_RESULT;
import static de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocumentProviderTest.VALID_TEST_RESULT_TICKET_IO;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.children.Child;
import de.culture4life.luca.children.ChildrenManager;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.document.DocumentManager.HasDocumentCheckResult;
import de.culture4life.luca.document.provider.ProvidedDocument;
import de.culture4life.luca.document.provider.appointment.Appointment;
import de.culture4life.luca.document.provider.eudcc.EudccDocumentProvider;
import de.culture4life.luca.document.provider.eudcc.EudccDocumentProviderTest;
import de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocument;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.DocumentProviderData;
import de.culture4life.luca.network.pojo.DocumentProviderDataList;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.RegistrationData;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class DocumentManagerTest extends LucaUnitTest {

    private static final String CHILD_EUDCC_FULLY_VACCINATED = "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0II$PPKHR D2J6K84D:RPIYFPXJ.MPM0SQHIZC4TPIFRMLNKNM8POCEUG*%NH$RSC9XHF4+5JJLCET7*KYD5$/IU7J$%2DU28:I /K%1TI$26-C1I5AT35-A0OI ZJY1BR.TANT+BF-6K4CIZ3E8AE-QD+PBNPCBLEH-B90I-CI%6N5AAP$M 52G+SB.V4Q56H0TJ1734LR6VR5VVB5VA81K0ECM8CXVDC8C90JK.A+ C/8DXEDKG0CGJB/S7-SN2H N37J3JFTULJ5CBR/S09T./0LWTKD33236J3TA3%*47%S/U456L7Y4/VIAY95J6QW6A$Q9G6PK9/1APEE6PP+ 5-PP:G9XF5-JU04AXIQM P7-5AQ5SW5PK9CZL0W56SP.E5BQ95ZM3762LEA.MLN1I%6$B69AELO1A-6W9EBIEV56V02F+659E5$LMIFT2T12AZHA.:0$UR6/PVKQZNN 1PE:M46D6O5+2QRBSP.4QRM2WIG86H62IK6T.SQ9PBQOP:A%SOD3KSYCNRBHX48L3G 2V508VVHYG";

    RegistrationManager registrationManager;
    DocumentManager documentManager;
    ChildrenManager childrenManager;

    private Document document;

    @Before
    public void setUp() {
        PreferencesManager preferencesManager = new PreferencesManager();
        NetworkManager networkManager = new NetworkManager();
        GenuinityManager genuinityManager = new GenuinityManager(preferencesManager, networkManager);
        CryptoManager cryptoManager = new CryptoManager(preferencesManager, networkManager, genuinityManager);
        registrationManager = spy(new RegistrationManager(preferencesManager, networkManager, cryptoManager));
        childrenManager = new ChildrenManager(preferencesManager, registrationManager);
        HistoryManager historyManager = new HistoryManager(preferencesManager, childrenManager);

        documentManager = spy(new DocumentManager(preferencesManager, networkManager, historyManager, cryptoManager, registrationManager, childrenManager));
        documentManager.initialize(application).blockingAwait();

        document = new Document();
        document.setId("12345");
        document.setType(Document.TYPE_FAST);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis());
        document.setOutcome(Document.OUTCOME_NEGATIVE);
    }

    @Test
    public void addDocument_validDocument_addsDocument() {
        documentManager.addDocument(document)
                .andThen(documentManager.getOrRestoreDocuments())
                .test()
                .assertValue(testResult -> testResult.getId().equals(this.document.getId()));
    }

    @Test
    public void addDocument_positiveTest_fails() {
        document.setOutcome(Document.OUTCOME_POSITIVE);
        documentManager.addDocument(document)
                .test()
                .assertError(TestResultPositiveException.class);
    }

    @Test
    public void generateEncodedDocumentHash_validDocument_expectedHash() {
        Document validDocument = new Document();
        validDocument.setHashableEncodedData(VALID_TEST_RESULT_TICKET_IO);

        documentManager.generateEncodedDocumentHash(validDocument)
                .test()
                .assertValue("Z/AAbdDXi/dZgJ27Wz6bAmLmbSOZ1MNHXEt34hrPQ4A=");
    }

    @Test
    public void reimportDocuments_noDocuments_completes() {
        documentManager.reImportDocuments()
                .test()
                .assertComplete();
    }

    @Test
    public void reimportDocuments_invalidDocuments_doesNotReImportInvalidDocuments() {
        Document invalidDocument = new OpenTestCheckDocument(UNVERIFIED_TEST_RESULT).getDocument();

        documentManager.addDocument(invalidDocument)
                .andThen(documentManager.reImportDocuments())
                .andThen(documentManager.getOrRestoreDocuments())
                .test()
                .assertNoValues();
    }

    @Test
    public void reimportDocuments_validDocuments_reImportsDocuments() {
        assumeTrue("Only run on debug where we ignore the expiry time", BuildConfig.DEBUG);
        doReturn(Completable.complete()).when(documentManager).redeemDocument(any());
        doReturn(Completable.complete()).when(documentManager).unredeemDocument(any());
        RegistrationData registrationData = new RegistrationData();
        registrationData.setFirstName("any");
        registrationData.setLastName("any");
        doReturn(Single.just(registrationData)).when(registrationManager).getRegistrationData();

        Document validDocument = new Appointment(VALID_APPOINTMENT).getDocument();
        Document invalidDocument = new OpenTestCheckDocument(UNVERIFIED_TEST_RESULT).getDocument();

        TestObserver<String> documentId = documentManager.addDocument(validDocument)
                .andThen(documentManager.addDocument(invalidDocument))
                .andThen(documentManager.reImportDocuments())
                .andThen(documentManager.getOrRestoreDocuments())
                .map(Document::getId)
                .test();

        rxSchedulersRule.getTestScheduler().triggerActions();
        documentId.assertValue(validDocument.getId());

        verify(documentManager, times(2)).unredeemDocument(any(Document.class));
        verify(documentManager, times(1)).redeemDocument(any(Document.class));
    }

    @Test
    public void reimportDocuments_validDocumentOfChild_reImportsIt() {
        assumeTrue("Only run on debug where we ignore the expiry time", BuildConfig.DEBUG);
        doReturn(Completable.complete()).when(documentManager).redeemDocument(any());
        doReturn(Completable.complete()).when(documentManager).unredeemDocument(any());
        RegistrationData registrationData = new RegistrationData();
        registrationData.setFirstName("Any");
        registrationData.setLastName("Parent");
        doReturn(Single.just(registrationData)).when(registrationManager).getRegistrationData();

        EudccDocumentProvider eudccDocumentProvider = spy(new EudccDocumentProvider(application));
        doReturn(Completable.complete()).when(eudccDocumentProvider).verify(CHILD_EUDCC_FULLY_VACCINATED);
        documentManager.setEudccDocumentProvider(eudccDocumentProvider);

        Document validDocument = childrenManager.addChild(new Child("Erika Dörte", "Dießner Musterfrau"))
                .andThen(documentManager.parseAndValidateEncodedDocument(CHILD_EUDCC_FULLY_VACCINATED))
                .blockingGet();

        documentManager.addDocument(validDocument)
                .andThen(documentManager.reImportDocuments())
                .andThen(documentManager.getOrRestoreDocuments())
                .map(Document::getId)
                .test()
                .assertValueCount(1)
                .assertValue(validDocument.getId());

        verify(documentManager, times(1)).unredeemDocument(any(Document.class));
        verify(documentManager, times(1)).redeemDocument(any(Document.class));
    }

    @Test
    public void reVerifyDocuments_previouslyVerifiedNowUnverifiable_updatesVerifiedStatus() {
        EudccDocumentProvider eudccDocumentProvider = spy(new EudccDocumentProvider(application));
        doReturn(Completable.error(new DocumentVerificationException(DocumentVerificationException.Reason.INVALID_SIGNATURE)))
                .when(eudccDocumentProvider).verify(EudccDocumentProviderTest.EUDCC_FULLY_VACCINATED);
        documentManager.setEudccDocumentProvider(eudccDocumentProvider);

        TestObserver<Document> parseDocument = eudccDocumentProvider.parse(EudccDocumentProviderTest.EUDCC_FULLY_VACCINATED)
                .map(ProvidedDocument::getDocument)
                .doOnSuccess(document -> document.setVerified(true))
                .flatMapCompletable(document -> documentManager.addDocument(document))
                .andThen(documentManager.reVerifyDocuments())
                .andThen(documentManager.getOrRestoreDocuments())
                .test();
        rxSchedulersRule.getTestScheduler().triggerActions();
        parseDocument.assertValue(document -> !document.isVerified());
    }

    @Test
    public void addDocument_expiredDocument_throwsInRelease() {
        assumeTrue("Only run on release where we don't ignore the expiry time", !BuildConfig.DEBUG);

        Document expiredDocument = new OpenTestCheckDocument(EXPIRED_TEST_RESULT_TICKET_IO).getDocument();

        documentManager.addDocument(expiredDocument)
                .test()
                .assertError(DocumentExpiredException.class);
    }

    @Test
    public void clearDocuments_withExistingResult_hasNoValues() {
        documentManager.addDocument(document)
                .andThen(documentManager.clearDocuments())
                .andThen(documentManager.getOrRestoreDocuments())
                .test()
                .assertNoValues();
    }

    @Test
    public void deleteDocument_validId_removesFromDocuments() {
        documentManager.addDocument(document)
                .andThen(documentManager.deleteDocument(document.getId()))
                .andThen(documentManager.getOrRestoreDocuments())
                .test()
                .assertNoValues();
    }

    @Test
    public void deleteDocument_invalidId_doesNotChangeCount() {
        documentManager.addDocument(document)
                .andThen(documentManager.deleteDocument("does not exist"))
                .andThen(documentManager.getOrRestoreDocuments())
                .test()
                .assertValueCount(1);
    }

    @Test
    public void deleteTestedBefore_oldTest_remove() {
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(3));
        documentManager.addDocument(document)
                .andThen(documentManager.deleteExpiredDocuments())
                .andThen(documentManager.getOrRestoreDocuments())
                .test()
                .assertNoValues();
    }

    @Test
    public void deleteTestedBefore_currentTest_keep() {
        document.setTestingTimestamp(TimeUtil.getCurrentMillis());
        documentManager.addDocument(document)
                .andThen(documentManager.deleteExpiredDocuments())
                .andThen(documentManager.getOrRestoreDocuments())
                .test()
                .assertValueCount(1);
    }

    @Test
    public void getDocumentProviderData_cachedFingerprint_emitsMatchingCachedData() {
        String cachedFingerprint = "1";
        DocumentProviderDataList cachedDataList = new DocumentProviderDataList(Collections.singletonList(
                new DocumentProviderData("", "", cachedFingerprint)
        ));
        doReturn(Maybe.just(cachedDataList)).when(documentManager).restoreDocumentProviderDataListIfAvailable();
        doReturn(Single.error(new IllegalStateException("Should not be subscribed"))).when(documentManager).fetchDocumentProviderDataList();

        documentManager.getDocumentProviderData(cachedFingerprint)
                .test()
                .assertValue(documentProviderData -> documentProviderData.getFingerprint().equals(cachedFingerprint));
    }

    @Test
    public void getDocumentProviderData_newFingerprint_emitsMatchingFetchedDataAndCachesData() {
        String fetchedFingerprint = "2";
        DocumentProviderDataList fetchedDataList = new DocumentProviderDataList(Collections.singletonList(
                new DocumentProviderData("", "", fetchedFingerprint)
        ));
        doReturn(Single.just(fetchedDataList)).when(documentManager).fetchDocumentProviderDataList();

        documentManager.getDocumentProviderData(fetchedFingerprint)
                .test()
                .assertValue(documentProviderData -> documentProviderData.getFingerprint().equals(fetchedFingerprint));

        documentManager.restoreDocumentProviderDataListIfAvailable()
                .flatMapObservable(Observable::fromIterable)
                .test()
                .assertValue(documentProviderData -> fetchedFingerprint.equals(documentProviderData.getFingerprint()));
    }

    @Test
    public void getDocumentProviderData_unknownFingerprint_emitsAllFetchedData() {
        String fetchedFingerprint = "2";
        String unknownFingerprint = "";
        DocumentProviderDataList fetchedDataList = new DocumentProviderDataList(Collections.singletonList(
                new DocumentProviderData("", "", fetchedFingerprint)
        ));
        doReturn(Single.just(fetchedDataList)).when(documentManager).fetchDocumentProviderDataList();

        documentManager.getDocumentProviderData(unknownFingerprint)
                .toList()
                .map(List::size)
                .test()
                .assertValue(fetchedDataList.size());
    }

    @Test
    public void isTestResult_validUrls_returnsTrue() {
        assertTrue(DocumentManager.isTestResult("https://app.luca-app.de/webapp/testresult/#eyJ0eXAi..."));
    }

    @Test
    public void isTestResult_invalidUrls_returnsFalse() {
        assertFalse(DocumentManager.isTestResult("https://app.luca-app.de/webapp/meeting/e4e3c...#e30"));
        assertFalse(DocumentManager.isTestResult("https://app.luca-app.de/webapp/"));
        assertFalse(DocumentManager.isTestResult("https://www.google.com"));
        assertFalse(DocumentManager.isTestResult("https://www.google.com/webapp/testresult/#eyJ0eXAi..."));
        assertFalse(DocumentManager.isTestResult(""));
    }

    @Test
    public void hasVaccinationDocument_valid_returnsValid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_VACCINATION);
        when(document.isValidVaccination()).thenReturn(true);
        when(document.isVerified()).thenReturn(true);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasVaccinationDocument().test();

        // Then
        observer.await().assertValue(VALID_DOCUMENT);
    }

    @Test
    public void hasVaccinationDocument_invalid_returnsInvalid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_VACCINATION);
        when(document.isValidVaccination()).thenReturn(false);
        when(document.isVerified()).thenReturn(false);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasVaccinationDocument().test();

        // Then
        observer.await().assertValue(INVALID_DOCUMENT);
    }

    @Test
    public void hasVaccinationDocument_none_returnsNone() throws InterruptedException {
        // Given
        doReturn(Observable.empty()).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasVaccinationDocument().test();

        // Then
        observer.await().assertValue(NO_DOCUMENT);
    }

    @Test
    public void hasRecoveryDocument_valid_returnsValid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_RECOVERY);
        when(document.isValid()).thenReturn(true);
        when(document.isVerified()).thenReturn(true);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasRecoveryDocument().test();

        // Then
        observer.await().assertValue(VALID_DOCUMENT);
    }

    @Test
    public void hasRecoveryDocument_invalid_returnsInvalid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_RECOVERY);
        when(document.isValidRecovery()).thenReturn(false);
        when(document.isVerified()).thenReturn(false);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasRecoveryDocument().test();

        // Then
        observer.await().assertValue(INVALID_DOCUMENT);
    }

    @Test
    public void hasRecoveryDocument_none_returnsNone() throws InterruptedException {
        // Given
        doReturn(Observable.empty()).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasRecoveryDocument().test();

        // Then
        observer.await().assertValue(NO_DOCUMENT);
    }

    @Test
    public void hasPcrDocument_valid_returnsValid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_PCR);
        when(document.isValidNegativeTestResult()).thenReturn(true);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasPcrTestDocument().test();

        // Then
        observer.await().assertValue(VALID_DOCUMENT);
    }

    @Test
    public void hasPcrDocument_invalid_returnsInvalid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_PCR);
        when(document.isValidNegativeTestResult()).thenReturn(false);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasPcrTestDocument().test();

        // Then
        observer.await().assertValue(INVALID_DOCUMENT);
    }

    @Test
    public void hasPcrDocument_none_returnsNone() throws InterruptedException {
        // Given
        doReturn(Observable.empty()).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasPcrTestDocument().test();

        // Then
        observer.await().assertValue(NO_DOCUMENT);
    }

    @Test
    public void hasQuickTestDocument_valid_returnsValid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_FAST);
        when(document.isValidNegativeTestResult()).thenReturn(true);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasQuickTestDocument().test();

        // Then
        observer.await().assertValue(VALID_DOCUMENT);
    }

    @Test
    public void hasQuickTestDocument_invalid_returnsInvalid() throws InterruptedException {
        // Given
        Document document = mock(Document.class);
        when(document.getType()).thenReturn(TYPE_FAST);
        when(document.isValidNegativeTestResult()).thenReturn(false);
        doReturn(Observable.just(document)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasQuickTestDocument().test();

        // Then
        observer.await().assertValue(INVALID_DOCUMENT);
    }

    @Test
    public void hasQuickTestDocument_none_returnsNone() throws InterruptedException {
        // Given
        doReturn(Observable.empty()).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasQuickTestDocument().test();

        // Then
        observer.await().assertValue(NO_DOCUMENT);
    }

    @Test
    public void hasMultipleDocuments_oneValid_returnsValid() throws InterruptedException {
        // Given
        Document invalidDocument1 = mock(Document.class);
        when(invalidDocument1.getType()).thenReturn(TYPE_VACCINATION);
        when(invalidDocument1.isValidVaccination()).thenReturn(false);
        when(invalidDocument1.isVerified()).thenReturn(false);

        Document invalidDocument2 = mock(Document.class);
        when(invalidDocument2.getType()).thenReturn(TYPE_FAST);
        when(invalidDocument2.isValidNegativeTestResult()).thenReturn(false);

        Document validDocument = mock(Document.class);
        when(validDocument.getType()).thenReturn(TYPE_VACCINATION);
        when(validDocument.isValidVaccination()).thenReturn(true);
        when(validDocument.isVerified()).thenReturn(true);

        doReturn(Observable.just(invalidDocument1, invalidDocument2, validDocument)).when(documentManager).getOrRestoreDocuments();

        // When
        TestObserver<HasDocumentCheckResult> observer = documentManager.hasVaccinationDocument().test();

        // Then
        observer.await().assertValue(VALID_DOCUMENT);
    }

}