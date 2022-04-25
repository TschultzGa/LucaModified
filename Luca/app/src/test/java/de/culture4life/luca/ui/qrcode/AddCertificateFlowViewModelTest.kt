package de.culture4life.luca.ui.qrcode

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowPage
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.spy

class AddCertificateFlowViewModelTest : LucaUnitTest() {

    private lateinit var viewModel: AddCertificateFlowViewModel

    @Before
    fun before() {
        val applicationSpy = spy(application)

        viewModel = spy(AddCertificateFlowViewModel(applicationSpy))
    }

    @Test
    fun `Calling initialize to check updatePages with all pages`() {
        // Given
        val pages = mutableListOf<BaseFlowPage>().apply {
            add(AddCertificateFlowPage.SelectInputPage)
            add(AddCertificateFlowPage.ScanQrCodePage)
            add(AddCertificateFlowPage.DocumentAddedSuccessPage)
        }

        // When
        val observer = viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        Assertions.assertThat(viewModel.pages).isEqualTo(pages)
    }
}
