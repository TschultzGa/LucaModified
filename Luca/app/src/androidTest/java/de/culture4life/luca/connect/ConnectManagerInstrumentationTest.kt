package de.culture4life.luca.connect

import com.google.gson.Gson
import de.culture4life.luca.LucaInstrumentationTest
import de.culture4life.luca.crypto.AsymmetricCipherProvider
import de.culture4life.luca.crypto.decodeFromBase64
import de.culture4life.luca.crypto.decodeFromHex
import de.culture4life.luca.document.Document
import de.culture4life.luca.health.ResponsibleHealthDepartment
import de.culture4life.luca.network.pojo.ConnectMessageResponseData
import de.culture4life.luca.registration.RegistrationData
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import timber.log.Timber

class ConnectManagerInstrumentationTest : LucaInstrumentationTest() {

    private lateinit var connectManager: ConnectManager
    private val healthDepartmentId = "32baf484-bfa6-41fa-bf4d-5c9ca08f1ae8"
    private val healthDepartmentPublicKey = Single.just("BAPT3NPL+dNB6BwFUvdxaeTfrjfl99Uj1om5BF73M2TMi44cvj0qFjmqJGidrTaoFlBcQOOxoCmpKVKjA8y2C78=")
        .map { it.decodeFromBase64() }
        .flatMap { AsymmetricCipherProvider.decodePublicKey(it) }
        .blockingGet()

    private val registrationData = RegistrationData().apply {
        firstName = "Erika"
        lastName = "Mustermann"
        phoneNumber = "+491711234567"
        email = "erika.mustermann@example.de"
        street = "Street"
        houseNumber = "123"
        postalCode = "12345"
        city = "City"
    }
    private val document = Document().apply {
        encodedData =
            "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0IIAYR*CM.W4:2F \$C7SPQ%9ORP9.SURC/GPWBI\$C9UDBQEAJJKKKMEC8.-B97U: K8*NHQ2VAP1US1Q7OKHHUEPOP%WUXQ72QETZU3UQRVULKHWWU:16Q8Q*7R-PP9\$PB/9OK5JWEMN1/9VSL1Y813.UNNUR+UK0VF/94O5%ZE/NEVTEJAVX5NGTUY*U9/9-3APF6:66A\$QX76LZ6Q59WPDN*I4OIMEDTJCJKDLEDL9CZTAKBI/8D:8DKTDL+S/15A+2XEN QT QTHC31M3+E3+T4D-4HRVUMNMD3323623423.LJX/KR968X2+36/-KW10SW6A\$Q836BPK$*SQDKVLI7VHB\$FNXUJRH0LH%Y2 UQ/RONSGWLIML5YO9OUUMK9WLIK*L5R1G-VOXL2VFLW5BWOM-PDNK7NGXH43R3/YF9\$R8SK3 EAM2-UL779%*IN89:JMXDA+N98FMHJL*TJZTJUJMYCHCFDOUGOPU5L6EFUZM1GQAMJJ8M7+HPPZILSP6PO91O4 9U851.IE3B2IEJ-9XWDK/M/MOG+T/3Q3-I*718$401JN9BG0E:ED1VJ/AK+K70002EKX4"
    }

    @Before
    fun setup() {
        connectManager = spy(getInitializedManager(application.connectManager))

        whenever(connectManager.getHealthDepartmentId()).then { Single.just(healthDepartmentId) }
        whenever(connectManager.getHealthDepartmentPublicKey()).then { Single.just(healthDepartmentPublicKey) }
        whenever(connectManager.getRegistrationData()).then { Single.just(registrationData) }
        whenever(connectManager.getLatestCovidCertificates()).then { Observable.just(document) }
    }

    @After
    fun teardown() {
        connectManager.clearContactArchive()
            .onErrorComplete()
            .blockingAwait()
    }

    @Test
    @Ignore("For manual invocation only")
    fun enroll_validData_completes() {
        connectManager.enroll()
            .andThen(connectManager.getContactArchiveEntries())
            .test()
            .await()
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun generateEnrollmentRequestData_validData_completes() {
        connectManager.generateEnrollmentRequestData()
            .map(Gson()::toJson)
            .doOnSuccess { Timber.i("Request data JSON: %s", it) }
            .test()
            .await()
            .assertComplete()
    }

    @Test
    fun unEnroll_afterEnrollment_completes() {
        connectManager.enroll()
            .andThen(connectManager.unEnroll())
            .andThen(connectManager.getContactArchiveEntries())
            .test()
            .await()
            .assertValueCount(0)
            .assertComplete()
    }

    @Test
    fun generateUnEnrollmentRequestData_validData_completes() {
        connectManager.generateUnEnrollmentRequestData("fd984282-1006-43a2-8c28-66de34a661ae")
            .map(Gson()::toJson)
            .doOnSuccess { Timber.i("Request data JSON: %s", it) }
            .test()
            .await()
            .assertComplete()
    }

    @Test
    fun fetchNewMessages_validData_completes() {
        whenever(connectManager.getHealthDepartmentId()).then { Single.just("52d4fe63-2abd-48fc-83fd-628df2c088a1") }
        whenever(connectManager.getHealthDepartmentPublicKey()).then {
            Single.just("AsLBHAoaGnzRh2jIIiRnRNTtWSs48N8iSemTbneCXWvT") // 02c2c11c0a1a1a7cd18768c822246744d4ed592b38f0df2249e9936e77825d6bd3
                .map { it.decodeFromBase64() }
                .flatMap { AsymmetricCipherProvider.decodePublicKey(it) }
        }
        whenever(connectManager.getNotificationId()).then { Single.just("6Bgi9iDKj4VUYMv5g4dAhw==") } // e81822f620ca8f855460cbf983874087
        whenever(connectManager.generateRoundedTimestampsSinceLastUpdate()).then { Observable.just(1639754400000) }

        connectManager.fetchNewMessages()
            .test()
            .await()
            .assertComplete()
    }

    @Test
    fun decryptMessageResponseData_validResponse_decryptsMessage() {
        val responseData = ConnectMessageResponseData(
            id = "SOocVE1DFGhLMX5XXHELSg==",
            data = "a0VhZrdpg90z88GEXfwjcCeXMeFK+SNES4rKyQxTHakXYvniKdPLLz30mttg8JBH+awlnDSnfIHiypNxEa+o0vNOuukONAkEcfmH5EEhf641T9kLSXpn6cvFIbLS2w/A7A==",
            iv = "kl7SH5KjuTZuTtLeMwY1IA==",
            mac = "h+tOSLA8B242lCFAUyzVV5cNXYGIN9VaGOAm+7e2q0Y=",
            timestamp = 1639754454
        )
        val department = ResponsibleHealthDepartment(
            id = "52d4fe63-2abd-48fc-83fd-628df2c088a1",
            name = "",
            publicHDEKP = "BC2/k5zXF2Yu+Ix2jlDK4bexZDCVWkXgCNCXOTBpIMc8TCAwpZbR75zwMWlfW+USjMOzIRKKXWEDALxstVvkU80=",
            publicHDSKP = "",
            connectEnrollmentSupported = true,
            postalCode = "12345"
        )
        val messageEncryptionPrivateKey =
            AsymmetricCipherProvider.decodePrivateKey("1317d0dc4a6ab6182c62a1681f3fa53d526f55e024cb9ce63011903cf18d8239".decodeFromHex())
                .blockingGet()

        connectManager.decryptMessageResponseData(responseData, department, messageEncryptionPrivateKey)
            .map(::String)
            .test()
            .assertValue("{\"sub\":\"TBD Bitte zum PCR test gehen\",\"msg\":\"TBD das ist die Nachricht um zum PCR Test zu gehen\"}")
    }

}