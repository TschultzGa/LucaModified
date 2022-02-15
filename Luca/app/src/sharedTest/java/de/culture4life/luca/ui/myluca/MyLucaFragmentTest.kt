package de.culture4life.luca.ui.myluca

import de.culture4life.luca.R
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.pages.MyLucaPage
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.util.TimeUtil
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore
class MyLucaFragmentTest : LucaFragmentTest<MyLucaFragment>(LucaFragmentScenarioRule.create()) {

    private val vaccinatedErikaMustermann2of2 =
        "HC1:6BFOXN%TSMAHN-H+XO5XF7:UY%FJ.G0II8 NKHR4D2*T9M/C%PPSXR AJ3NPFAPQHIZC4TPIFRMLNKNM8POCEUG*%NH\$RSC9FFFP4OO-O/HLMKN2GFLF95HFI1MAKJ%IH1FDKX4%*53E8Z7W7JO:EC0/F\$%3Q-7C1G9NTRY4A.DS0IP7Q63KD*668WC1GZD5CC9G%8L\$0CNNG.8W%8SGH.+HAMI PQVW5/O16%HAT1Z%PHOP+MMBT16Y5+Z9XV7*\$K8KG+9RR\$F+ F%J00N89M4*\$K3\$OHBW24FAL86H0CNCRK40YQDXI03L6QS03LGWI:DK9-8CNNZ0LBZI WJWWEYIA3HEX3E1.BLEELEACI8PMJD8B:8E KE:%G6EDX0KEEDAMEN+IAJKW7KI A67I%Y8QBF%GJQXI:6AAU8LUK-6I1VN UA+N8:XDUFJ7AWM8HH6G5%HM1A4AN3.A%8J/UJB 2HEAZFV7SBD3W/NDESBB5M ISMANUQK-4VJ0C+ORDQ72T4:7G7D3P\$HVMDZ09G3D3H0 9SG:F"

    @Before
    fun setup() {
        setupDefaultWebServerMockResponses()
    }

    @Test
    fun userCanAddValidDocument() {
        givenRegisteredUser("Erika", "Mustermann")
        fragmentScenarioRule.launchInContainer()

        MyLucaPage().run {
            documentList.hasSize(0)
            addDocumentButton.click()
            addCertificateFlow.run {
                scanQrCodeButton.run {
                    scrollTo()
                    click()
                }
                scanQrCode(fragmentScenarioRule.scenario!!, vaccinatedErikaMustermann2of2)
                consentsDialog.okButton.click()
                successConfirmButton.scrollTo()
                successConfirmButton.click()
            }
            Thread.sleep(MyLucaViewModel.DELAY_UPDATE_DOCUMENTS_LIST_AFTER_SOURCE_CHANGED)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(1) {
                    title.hasText(R.string.document_type_vaccination)
                }
            }
        }
    }

    private fun setupDefaultWebServerMockResponses() {
        mockWebServerRule.mockResponse.apply {
            put("/api/v3/timesync") {
                setResponseCode(200)
                setBody("{\"unix\":${TimeUtil.getCurrentUnixTimestamp().blockingGet()}}")
            }
            put("/api/v3/tests/redeem") { setResponseCode(200) }
        }
    }

    private fun givenRegisteredUser(firstName: String, lastName: String) {
        // Transitive used PreferencesManager needs to be initialized before we can fake registered user.
        getInitializedManager(applicationContext.preferencesManager)

        val registrationManager = applicationContext.registrationManager
        val registrationData = registrationManager.getRegistrationData().blockingGet()

        registrationData.firstName = firstName
        registrationData.lastName = lastName

        registrationManager.persistRegistrationData(registrationData).blockingAwait()
    }
}