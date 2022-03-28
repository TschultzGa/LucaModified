package de.culture4life.luca.testtools.samples

import de.culture4life.luca.document.Document

interface SampleDocuments {

    companion object {
        // When the app is at this given DateTime, then the samples here are working like expected.
        // Means usually all documents are valid (if not other mentioned).
        const val referenceDateTime = "2021-06-15T15:30:00"
    }

    val person: Person
    val documentType: Int
    val outcome: Int
    val lab: String
    val qrCodeContent: String

    class ErikaMustermann : Person {
        override val firstName = "Erika"
        override val lastName = "Mustermann"
        override val dateOfBirth = "2000-02-25"

        class EudccPartiallyVaccinated : Vaccination() {
            override val person = ErikaMustermann()
            override val lab = "Robert Koch-Institut"
            override val outcome = Document.OUTCOME_PARTIALLY_IMMUNE
            override val vaccinationDate = "2021-05-05"
            override val qrCodeContent =
                "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0II9\$PKHRAF22+I5WCPQP+BJ7GP9.SURC/GPWBI\$C9UDBQEAJJKKKMEC8.-B97U: KMZNDDS.SST*Q-+RV%NI%KH NWBRZ+P4+O8%MPLIBDSUES\$8R12CT28M.SY\$NTWA 34+5EG+SB.V4Q56H06J0%H0%YBWYQ1RM8ZAUZ4+FJE 4Y3LL/II 0OC9JU0D0HT0HB2PR788FFA.D90I/EL6KKYHIL4OTJLGY8/.DV2MGDIR0MDDQCNCRK40YQDXI25P6QS03LGWI:DK9-8CNNZ0LBZI WJHWEYIALEE-7A%IA%DA9MGF:F81H23DLEE+-C/DD1JAA/CGKDEEA+ZA%DBU2LKHG8-II5IGBG\$SKE9DTEM3D83C9HTAFDJV6IPJJ3PCJWG GBV28*-G-2NT4F/6I7PLJBH%2OSO54XCP5NE1W:7I1%J1EAG/I/\$6Y3UIT5O2R917HLDON3W7WY.4YPTSG1CK31ESBSH7/DMLVUZ326RU0F"
        }

        class EudccFullyVaccinated : Vaccination() {
            override val person = ErikaMustermann()
            override val lab = "Robert Koch-Institut"
            override val outcome = Document.OUTCOME_FULLY_IMMUNE
            override val vaccinationDate = "2021-05-10"
            override val qrCodeContent =
                "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0II9\$PKHRAF2Y%IOIMZZCVT99JC5WU7JM:UC*GP-S4FT5D75W9AAABE34L/5R3FMIA4/B 3ELEE\$JD/.D%-B9JAO/BNPD+.C2KC4JBSD9Z3E8AE-QD89MT6KBLEH-BMOL-CIC8N8KES/FP1JXGGXHG4HG43MNEDGGBYPL0QIRR97I2HOAXL92L0. KOKGTM8\$M8SNCT64BR7Z6NC8P\$WA3AA9EPBDSM+QFE4:/6N9R%EPL8RM9D JMLII7EDTG90OA3DE0OA8G97FQRK46YBXPAWUC-JEMHQVD9O-OEF82E9GX8\$G10QVGB3O1KO-OAGJM*KYE9*FJZPLAZ8-.A2*CEHJ5\$02.AS*7370E/H-KKAKO.Z0S\$48BA:EC4*7P0DS:H172FOMVFHX2K*NV8ONQ/M%K9N:I4P59JT5LLEU32*G04Q2DFORVLYATD91FH7E0\$IG\$9VW-O5*R2XRL:M.\$TBYP5CSRAWXSJUUN24DH/ID00 *H05"
        }

        class EudccBoosteredVaccinated : Vaccination() {
            override val person = ErikaMustermann()
            override val lab = "Robert Koch-Institut"
            override val outcome = Document.OUTCOME_FULLY_IMMUNE
            override val vaccinationDate = "2021-05-15"
            override val qrCodeContent =
                "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0II9\$PKHRAF2T*I%CMZZC8W9FLO9.SURC/GPWBI\$C9UDBQEAJJKKKMEC8.-B97U: KMZNDDS.SST*Q-+RV%NI%KH NWBRZ+P4+O8%MPLIBDSUES\$8R12CT28M.SY\$NTWA 34+5EG+SB.V4Q56H06J0%H0%YBWYQ1RM8ZAUZ4+FJE 4Y3LL/II 0OC9JU0D0HT0HB2PR788FFA.D90I/EL6KKYHIL4OTJLGY8/.DV2MGDIR0MDDQCNCRK40YQDXI47T6QS47TGWI:DK9-8CNNM3LX88\$*S/CKHRIJRH.OG4SIIRH/R2UZUWM6J\$7XLH5G6TH9\$NI4L6OTA6VH6ZL4XP:N6ON1Z:LBYF6E0DP3 J8 TG*Q1BU9GMKCOO*%FJ+PRX32E4LRQA2K+DQNC6QC4CCVA\$NQ.744PYZ148MM4TCE10/CQYUZ1Q 1AXJR UD5/A80F:ZP5PHMTNC%MU0TA+5INEF-3\$DT/*MVF5YXL OQS30:TKZ0"
        }

        class EudccRecovered : Recovery() {
            override val person = ErikaMustermann()
            override val lab = "Robert Koch-Institut"
            override val outcome = Document.OUTCOME_FULLY_IMMUNE
            override val firstPositiveTestDate = "2021-04-21"
            override val startDate = "2021-05-01"
            override val expirationDate = "2021-10-21"
            override val qrCodeContent =
                "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G272.7J5B9-5W31GXG4PKR6C7AD61CQ NI4EFSYS1-ST*QGTAAY7.Y7B-S-*O5W41FDOFB2/KBY4T 83HH:EF9NTDKL.Z8OMR KP8EFOFB9KKRB40E8S0IC1G9NTBY4E1MZ3KD2IZD5CC9G%8CY0CNNG.8--8-FHT-H-RI PQVW5/O16%HAT1Z%PXRQ%CG9-8CNNE+49-89B95EDR2K+:L6BI+G1 73BDG:PI1HGLK9FQ5VA131A.V56GAU3QO6QE3VTK5KJPB95.B9X5Q7AIMZ5BTMUW5-5QNF6H*MF U-QUC*1*.1G+E2:6OQM3ZMPH659EDQ2M+UQAPC46%1MX1PP2QEAF%-4X8UT\$CTHGTD0S6OA28PBJ46OEIN7CO:07 N4PH8 /7KGT3BM59OP*B7-VH0GA13L18O8UG1J9NNC30Z93/4"
        }

        class EudccPcrNegative : CovidTestResult() {
            override val person = ErikaMustermann()
            override val lab = "Robert Koch-Institut"
            override val documentType = Document.TYPE_PCR
            override val outcome = Document.OUTCOME_NEGATIVE
            override val testingDateTime = "2021-06-15T10:30:00Z"
            override val qrCodeContent =
                "HC1:6BFPX1S7O*I0C40GDEQ%C.D9+59W93W3V0M7P/TNRS\$0W7SFAI5%ROFDR1HF+CUD7FZ%VGL8\$FH5R8H%2KEQ*XG4KO%GIS6B+HC25WI\$6\$FHJ%UTIV+PIEYUD0L6-E.PICJG5:MI2O XOW%M8DA-7V4BOSQGB28AT3LQ9869H\$K.PAP-8YR5NLOMC2FYGF/ST+5+KA8REA0PH%43GRG.ALUQWQIDGJJZC 6P:IMK*8KP1DM6B/OAILR%JFQ3LF8P00R24E%GKJMFHHH\$900WMYKZ10DHMB-KK*2A60WA1HC1AXJ+Y25VOL:N8ZLMTK1UIJLSS+6QVOESMZHI-CM M0051I78WCC9/1G:K V1983%FPS18%13P+OCQLSHVZ%C*HGL J3 2THE*OMASCA*QU565\$8KIOW/2 DA2+PGD5\$C19T7C81%PTC87FZNTG8810/M4Q0R/1OM-QUQ482V0KLSRBUWV-RUO8UZ\$D6VTLZQV/RXEV+-QSOVN5W86DJ1F./9JUKIAQ*5CVYMHZBB.IWYS%3UDXFZ*8 8VCY9M0PN*SAFUM:74AE\$0"
        }
    }

    class ErikaMustermannDifferentBirthday : Person {
        override val firstName = "Erika"
        override val lastName = "Mustermann"
        override val dateOfBirth = "2000-01-01"

        class EudccFullyVaccinated : Vaccination() {
            override val person = ErikaMustermann()
            override val lab = "Robert Koch-Institut"
            override val outcome = Document.OUTCOME_FULLY_IMMUNE
            override val vaccinationDate = "2021-05-10"
            override val qrCodeContent =
                "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0II9\$PKHRTPI7Z4/EB H6MMJT2D5WU7JM:UC*GP-S4FT5D75W9AAABE34L/5R3FMIA4/B 3ELEE\$JD/.D%-B9JAO/BNPD+.C2KC4JBSD9Z3E8AE-QD89MT6KBLEH-BMOL-CIC8N8KES/FP1JXGGXHGREG%12N:IN1MPF5RBQ746B46O1N646RM93O5RF6\$T61R63B0 %PZIEQKERQ8IY1I\$HH%U8 9PS5/IE%TE6UG+ZE0EG623423.LJX/KQ968X2+36/-KW10PV6BF38F10T932QGONAC5PCN/35CC5BCLCJOU:7.P81O6PCN/VM YMN95ZTMV/M7755QLQQ5%YQ+GOYSPQBPP96RIR-ZOLOOBJ6NIR9SOC0P6YOH 71WRHOO3BSKF0RGBK0VN.81ES.BUG9UEOR09UX+5+8OQ4DJXT+X0ZJ0BRJGSAGCU*WF00J-BQT0TXTBC+11QF1E5W.CJWMAGU4867:R739DO3H5TC5F"
        }
    }

    class JulianMusterkind : Person {
        override val firstName = "Julian"
        override val lastName = "Musterkind"
        override val dateOfBirth = "2010-05-13"

        class EudccFullyVaccinated : Vaccination() {
            override val person = JulianMusterkind()
            override val lab = "Robert Koch-Institut"
            override val outcome = Document.OUTCOME_FULLY_IMMUNE
            override val vaccinationDate = "2021-05-10"
            override val qrCodeContent =
                "HC1:6BF180190T9WTWGSLKC 4769R174TF//5P30FBBXL2WY0QHCE7GFN0HFDN6K*70J+9D97TK0F90\$PC5\$CUZC\$\$5Y\$5TPCBEC7ZKW.CXJD7%E7WE-KEZED3VCI3D--C0%EMED:.DW.CJWEDS8FIA71ANNAV+A3+9269+S9.+9I3DFWE2+8NB8-M86C9:R7*0A.+9GVC*JCNF6F463W5KF6VF6KECTHG4KCD3DX47B46IL6646H*6Z/E5JD%96IA74R6646307Q\$D.UDRYA 96NF6L/5SW6Y57B\$D% D3IA4W5646946846.96XJC\$+D3KC.SCXJCCWENF6OF63W5NW6\$96WJCT3EHS8%JC QE/IAYJC5LEW34U3ET7DXC9 QE-ED8%EWJC0FD4X4:KEPH7M/ESDD746VG7TS9TB8ENA.Q667B7DBEL6657IS8X59Z09%M927BXYFOH6CGVV RYNR5XRA0UXQD5%4\$01EZKV87%DDMEVZ9C6ILO0E+70S\$V695QQ92XN1/RJVPJ 0\$BP QM:%8X.L%RP-OQ+H77/LJ0G"
        }
    }

    interface Person {
        val firstName: String
        val lastName: String
        val dateOfBirth: String
    }

    abstract class Vaccination : SampleDocuments {
        override val documentType = Document.TYPE_VACCINATION
        abstract val vaccinationDate: String
    }

    abstract class Recovery : SampleDocuments {
        override val documentType = Document.TYPE_RECOVERY
        abstract val firstPositiveTestDate: String
        abstract val startDate: String
        abstract val expirationDate: String
    }

    abstract class CovidTestResult : SampleDocuments {
        abstract val testingDateTime: String
    }

    interface VaccinationAppointment
}
