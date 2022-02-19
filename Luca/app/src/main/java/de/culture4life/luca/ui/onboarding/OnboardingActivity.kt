package de.culture4life.luca.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentOnboardingCountryUnavailableBinding
import de.culture4life.luca.databinding.FragmentOnboardingInfoBinding
import de.culture4life.luca.databinding.FragmentOnboardingWelcomeBinding
import de.culture4life.luca.ui.BaseActivity
import de.culture4life.luca.ui.registration.RegistrationActivity
import de.culture4life.luca.util.ViewRequiredUtil.showCheckBoxRequiredError

class OnboardingActivity : BaseActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    private lateinit var welcomeBinding: FragmentOnboardingWelcomeBinding
    private lateinit var infoBinding: FragmentOnboardingInfoBinding
    private lateinit var countryUnavailableBinding: FragmentOnboardingCountryUnavailableBinding

    private lateinit var countryAdapter: ArrayAdapter<OnboardingViewModel.CountryViewItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        welcomeBinding = FragmentOnboardingWelcomeBinding.inflate(layoutInflater)
        infoBinding = FragmentOnboardingInfoBinding.inflate(layoutInflater)
        countryUnavailableBinding = FragmentOnboardingCountryUnavailableBinding.inflate(layoutInflater)

        showWelcomeScreen()
        hideActionBar()
    }

    private fun showWelcomeScreen() {
        setContentView(welcomeBinding.root)
        viewModel.countryListLiveData.observe(this) { updateCountrySelection(it) }
        viewModel.checkBoxErrorLiveData.observe(this) {
            if (!it.hasBeenHandled()) {
                it.setHandled(true)
                showCheckboxErrors()
            }
        }
        viewModel.showInfoScreenLiveData.observe(this) {
            if (!it.hasBeenHandled()) {
                it.setHandled(true)
                showInfoScreen()
            }
        }
        viewModel.showCountryUnavailableLiveData.observe(this) {
            if (!it.hasBeenHandled()) {
                it.setHandled(true)
                showCountryUnavailableScreen()
            }
        }

        welcomeBinding.termsTextView.movementMethod = LinkMovementMethod.getInstance()
        welcomeBinding.privacyTextView.movementMethod = LinkMovementMethod.getInstance()

        welcomeBinding.countryAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            viewModel.setSelectedCountryItem(countryAdapter.getItem(position)!!)
        }

        welcomeBinding.primaryActionButton.setOnClickListener {
            viewModel.onWelcomeActionButtonClicked(welcomeBinding.termsCheckBox.isChecked)
        }

        viewModel.initializeCountries()
    }

    private fun updateCountrySelection(countryUserData: OnboardingViewModel.CountryUserData) {
        countryAdapter = ArrayAdapter(this, R.layout.item_select_country, countryUserData.countryItems.toTypedArray())

        welcomeBinding.countryAutoCompleteTextView.apply {
            setAdapter(countryAdapter)
            setText(countryUserData.userCountryItem.toString(), false)
        }
    }

    private fun showCheckboxErrors() {
        showCheckBoxRequiredError(welcomeBinding.termsCheckBox, welcomeBinding.termsTextView)
    }

    private fun showInfoScreen() {
        setContentView(infoBinding.root)
        infoBinding.primaryActionButton.setOnClickListener { view: View? -> showRegistration() }
    }

    private fun showCountryUnavailableScreen() {
        setContentView(countryUnavailableBinding.root)
        countryUnavailableBinding.countryUnavailableDescriptionTextView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showRegistration() {
        val intent = Intent(this, RegistrationActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    companion object {
        const val WELCOME_SCREEN_SEEN_KEY = "welcome_screen_seen"
    }
}