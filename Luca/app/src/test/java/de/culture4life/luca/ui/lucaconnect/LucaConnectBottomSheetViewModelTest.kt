package de.culture4life.luca.ui.lucaconnect

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowPage
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.spy

class LucaConnectBottomSheetViewModelTest : LucaUnitTest() {

    private lateinit var viewModel: LucaConnectBottomSheetViewModel

    @Before
    fun before() {
        val applicationSpy = spy(application)

        viewModel = spy(LucaConnectBottomSheetViewModel(applicationSpy))
    }

    @Test
    fun `Calling initialize to check updatePages with all pages`() {
        // Given
        val pages = mutableListOf<BaseFlowPage>().apply {
            add(LucaConnectFlowPage.ExplanationPage)
            add(LucaConnectFlowPage.ProvideProofPage)
            add(LucaConnectFlowPage.LucaConnectSharedDataPage)
            add(LucaConnectFlowPage.KritisPage)
            add(LucaConnectFlowPage.LucaConnectConsentPage)
            add(LucaConnectFlowPage.ConnectSuccessPage)
        }

        // When
        val observer = viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        Assertions.assertThat(viewModel.pages).isEqualTo(pages)
    }
}
