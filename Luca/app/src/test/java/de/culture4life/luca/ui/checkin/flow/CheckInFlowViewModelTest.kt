package de.culture4life.luca.ui.checkin.flow

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.network.pojo.LocationResponseData
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowPage
import de.culture4life.luca.ui.checkin.flow.children.ConfirmCheckInFragment
import de.culture4life.luca.ui.checkin.flow.children.ConfirmCheckInViewModel
import de.culture4life.luca.ui.checkin.flow.children.EntryPolicyViewModel
import de.culture4life.luca.ui.checkin.flow.children.VoluntaryCheckInViewModel
import io.reactivex.rxjava3.core.Single
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy

class CheckInFlowViewModelTest : LucaUnitTest() {

    private lateinit var viewModel: CheckInFlowViewModel
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun before() {
        val applicationSpy = spy(application)
        preferencesManager = spy(applicationSpy.preferencesManager)

        doReturn(preferencesManager).`when`(applicationSpy).preferencesManager

        viewModel = spy(CheckInFlowViewModel(applicationSpy))
    }

    @Test
    fun `Calling initialize to check initializeUserSetting with checkInAnonymously and not shareEntryPolicyState`() {
        // Given
        setLocationData()
        val checkInAnonymously = true
        val shareEntryPolicyState = false
        doReturn(Single.just(checkInAnonymously)).`when`(preferencesManager).restoreOrDefault(
            VoluntaryCheckInViewModel.KEY_ALWAYS_CHECK_IN_ANONYMOUSLY, true
        )
        doReturn(Single.just(shareEntryPolicyState)).`when`(preferencesManager).restoreOrDefault(
            EntryPolicyViewModel.KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, false
        )

        // When
        val observer = viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertThat(viewModel.checkInAnonymously).isEqualTo(checkInAnonymously)
        assertThat(viewModel.shareEntryPolicyState).isEqualTo(shareEntryPolicyState)
    }

    @Test
    fun `Calling initialize to check initializeUserSetting with shareEntryPolicyState and not checkInAnonymously`() {
        // Given
        setLocationData()
        val checkInAnonymously = false
        val shareEntryPolicyState = true
        doReturn(Single.just(checkInAnonymously)).`when`(preferencesManager).restoreOrDefault(
            VoluntaryCheckInViewModel.KEY_ALWAYS_CHECK_IN_ANONYMOUSLY, true
        )
        doReturn(Single.just(shareEntryPolicyState)).`when`(preferencesManager).restoreOrDefault(
            EntryPolicyViewModel.KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, false
        )

        // When
        val observer = viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertThat(viewModel.checkInAnonymously).isEqualTo(checkInAnonymously)
        assertThat(viewModel.shareEntryPolicyState).isEqualTo(shareEntryPolicyState)
    }

    @Test
    fun `Calling initialize to check updatePages with no pages`() {
        // Given
        setLocationData()
        val skipCheckInConfirmation = true
        doReturn(Single.just(skipCheckInConfirmation)).`when`(preferencesManager).restoreOrDefault(
            ConfirmCheckInViewModel.KEY_SKIP_CHECK_IN_CONFIRMATION,
            false
        )
        val pages = mutableListOf<BaseFlowPage>()

        // When
        val observer = viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertThat(viewModel.pages).isEqualTo(pages)
    }

    @Test
    fun `Calling initialize to check updatePages with check in confirmation page`() {
        // Given
        setLocationData()
        val skipCheckInConfirmation = false
        doReturn(Single.just(skipCheckInConfirmation)).`when`(preferencesManager).restoreOrDefault(
            ConfirmCheckInViewModel.KEY_SKIP_CHECK_IN_CONFIRMATION, false
        )
        val args = ConfirmCheckInFragment.createArguments(LOCATION_GROUP_NAME)
        val confirmCheckInPage = CheckInFlowPage.ConfirmCheckInPage(args)
        val pages = mutableListOf<BaseFlowPage>(confirmCheckInPage)

        // When
        val observer = viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertThat(viewModel.pages.size).isEqualTo(pages.size)
        assertThat(viewModel.pages[0]).isInstanceOf(confirmCheckInPage.javaClass)
        assertThat(viewModel.pages[0].arguments?.get(ConfirmCheckInFragment.KEY_LOCATION_NAME))
            .isEqualTo(args.get(ConfirmCheckInFragment.KEY_LOCATION_NAME))
    }

    private fun setLocationData(
        groupName: String? = LOCATION_GROUP_NAME,
        isContactDataMandatory: Boolean = false,
        entryPolicy: LocationResponseData.EntryPolicy? = null
    ) {
        val locationData = mock<LocationResponseData>()
        doReturn(groupName).`when`(locationData).groupName
        doReturn(isContactDataMandatory).`when`(locationData).isContactDataMandatory
        doReturn(entryPolicy).`when`(locationData).entryPolicy
        viewModel.locationResponseData = locationData
    }

    companion object {
        private const val LOCATION_GROUP_NAME = "Hilton"
    }
}
