package de.culture4life.luca.ui.onboarding

import android.app.Application
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.onboarding.OnboardingActivity.Companion.WELCOME_SCREEN_SEEN_KEY
import de.culture4life.luca.ui.terms.UpdatedTermsUtil.Companion.markTermsAsAccepted
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.*

class OnboardingViewModel(app: Application) : BaseViewModel(app) {

    private var countryItems = listOf<CountryViewItem>()
    private lateinit var selectedCountryItem: CountryViewItem

    val countryListLiveData: MutableLiveData<CountryUserData> = MutableLiveData()
    val checkBoxErrorLiveData: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()
    val showInfoScreenLiveData: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()
    val showCountryUnavailableLiveData: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()

    fun initializeCountries() {
        Single.zip(
            Single.just(createSortedDisplayCountryList()),
            Single.just(getDefaultCountryViewItemByLocale())
        ) { countryItemList, userCountryItem ->
            setSelectedCountryItem(userCountryItem)
            CountryUserData(countryItemList, userCountryItem)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete()
            .subscribe {
                updateAsSideEffect(countryListLiveData, it)
            }
            .addTo(modelDisposable)
    }

    fun onWelcomeActionButtonClicked(termsChecked: Boolean) {
        if (termsChecked && selectedCountryItem.countryCode == AVAILABLE_COUNTRY) {
            application.preferencesManager
                .persist(WELCOME_SCREEN_SEEN_KEY, true)
                .andThen(markTermsAsAccepted(application))
                .onErrorComplete()
                .subscribe {
                    updateAsSideEffect(showInfoScreenLiveData, ViewEvent(true))
                }
                .addTo(modelDisposable)
        } else {
            if (!termsChecked) {
                updateAsSideEffect(checkBoxErrorLiveData, ViewEvent(true))
            } else if (selectedCountryItem.countryCode != AVAILABLE_COUNTRY) {
                updateAsSideEffect(showCountryUnavailableLiveData, ViewEvent(true))
            }
        }
    }

    fun setSelectedCountryItem(countryItem: CountryViewItem) {
        selectedCountryItem = countryItem
    }

    private fun createSortedDisplayCountryList(): List<CountryViewItem> {
        val translationLocale = if (getUserCountryCode() == AVAILABLE_COUNTRY) {
            ConfigurationCompat.getLocales(application.resources.configuration)[0]
        } else {
            Locale("en", "GB")
        }

        countryItems = Locale.getISOCountries()
            .map {
                CountryViewItem(it, Locale("", it).getDisplayCountry(translationLocale))
            }
            .sortedBy { it.countryDisplayName }

        return countryItems
    }

    private fun getDefaultCountryViewItemByLocale(): CountryViewItem {
        return if (getUserCountryCode() == AVAILABLE_COUNTRY) {
            countryItems.first { it.countryCode == getUserCountryCode() }
        } else {
            countryItems[0]
        }
    }

    private fun getUserCountryCode(): String {
        val locale = ConfigurationCompat.getLocales(application.resources.configuration)[0]
        return when {
            locale.language == "de" -> COUNTRY_CODE_GERMANY
            locale.country.isNullOrEmpty() -> COUNTRY_CODE_GB
            else -> locale.country
        }
    }

    companion object {
        private const val AVAILABLE_COUNTRY = "DE"
        private const val COUNTRY_CODE_GERMANY = "DE"
        private const val COUNTRY_CODE_GB = "GB"
    }

    data class CountryUserData(
        val countryItems: List<CountryViewItem>,
        val userCountryItem: CountryViewItem
    )

    data class CountryViewItem(
        val countryCode: String,
        val countryDisplayName: String
    ) {
        override fun toString(): String {
            return countryDisplayName
        }
    }
}
