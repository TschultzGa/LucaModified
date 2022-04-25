package de.culture4life.luca.document.provider.eudcc

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.document.Document
import de.culture4life.luca.document.DocumentParsingException
import de.culture4life.luca.document.DocumentVerificationException
import de.culture4life.luca.document.provider.baercode.BaercodeTestResultProviderTest
import de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocumentProviderTest
import de.culture4life.luca.registration.Person
import io.reactivex.rxjava3.core.Observable
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`

@ExperimentalUnsignedTypes
class EudccDocumentProviderTest : LucaUnitTest() {

    private lateinit var provider: EudccDocumentProvider

    @Before
    fun setUp() {
        provider = spy(EudccDocumentProvider(application))
        `when`(provider.fetchSigningKeys()).thenReturn(
            Observable.just(
                EudccSigningKey(
                    "DSC",
                    "DE",
                    "yWCRdph8XJs=",
                    "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",
                    "MIAGCSqGSIb3DQEHAqCAMIACAQExDzANBglghkgBZQMEAgEFADCABgkqhkiG9w0BBwEAAKCAMIIFTjCCBDagAwIBAgIQeQJYn+2D5dqB2rpatIzUezANBgkqhkiG9w0BAQsFADBQMQswCQYDVQQGEwJERTEVMBMGA1UECgwMRC1UcnVzdCBHbWJIMSowKAYDVQQDDCFELVRSVVNUIExpbWl0ZWQgQmFzaWMgQ0EgMS0yIDIwMTkwHhcNMjEwNTIzMTk1ODI3WhcNMjExMTIyMjA1ODI3WjCBizELMAkGA1UEBhMCREUxFDASBgNVBAoTC3ViaXJjaCBHbWJIMSEwHwYDVQQDExh1cGxvYWQuZGUuZHNjLnViaXJjaC5jb20xDjAMBgNVBAcTBUtvZWxuMRUwEwYDVQQFEwxDU00wMjYyOTQyNjUxHDAaBgNVBAgTE05vcmRyaGVpbi1XZXN0ZmFsZW4wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS7vWbF4ktHYoVMgQGN5PpIDUxvyTJ5NJ3m4wmceQzgwmSIuX2y/0N1H/tcBhFEbzKhTZs3p6bSilCB4fD230fRo4ICsTCCAq0wEwYDVR0lBAwwCgYIKwYBBQUHAwIwHwYDVR0jBBgwFoAU0A0+3Aiv40EIZuDc8vqZai3fGLkwggEvBggrBgEFBQcBAQSCASEwggEdMEUGCCsGAQUFBzABhjlodHRwOi8vZC10cnVzdC1saW1pdGVkLWJhc2ljLWNhLTEtMi0yMDE5Lm9jc3AuZC10cnVzdC5uZXQwUAYIKwYBBQUHMAKGRGh0dHA6Ly93d3cuZC10cnVzdC5uZXQvY2dpLWJpbi9ELVRSVVNUX0xpbWl0ZWRfQmFzaWNfQ0FfMS0yXzIwMTkuY3J0MIGBBggrBgEFBQcwAoZ1bGRhcDovL2RpcmVjdG9yeS5kLXRydXN0Lm5ldC9DTj1ELVRSVVNUJTIwTGltaXRlZCUyMEJhc2ljJTIwQ0ElMjAxLTIlMjAyMDE5LE89RC1UcnVzdCUyMEdtYkgsQz1ERT9jQUNlcnRpZmljYXRlP2Jhc2U/MBgGA1UdIAQRMA8wDQYLKwYBBAGlNAKDdAEwgdMGA1UdHwSByzCByDCBxaCBwqCBv4ZAaHR0cDovL2NybC5kLXRydXN0Lm5ldC9jcmwvZC10cnVzdF9saW1pdGVkX2Jhc2ljX2NhXzEtMl8yMDE5LmNybIZ7bGRhcDovL2RpcmVjdG9yeS5kLXRydXN0Lm5ldC9DTj1ELVRSVVNUJTIwTGltaXRlZCUyMEJhc2ljJTIwQ0ElMjAxLTIlMjAyMDE5LE89RC1UcnVzdCUyMEdtYkgsQz1ERT9jZXJ0aWZpY2F0ZXJldm9jYXRpb25saXN0MB0GA1UdDgQWBBR3URlrkdxR+aUZUhApAz5uZmJTAzAOBgNVHQ8BAf8EBAMCBLAwIwYDVR0RBBwwGoIYdXBsb2FkLmRlLmRzYy51YmlyY2guY29tMA0GCSqGSIb3DQEBCwUAA4IBAQAizlIQ5L37J6KF4Is7foLR6UeYBA/ibUXB333T7mCIJZ/VnZ+AUNz5BqCKTnIWEN+LyAxbKtAeuX45nDv4OuwT9f0IO4ZvK4aFexZk3/Ur33uzlSPC8t84kLe3kFQG+i2IE/3guYJsm+YRQrOKcfAr/0wONbbrSH+sOOUPYAGuAAok1WGhJDs90QEswWS9LYAKhYdmeBAyFblOIgEcyvxj4MEBTEbqj7LEZlg5Fsh5ne0twwgRGKZIs3gQCto0Togzh6CMI6B63kz1YFDNyorUaln20DiyTv2UI1y6cKqAaStS19tGej0BRwVCL6ShzS3qp3Q+4wf53bJbzw8um6ybAAAxggG2MIIBsgIBATBkMFAxCzAJBgNVBAYTAkRFMRUwEwYDVQQKDAxELVRydXN0IEdtYkgxKjAoBgNVBAMMIUQtVFJVU1QgTGltaXRlZCBCYXNpYyBDQSAxLTIgMjAxOQIQeQJYn+2D5dqB2rpatIzUezALBglghkgBZQMEAgGggeQwGAYJKoZIhvcNAQkDMQsGCSqGSIb3DQEHATAcBgkqhkiG9w0BCQUxDxcNMjEwNjAzMTcwOTExWjAvBgkqhkiG9w0BCQQxIgQgyWCRdph8XJsbsnzH7bDul9pZsflGDL/dLNWbPkP0cUUweQYJKoZIhvcNAQkPMWwwajALBglghkgBZQMEASowCwYJYIZIAWUDBAEWMAsGCWCGSAFlAwQBAjAKBggqhkiG9w0DBzAOBggqhkiG9w0DAgICAIAwDQYIKoZIhvcNAwICAUAwBwYFKw4DAgcwDQYIKoZIhvcNAwICASgwCgYIKoZIzj0EAwIERzBFAiEAuKnZ9ACdkc9UlHa1lOWc12WpTU2z7Yl7Wieq964zukECIHKYNn9KFT+Ny8XtK1zeCTD4h6AvFhrWL7oMgVmi/AE9AAAAAAAA",
                    "c9609176987c5c9b1bb27cc7edb0ee97da59b1f9460cbfdd2cd59b3e43f47145",
                    "2021-06-03T19:09:10+02:00"
                )
            )
        )
    }

    @Test
    fun canParse_validData_emitsTrue() {
        provider.canParse(EUDCC_TEST_RESULT)
            .test()
            .assertValue(true)
    }

    @Test
    fun canParse_invalidData_emitsFalse() {
        provider.canParse("anything")
            .test()
            .assertValue(false)
    }

    @Test
    fun canParse_ticketIoDocument_emitsFalse() {
        provider.canParse(OpenTestCheckDocumentProviderTest.VALID_TEST_RESULT_TICKET_IO)
            .test()
            .assertValue(false)
    }

    @Test
    fun canParse_baercodeDocument_emitsFalse() {
        provider.canParse(BaercodeTestResultProviderTest.TEST_QR_CODE)
            .test()
            .assertValue(false)
    }

    @Test
    fun parse_fullyVaccinated_setsCorrectValues() {
        with(provider.parse(EUDCC_FULLY_VACCINATED).blockingGet().document) {
            assertEquals("Erika Dörte", firstName)
            assertEquals("Dießner Musterfrau", lastName)
            assertEquals(Document.TYPE_VACCINATION, type)
            assertEquals(Document.OUTCOME_FULLY_IMMUNE, outcome)
            assertEquals(1643241600000, testingTimestamp)
            assertEquals(1643241600000, resultTimestamp)
        }
    }

    @Test
    fun parse_bulgariaFullyVaccinated_setsCorrectValues() {
        with(provider.parse(EUDCC_BG_FULLY_VACCINATED).blockingGet().document) {
            assertEquals("СТАМО ГЕОРГИЕВ", firstName)
            assertEquals("ПЕТКОВ", lastName)
            assertEquals(Document.TYPE_VACCINATION, type)
            assertEquals(Document.OUTCOME_FULLY_IMMUNE, outcome)
        }
    }

    @Test
    fun parse_partiallyVaccinated_setsCorrectValues() {
        with(provider.parse(EUDCC_PARTIALLY_VACCINATED).blockingGet().document) {
            assertEquals("Erika", firstName)
            assertEquals("Mustermann", lastName)
            assertEquals("Robert Koch-Institut", labName)
            assertEquals(Document.TYPE_VACCINATION, type)
            assertEquals(Document.OUTCOME_PARTIALLY_IMMUNE, outcome)
            assertEquals("urn:uvci:01:DE:12345XYZ1", hashableEncodedData)
        }
    }

    @Test
    fun parse_fullyVaccinatedWithThirdDose_setsCorrectValues() {
        with(provider.parse(EUDCC_THIRD_DOSE).blockingGet().document) {
            assertEquals("John", firstName)
            assertEquals("Doe", lastName)
            assertEquals(Document.TYPE_VACCINATION, type)
            assertEquals(Document.OUTCOME_FULLY_IMMUNE, outcome)
        }
    }

    @Test
    fun parse_validTestData_setsCorrectValues() {
        val eudccDocument = provider.parse(EUDCC_TEST_RESULT).blockingGet()
        assertEquals(-170035200000, eudccDocument.document.dateOfBirth) // "1964-08-12"
        with(eudccDocument.document) {
            assertEquals("Erika", firstName)
            assertEquals("Mustermann", lastName)
            assertEquals(Document.TYPE_FAST, type)
            assertEquals(Document.OUTCOME_NEGATIVE, outcome)
            assertEquals("Testzentrum Köln Hbf", labDoctorName)
            assertEquals("Robert Koch-Institut", labName)
            assertEquals(1643293200000, testingTimestamp)
            assertEquals(1643293200000, resultTimestamp)
            assertEquals("urn:uvci:01DE/IZ12345A/5CWLU12RNOB9RXSEOP6FG8#W", hashableEncodedData)
        }
    }

    @Test
    fun parse_testDataWithSpecialDate_setsCorrectValues() {
        with(provider.parse(EUDCC_CY_TEST_RESULT).blockingGet().document) {
            assertEquals("Francisco", firstName)
            assertEquals("Garcia Miguel", lastName)
            assertEquals(1623060000110, testingTimestamp)
            assertEquals(1623061800701, resultTimestamp)
        }
    }

    @Test
    fun parse_recoveredCertificate_setsCorrectValues() {
        with(provider.parse(EUDCC_RECOVERED).blockingGet().document) {
            assertEquals("Erika", firstName)
            assertEquals("Mustermann", lastName)
            assertEquals("Robert Koch-Institut", labName)
            assertEquals(Document.TYPE_RECOVERY, type)
            assertEquals(Document.OUTCOME_FULLY_IMMUNE, outcome)
            assertEquals(1643241600000, testingTimestamp)
            assertEquals(1643241600000, resultTimestamp)
            assertEquals(1643241600000, validityStartTimestamp)
            assertEquals(1656288000000, expirationTimestamp)
            assertEquals("urn:uvci:01DE/5CWLU12RNOB9RXSEOP6FG8#W", hashableEncodedData)
        }
    }

    @Test
    fun parseDateAndTime_differentDateStyles_canParse() {
        assertEquals(1622370615000, "2021-05-30T10:30:15Z".parseDate())
        assertEquals(1623016799000, "2021-06-06T23:59:59+0200".parseDate())
    }

    @Test
    fun validate_correctName_completes() {
        val person = Person("Erika", "Mustermann")
        provider.parse(EUDCC_TEST_RESULT)
            .flatMapCompletable { result -> provider.validate(result, person) }
            .test()
            .assertComplete()
    }

    @Test
    fun validate_wrongName_fails() {
        val person = Person("Hans", "Wurst")
        provider.parse(EUDCC_TEST_RESULT)
            .flatMapCompletable { result -> provider.validate(result, person) }
            .test()
            .assertError(DocumentVerificationException::class.java)
    }

    @Test
    fun validate_fakedDocument_fails() {
        provider.parse(EUDCC_FAKED_VACCINATION)
            .test()
            .assertError(DocumentParsingException::class.java)
    }

    @Ignore("Missing test data")
    @Test
    fun validateSignature_validSignature_completes() {
        // TODO: 12.10.21 get test data with valid signature
        provider.verifySignature(EUDCC_TEST_RESULT)
            .test()
            .await()
            .assertComplete()
    }

    @Test
    fun validateSignature_invalidSignature_fails() {
        provider.verifySignature(EUDCC_FAKED_VACCINATION)
            .test()
            .await()
            .assertError(DocumentVerificationException::class.java)
    }

    companion object {
        const val EUDCC_FULLY_VACCINATED =
            "HC1:6BF180190T9WTWGSLKC 4769R174TF//5P30FBBXL2WY0AHCBEPFN01FDKDT*70J+9D97TK0F90\$PC5\$CUZC\$\$5Y\$5TPCBEC7ZKW.CXPEQED*WO1/DBJETZ9 QE5\$C .CJEC JC8/D3Z8WEDOCC8V8-2N7WELPCG/DXJDIZAITA9IANB8.+9I3D7WEGY8/B9:B8GVC*JC1A6G%63W5Q47*96KECTHG4KCD3DX47B46IL6646H*6Z/E5JD%96IA74R6646307Q\$D.UDRYA 96NF6L/5SW6Y57B\$D% D3IA4W5646946846.96XJC\$+D3KC.SCXJCCWENF6PF63W5Y96UF6WJCT3EHS8%JC QE/IAYJC5LEW34U3ET7DXC9 QE-ED8%EWJC0FD4X4:KEPH7M/ESDD746LG7\$X8TB8ENA.Q667B7DBEL6657IS8X59Z09%M927BTX64XM6Z5++QQN7%WBD5Q174Q/R87PLRRO7DI7B9HMG:4N0A/RJ2NEGNBNVH10DI-78MR/8EOOIK.P: QFPPGBKL-570I82I33MIAG"
        private const val EUDCC_PARTIALLY_VACCINATED =
            "HC1:6BFE70X90T9WTWGSLKC 4769R174TF//5P302BB5XK3F3X U65LBF3IIF+-4BJCSS9Y50.FK8ZKO/EZKEZ967L6C56..DX%DZJC6/DTZ9 QE5\$CB\$DA/D JC1/D3Z8WED1ECW.CCWE.Y92OAGY8MY9L+9MPCG/D5 C5IA5N9JPCT3E6JD646CA78465W5X577:EDOL9WEQDD+Q6TW6FA7C466KCN9E%961A6DL6FA7D46.JCP9EJY8L/5M/5546.96VF6.JCBECB1A-:8\$966469L6OF6VX6FVC*70KQEPD0LVC6JD846KF67465W5307UPC1JCWY8+EDXVET3E5\$CSUE6O9NPCSW5F/DBWENWE4WEOPCQ4F:KEPH7M/ESDD746LG7\$X8*96DL6LX6EDB27B:PH:D80O9+\$ODY5PME-CEOA74D7F49T/OLD7G11RGOU.LW*L4DJL2P7:TAD5GW0INFIZK7265IHE1V3THG1NSKM*KN-YR9E1W\$M/2F"
        private const val EUDCC_TEST_RESULT =
            "HC1:6BFN80O80T9WTWGSLKC 4769R174TF//5P30FBB+B5WY0AHCU8PFN01FD*7T*70J+9D97TK0F90\$PC5\$CUZC\$\$5Y\$5TPCBEC7ZKW.CXJD7%E7WE/KECEC/.DI3D5 C*KE*PDLPCG/DXJDIZAITA9IANB8.+9I3D7WEGY8/B9:B8GVC*JC1A6G%63W5Q47*96IECTHG4KCD3DX47B46IL6646H*6MWEWJDA6A:961A6Q47EM6KWEKDDC%6-Q6QW6646/JCJ\$D-M8*+APR8R6AUIAB\$D:TCQF6SG6JQE1VE846KF67465W5Y:6-963G7G466468JB5WEL\$E6\$CSWE1\$CKWE1%EW34HXO%VDT34ZJCWJCT3EHS8%JC QE/IAYJC5LEW34U3ET7DXC9 QE-ED8%EWJC0FD::5:KEPH7M/ESDD746HS80:54IBQF60R6\$A80X6S1BTYACG6M+9XG8KIAWNA91AY%67092L427BA471TU3-52PJ\$RNQ B8CCG0TF*D\$N31:EY%NQHJY90/O789376R3/PY+0WTAWTP E9/NUO-APHHUT8LSOMOGFTQ9PLWCBSP01EL:JH"
        private const val EUDCC_RECOVERED =
            "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.GY72.7J5B98/FFJTXG4%GBDFSKQCW7KCV4*XUA2PWKP/HLIJLKNF8JF7LPMIH-O92UQHPMYO9MN9DSHMN2/TU3UQPIA*QHXSM/UIGSUHPM3O89N80SGRZ3%-VYZQ H9: BOU7BX3PRAAUIWVHBO1+ZE1YH5%HMF2CG3805CZKHKB-43.E3KD3OAJ6*KG90B+H9UE*P1U-H4SIKQQFBFS8ABA3M7JR9K5TS.XIZSBMZI/XILVA HS08K6LKFHJSVB6DBBKBJZI+EBB-ABYIPZA 1JQEDDBCLTSU6EQT3KK247D9.S*BUU73/B2+QTBBC+5TOGKU+3BJU:HP.*EHZR2C3BPT5ZQ02R 5RP2VV6KKF841OQ-G+6M2BBY:BB9K+05O.25 0R6JPV1LUJ2OUS5PVJJIAWV\$UR4NSH5%PI1UBI8K000L8D\$+E"
        private const val EUDCC_BG_FULLY_VACCINATED =
            "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0II33PKHR D2T5KJQC:RPOYFA%H.MPM0SQHIZC4TPIFRMLNKNM8POCEUG*%NH\$RSC9SEF/03\$3C9LF.LQ\$BBN5T0DJLZIU0BC%F.A4TP75RO:M1 O2MR9WSPXWNR10R7QLNM9Y431TOCKF69P8Q%LO.J4E:7LYP9TQSV8XDOXCRM650 LHZA0D9E2LBHHGKLO-K%FGLIA5D8MJKQJK6HMMBI62K+PB/VSQOL9DLKWCZ3EBKD8II7UJQWT.+S1QDC8CO8CG8C3AD9ZIHAPFVA.QOZXI\$MI1VCOWC%PD5DLIWCJZIY02TDCZ.CXGI3EDALF3DAMOK23DLEE+-C/DD%JD KE:%G6EDX0KEEDAMEN+IAJKJUI%*IN89:JMXDA+N98FMHJL*TJZTJUJMYCHCFDYXOL.K**4EEVR0NV0EVMNS4L%:Q:8VT*1F04PTMTSB%SF6I8R5LB2SRLFD9FRBTQDTZELWDI/ VF/U4XF5VTC9M7RF57J2V8H\$6E.B7Z2JW2BBL"
        private const val EUDCC_THIRD_DOSE =
            "HC1:6BFHX1ZX73POO13F6CM*OJDN:P3R+VNT5TUNO35VMDH:IMS0143E+TZW5GJ8-P0Y\$0 SHOTL./9*BGJHH2D6O/OSIS0ELGLUC685XE-:3Q.71ZU93NT03841D4VT4W6THVLJV4RX7W8HRM+HEN8AZVLJFZDGN+B952LY11+QSNNJ:HA82IKJSHV\$2DR EW7DGP6Y\$6E+LL/95LGWL3RVJYG861A%.2/37:PH:497469*N :0:U16FH/CBM24/K0UYQI236%KVFW+GS1UR+GGJZ8\$R89I2SS3OB5OGAI%8ED9V6C%R9KYGE73FUIC11CS8F6BIGEJ+5WH35Y7\$MEQVT5-I5P9KI8*2V 1Q1S3:X7DCUCFBKBUVN7:8E::Q6WN3NMUV4V2VZT5.NHOST6A5.MP8/M:PISXN/MNKCF56KG8MM:E1B63*SHDBPF3C%9CK5W8MK-I0W5I.QCY8 2LC4JLPDUCT.-Q4/331NWUHT 88ESNIE-00TJ0-1"
        private const val EUDCC_CY_TEST_RESULT =
            "HC1:6BFOXN%TSMAHN-H/N8KMQ8/8.:91 P/R84KF2FCIJ9+\$VZT78WAY5SV9TXTI5K4SA3/-2E%5G%5TW5A 6YO6XL6Q3QR\$P*NIV1JIZT-8B*ZJWFL8UJ8/BGOJ ZJ83B8\$TR63Y.TVBD3\$T*+3LTTLZI99JO.A3DJKYJ.ZJ08KZ0KYPIA+2/JT6%T\$.T08K5OI9YI:8D+FD%PD5DL%9DDAB2DNAHLW 70SO:GOLIROGO3T5ZXK9UO GOP*OSV8WP4R/5UYP.28XYQ/HQBCQU96:/6F0P3IRPS4V77ZJ82HPPEPHCR6W9RDOT*OYGO.20G%UBT1%OKPJA5EQJ-HVPI5\$0JCA1W4/GJI+C7*4M:KCY05B9QIPMB4O-O4IJXKTAMP8EF/HL*E1YE9/MVE1C3ZCH13H0D3ZCL4JMYAZ+S-A5\$XKX6TVTCZW4P-AK.GNNVR*G0C7/JBA93%A75HB:U8LI3FCN\$RV-Z88LJWBJ4DJ7PN*M04RVWJB0JNO572J6E+70ZSCDWS:NQZ0/TJ2%O6 A0%59-SA9MUOT8ZE9D7109T4I/BQWF1M42JZB:5DZY9DFVTSD4ET3%V:+COJU+ HKFLR2G73K/E73*A:DI"
        private const val EUDCC_FAKED_VACCINATION =
            "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.GO92\$:25B9O\$RZ CHJIACH.QQ-MPW\$NLEENKE\$JDVPL32KD0KSKE MCAOI8%M3/U8+S4-R9ZII%KP N7DS8+SX*OIO3Y9LZPK1\$I8%M0QIJ8CK.1Z2ACEIC.USMI92CD1D%09/-3T15K3449JP\$I/XK\$M8HK66YBCPC2L0:%OD3P5B9-NT0 2\$\$0X4PCY0+-CVYCDEBD0HX2JR\$4O1K.IA.C8KRDL4O54O4IGUJKJGI.IAHLCV5GVWNZIKXGG JMLII7EDTG90OA3DE0OARH9W/IO6AHCRTWA.DPN95*28+-OAC5G472N4GMK8C5H35N95ZTMV/M7755QLQQ5%YQ+GOOSPA1RP96RIR-ZOLOOBJ6NIR9SOC0P6YOH 71WR.RO91HVOR5*PDCMGAWRM3FGNR63%SA.26OA3W0FEF9\$JB04D/CHEHHOV3J.O*:A8QS6U3FM6HUEZXN.BS53GGOI96B:RKJTV VM39H430J2M/0"
    }
}
