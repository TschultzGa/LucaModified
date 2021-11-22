package de.culture4life.luca.document.provider.eudcc

import androidx.test.runner.AndroidJUnit4
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
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import org.robolectric.annotation.Config


@ExperimentalUnsignedTypes
@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
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
                    "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",
                    "c9609176987c5c9b1bb27cc7edb0ee97da59b1f9460cbfdd2cd59b3e43f47145",
                    "2021-06-03T19:09:10+02:00"
                )
            )
        )
    }

    @Test
    fun canParse_validData_emitsTrue() {
        provider.canParse(EUDCC_TEST_RESULT)
            .test().assertValue(true)
    }

    @Test
    fun canParse_invalidData_emitsFalse() {
        provider.canParse("anything")
            .test().assertValue(false)
    }

    @Test
    fun canParse_ticketIoDocument_emitsFalse() {
        provider.canParse(OpenTestCheckDocumentProviderTest.VALID_TEST_RESULT_TICKET_IO)
            .test().assertValue(false)
    }

    @Test
    fun canParse_baercodeDocument_emitsFalse() {
        provider.canParse(BaercodeTestResultProviderTest.TEST_QR_CODE)
            .test().assertValue(false)
    }

    @Test
    fun parse_fullyVaccinated_setsCorrectValues() {
        with(provider.parse(EUDCC_FULLY_VACCINATED).blockingGet().document) {
            assertEquals("Erika Dörte", firstName)
            assertEquals("Dießner Musterfrau", lastName)
            assertEquals(Document.TYPE_VACCINATION, type)
            assertEquals(Document.OUTCOME_FULLY_IMMUNE, outcome)
            assertEquals(1620345600000, testingTimestamp)
            assertEquals(1620345600000, resultTimestamp)
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
            assertEquals("URN:UVCI:01DE/IZ12345A/5CWLU12RNOB9RXSEOP6FG8#W1", hashableEncodedData)
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
        assertEquals(-170035200000, eudccDocument.document.dateOfBirth)  // "1964-08-12"
        with(eudccDocument.document) {
            assertEquals("Erika", firstName)
            assertEquals("Mustermann", lastName)
            assertEquals(Document.TYPE_FAST, type)
            assertEquals(Document.OUTCOME_NEGATIVE, outcome)
            assertEquals("Testzentrum Köln Hbf", labDoctorName)
            assertEquals("Robert Koch-Institut", labName)
            assertEquals(1622369542000, testingTimestamp)
            assertEquals(1622370615000, resultTimestamp)
            assertEquals("URN:UVCI:01DE/IZ12345A/5CWLU12RNOB9RXSEOP6FG8#W", hashableEncodedData)
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
            assertEquals(1610236800000, testingTimestamp)
            assertEquals(1610236800000, resultTimestamp)
            assertEquals(1622246400000, validityStartTimestamp)
            assertEquals(1623715200000, expirationTimestamp)
            assertEquals("URN:UVCI:01DE/5CWLU12RNOB9RXSEOP6FG8#W", hashableEncodedData)
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
            .test().assertComplete()
    }

    @Test
    fun validate_wrongName_fails() {
        val person = Person("Hans", "Wurst")
        provider.parse(EUDCC_TEST_RESULT)
            .flatMapCompletable { result -> provider.validate(result, person) }
            .test().assertError(DocumentVerificationException::class.java)
    }

    @Test
    fun validate_fakedDocument_fails() {
        provider.parse(EUDCC_FAKED_VACCINATION)
            .test().assertError(DocumentParsingException::class.java)
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
            "HC1:6BFOXN*TS0BI\$ZD4N9:9S6RCVN5+O30K3/XIV0W23NTDEPWK G2EP4J0B3KLASMUG8GJL8LLG.3SA3/-2E%5VR5VVBJZILDBZ8D%JTQOL2009UVD0HX2JN*4CY009TX/9F/GZ%5U1MC82*%95HC2FCG2K80H-1GW\$5IKKQJO0OPN484SI4UUIMI.J9WVHWVH+ZE/T9MX1HRIWQHCR2HL9EIAESHOP6OH6MN9*QHAO96Y2/*13A5-8E6V59I9BZK6:IR/S09T./0LWTHC0/P6HRTO\$9KZ56DE/.QC\$QUC0:GOODPUHLO\$GAHLW 70SO:GOV636*2. KOKGKZGJMI:TU+MMPZ5OV1 V125VE-4RZ4E%5MK9BM57KPGX7K:7D-M1MO0Q2AQE:CA7ED6LF90I3DA+:E3OGJMSGX8+KL1FD*Y49+574MYKOE1MJ-69KKRB4AC8.C8HKK9NTYV4E1MZ3K1:HF.5E1MRB4WKP/HLIJL8JF8JF172M*8OEB2%7OREF:FO:7-WF11SKCU1MH8FWPVH%L635OBXTY*LPM6B9OBYSH:4Q1BQ:A5+I6:DQR9VKR8 BLHCFQMZA5:PHR14%GV4ZOP50\$ A 3"
        private const val EUDCC_PARTIALLY_VACCINATED =
            "HC1:6BF+70790T9WJWG.FKY*4GO0.O1CV2 O5 N2FBBRW1*70HS8WY04AC*WIFN0AHCD8KD97TK0F90KECTHGWJC0FDC:5AIA%G7X+AQB9746HS80:54IBQF60R6\$A80X6S1BTYACG6M+9XG8KIAWNA91AY%67092L4WJCT3EHS8XJC +DXJCCWENF6OF63W5NW6WF6%JC QE/IAYJC5LEW34U3ET7DXC9 QE-ED8%E.JCBECB1A-:8\$96646AL60A60S6Q\$D.UDRYA 96NF6L/5QW6307KQEPD09WEQDD+Q6TW6FA7C466KCN9E%961A6DL6FA7D46JPCT3E5JDLA7\$Q6E464W5TG6..DX%DZJC6/DTZ9 QE5\$CB\$DA/D JC1/D3Z8WED1ECW.CCWE.Y92OAGY8MY9L+9MPCG/D5 C5IA5N9\$PC5\$CUZCY\$5Y\$527BHB6*L8ARHDJL.Q7*2T7:SCNFZN70H6*AS6+T\$D9UCAD97R8NIBO+/RJVE\$9PAGPTBIZEP MO-Q0:R13IURRQ5MV93M9V3X2U:NDZSF"
        private const val EUDCC_TEST_RESULT =
            "HC1:6BFR%BH:7*I0PS33NUA9HWP5PZ2CLJ*GH7WV-UNA1VZJKZ6HX.A/5R..9*CV6+LJ*F.UN7A2BT8B+6B897S69R48S1.R1VJO9Q1ZZO+CC\$A9%T5X7RI25A8S57D JK-PQ+JR*FDTW3+1EC1JXLOQ58+KFL49ZMENAO.YOWR75PAH0HD6AIHCPWHJTF.RJ*JCSKEHL1N31HWEO67KJH8TIX-B3QB-+9*LCU:C:P2QEEQ7KF\$V--4CW7JWILDWU%Q%IO0LAK70J\$KW2JW56.KO8E2RHPH60ILI8T0N/7OEPD7P3+3IH9VZIVWP.44FX87QH5I97ZK0MK8OIGC3 3CQ6WO+9P9ECRSV%72M4L65 KAVKE*YPRHSIF1 89*4NDZ7FU6:F6NPJ1PHL059BGBB1%/C/J91R75Z5I7CWV0TREWYSY8ULK5HWPGEP\$SI5B1\$8HDOCH3JEBCL*8SE2AZT9SC+84JVGR39:2V*TR:KBW/4S:FK DOHF-1789MQ.18CV2C3YCN79OR176:1U:0CQVNGDJ0GUPO%CRT+QC/O\$:D/WQY\$3*5UR2M4YPFXK\$DH"
        private const val EUDCC_RECOVERED =
            "HC1:6BFOXN*TS0BI\$ZD-PHQ7I9AD66V5B22CH9M9ESI9XBHXK-%69LQOGI.*V76GCV4*XUA2P-FHT-HNTI4L6N\$Q%UG/YL WO*Z7ON15 BM0VM.JQ\$F4W17PG4.VAS5EG4V*BRL0K-RDY5RWOOH6PO9:TUQJAJG9-*NIRICVELZUZM9EN9-O9:PICIG805CZKHKB-43.E3KD3OAJ6*K6ZCY73JC3KD3ZQTWD3E.KLC8M3LP-89B9K+KB2KK3M*EDZI9\$JAQJKKIJX2MM+GWHKSKE MCAOI8%MCU5VTQDPIMQK9*O7%NC.UTWA6QK.-T3-SY\$NCU5CIQ 52744E09TBOC.UKMI\$8R+1A7CPFRMLNKNM8JI0JPGN:0K7OOBRLY667SYHJL9B7VPO:SWLH1/S4KQQK0\$5REQT5RN1FR%SHPLRKWJO8LQ84EBC\$-P4A0V1BBR5XWB3OCGEK:\$8HHOLQOZUJ*30Q8CD1"
        private const val EUDCC_BG_FULLY_VACCINATED =
            "HC1:NCFOXN*TS0BI\$ZDYSH-TTSBKCW1P-B0II6VL-36HD7-TM X4V7BI9CGJ99 PRAC/GPWBILC90GBYPLR-SCG1CSQ6U7SSQY%S5L51I0N:4UO5920R2EX:6N5FQ7EUN6/DKC3277I\$*SJAK9B9LGF9B9LW4G%89-8CNNG.8Q:GVD9B.OD4OYGFO-O%Z8JH1PCDJ*3TFH2V4IE9MIHJ6W48UK.GCY0\$2PH/MIE9WT0K3M9UVZSVV*001HW%8UE9.955B9-NT0 2\$\$0X4PCY0+-CVYCRMTB*05*9O%05\$K+8HYKSCPCXI00H3\$35+V4YC5/HQ1%RHCR+9AYDPEA7IB65C94JB*4Q2MVK1L98BQGCE*GV-27Y41BTW69 5B %S\$CNKDTDT7%K1IGGJXUEP5 PQ0CGYE91FD\$W4P90UC467WTNP8EFTHF+A4O6OM.6D1MR195QN%X47*KB*KYQTHFT4S8JYIVYF6C5*AD0SDW+J8WM%-3*LM2WFUNP/:GGABK8LN 0793*1PV%GT9BN+CYSDQ PT*4KBQ**P3RQ\$WK:TK60FR1KWKVV4PITNVYTHB0O-EHQN"
        private const val EUDCC_THIRD_DOSE =
            "HC1:6BF870*90T9WTWGSLKC 4769R174TF//5P30-AB5XK3F3Q:A7\$SAF3R.R-SCBJCSS9Y50.FK8ZKO/EZKEZ96LF6C56..DX%DZJC:.DET8Y CI3DOUCZ3E:.DW.C5WERS8XY8I3D6WEXH9F69JPCT3E6JD646Q478465W5X577:EDOL9WEQDD+Q6TW6FA7C466KCN9E%961A6DL6FA7D46.JCP9EJY8L/5M/5546.96VF6.JCBECB1A-:8\$966469L6OF6VX6FVCBJ0KQEBJ0LVC6JD846Y96D465W5VX6UPCBJCOT9+ED83EZED+EDKWE3EFX3E/34Z1BWJC0FD4X4:KEPH7M/ESDD746VG7TS9TB8ENA.Q667B7DBEL6657IS8X59Z09%M927BMJVMAFVGP:0S3WIX\$GLQOU-T *ENBE:QCY43OI7P.8J7RBF1WDSYCFQ15QOFBCU2:9P0BO8QWES5GB:S6DCD *7.R2:GB*L98C6Y*E"
        private const val EUDCC_CY_TEST_RESULT =
            "HC1:6BFOXN%TSMAHN-H/N8KMQ8/8.:91 P/R84KF2FCIJ9+\$VZT78WAY5SV9TXTI5K4SA3/-2E%5G%5TW5A 6YO6XL6Q3QR\$P*NIV1JIZT-8B*ZJWFL8UJ8/BGOJ ZJ83B8\$TR63Y.TVBD3\$T*+3LTTLZI99JO.A3DJKYJ.ZJ08KZ0KYPIA+2/JT6%T\$.T08K5OI9YI:8D+FD%PD5DL%9DDAB2DNAHLW 70SO:GOLIROGO3T5ZXK9UO GOP*OSV8WP4R/5UYP.28XYQ/HQBCQU96:/6F0P3IRPS4V77ZJ82HPPEPHCR6W9RDOT*OYGO.20G%UBT1%OKPJA5EQJ-HVPI5\$0JCA1W4/GJI+C7*4M:KCY05B9QIPMB4O-O4IJXKTAMP8EF/HL*E1YE9/MVE1C3ZCH13H0D3ZCL4JMYAZ+S-A5\$XKX6TVTCZW4P-AK.GNNVR*G0C7/JBA93%A75HB:U8LI3FCN\$RV-Z88LJWBJ4DJ7PN*M04RVWJB0JNO572J6E+70ZSCDWS:NQZ0/TJ2%O6 A0%59-SA9MUOT8ZE9D7109T4I/BQWF1M42JZB:5DZY9DFVTSD4ET3%V:+COJU+ HKFLR2G73K/E73*A:DI"
        private const val EUDCC_FAKED_VACCINATION =
            "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.GO92\$:25B9O\$RZ CHJIACH.QQ-MPW\$NLEENKE\$JDVPL32KD0KSKE MCAOI8%M3/U8+S4-R9ZII%KP N7DS8+SX*OIO3Y9LZPK1\$I8%M0QIJ8CK.1Z2ACEIC.USMI92CD1D%09/-3T15K3449JP\$I/XK\$M8HK66YBCPC2L0:%OD3P5B9-NT0 2\$\$0X4PCY0+-CVYCDEBD0HX2JR\$4O1K.IA.C8KRDL4O54O4IGUJKJGI.IAHLCV5GVWNZIKXGG JMLII7EDTG90OA3DE0OARH9W/IO6AHCRTWA.DPN95*28+-OAC5G472N4GMK8C5H35N95ZTMV/M7755QLQQ5%YQ+GOOSPA1RP96RIR-ZOLOOBJ6NIR9SOC0P6YOH 71WR.RO91HVOR5*PDCMGAWRM3FGNR63%SA.26OA3W0FEF9\$JB04D/CHEHHOV3J.O*:A8QS6U3FM6HUEZXN.BS53GGOI96B:RKJTV VM39H430J2M/0"

    }

}