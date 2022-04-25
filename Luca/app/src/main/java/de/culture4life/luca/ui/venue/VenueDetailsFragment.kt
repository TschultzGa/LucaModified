package de.culture4life.luca.ui.venue

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ncorti.slidetoact.SlideToActView
import com.ncorti.slidetoact.SlideToActView.OnSlideCompleteListener
import com.ncorti.slidetoact.SlideToActView.OnSlideUserFailedListener
import com.tbruyelle.rxpermissions3.Permission
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentVenueDetailsBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.BaseQrCodeViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.venue.VenueDetailsViewModel.UrlType
import de.culture4life.luca.util.AccessibilityServiceUtil
import de.culture4life.luca.util.ClipboardUtil
import de.culture4life.luca.util.addTo
import five.star.me.FiveStarMe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class VenueDetailsFragment : BaseFragment<VenueDetailsViewModel>() {

    private lateinit var binding: FragmentVenueDetailsBinding
    private lateinit var urlTypeToButtonMap: Map<UrlType, MaterialButton>
    private var handleGrantedLocationAccess: Completable? = null
    private var handleDeniedLocationAccess: Completable? = null

    override fun getViewBinding(): ViewBinding {
        binding = FragmentVenueDetailsBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<VenueDetailsViewModel> {
        return VenueDetailsViewModel::class.java
    }

    override fun initializeViews() {
        super.initializeViews()
        observe(viewModel.subtitle) {
            binding.actionBarSubTitleTextView.text = it
            binding.actionBarSubTitleTextView.visibility = if (it == null) View.GONE else View.VISIBLE
        }
        observe(viewModel.title) { value -> binding.actionBarTitleTextView.text = value }
        observe(viewModel.checkInTime) { value ->
            binding.checkInTimeTextView.text = getFormattedString(R.string.venue_checked_in_time, value)
        }
        observe(viewModel.additionalDataTitle) { binding.additionalDataTitleTextView.text = it }
        observe(viewModel.additionalDataValue) { binding.additionalDataValueTextView.text = it }
        observe(viewModel.showAdditionalData) { setAdditionalDataVisibility(if (it) View.VISIBLE else View.GONE) }
        binding.childCounterTextView.setOnClickListener { viewModel.openChildrenView() }
        observe(viewModel.childCounter) {
            if (it == 0) {
                binding.childCounterTextView.visibility = View.GONE
            } else {
                binding.childCounterTextView.visibility = View.VISIBLE
                binding.childCounterTextView.text = it.toString()
            }
        }
        binding.childrenActionBarMenuImageView.setOnClickListener { viewModel.openChildrenView() }
        observe(viewModel.checkInDuration) { binding.checkInDurationTextView.text = it }
        observe(viewModel.askUrlConsent) { urlType -> showOpenUrlConsentBottomSheet(urlType) }
        initializeUrlViews()
        initializeAutomaticCheckoutViews()
        initializeSlideToActView()
        observe(viewModel.bundle, ::processBundle)
    }

    override fun onResume() {
        super.onResume()
        val arguments = arguments
        if (arguments != null) {
            viewModel.setBundle(arguments)
        }
        viewModel.updateLocationServicesStatus()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .doOnError { throwable: Throwable -> Timber.w("Error updating location services status. %s", throwable.message) }
            .subscribe()
            .addTo(viewDisposable)
    }

    override fun onStop() {
        viewModel.setBundle(null)
        super.onStop()
    }

    private fun initializeAutomaticCheckoutViews() {
        binding.automaticCheckoutInfoImageView.setOnClickListener { showAutomaticCheckOutInfoDialog() }
        observe(viewModel.hasLocationRestriction) { updateAutoCheckoutViewsVisibility() }
        observe(viewModel.isGeofencingSupported) { updateAutoCheckoutViewsVisibility() }
        binding.automaticCheckoutToggle.setOnCheckedChangeListener { _, isChecked ->
            if (binding.automaticCheckoutToggle.isEnabled && isChecked) {
                viewModel.isLocationConsentGiven()
                    .flatMapCompletable { isConsentGiven ->
                        if (isConsentGiven) {
                            viewModel.enableAutomaticCheckout()
                        } else {
                            showGrantLocationAccessDialog()
                        }
                        Completable.complete()
                    }
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            } else {
                viewModel.disableAutomaticCheckout()
            }
        }
        binding.automaticCheckoutToggle.setOnClickListener {
            viewModel.setAutomaticCheckoutActiveAsDefault(
                binding.automaticCheckoutToggle.isChecked
            )
        }
        observe(viewModel.shouldEnableAutomaticCheckOut) { binding.automaticCheckoutToggle.isChecked = it }
        observe(viewModel.shouldEnableLocationServices) { shouldEnable: Boolean ->
            if (shouldEnable && !viewModel.isLocationServiceEnabled) {
                handleGrantedLocationAccess = Completable.fromAction {
                    binding.automaticCheckoutToggle.isEnabled = false
                    binding.automaticCheckoutToggle.isChecked = true
                    binding.automaticCheckoutToggle.isEnabled = true
                    viewModel.enableAutomaticCheckout()
                }
                handleDeniedLocationAccess = Completable.fromAction { binding.automaticCheckoutToggle.isChecked = false }
                showLocationServicesDisabledDialog()
            }
        }
    }

    private fun initializeSlideToActView() {
        binding.slideToActView.onSlideCompleteListener = object : OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                viewModel.onSlideCompleted()
            }
        }
        binding.slideToActView.onSlideUserFailedListener = object : OnSlideUserFailedListener {
            override fun onSlideFailed(view: SlideToActView, isOutside: Boolean) {
                if (AccessibilityServiceUtil.isScreenReaderActive(requireContext())) {
                    viewModel.onSlideCompleted()
                } else {
                    Toast.makeText(context, R.string.venue_slider_clicked, Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (AccessibilityServiceUtil.isKeyboardConnected(requireContext())) {
            binding.slideToActView.setOnKeyListener { _, _, event ->
                if (AccessibilityServiceUtil.isKeyConfirmButton(event)) {
                    viewModel.onSlideCompleted()
                }
                false
            }
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
            // Work-around because resetSlider fails on SDK 22 in onDraw():
            //  java.lang.IllegalArgumentException: width and height must be > 0
            //    at com.ncorti.slidetoact.SlideToActView.onDraw(SlideToActView.kt:525)
            binding.slideToActView.isAnimateCompletion = false
        }
        observe(viewModel.isCheckedIn) { isCheckedIn ->
            binding.slideToActView.text = getString(if (isCheckedIn) R.string.venue_check_out_action else R.string.venue_check_in_action)
            binding.slideToActView.contentDescription = getString(
                if (isCheckedIn) R.string.venue_check_out_content_description
                else R.string.venue_check_in_content_description
            )
            binding.checkInDurationTextView.isVisible = isCheckedIn
            if (!isCheckedIn) {
                // navigation can be skipped if app is not open and user gets checked out by server or via the notification
                safeNavigateFromNavController(R.id.action_venueDetailFragment_to_checkInFragment, viewModel.bundle.value)
                AccessibilityServiceUtil.speak(requireContext(), getString(R.string.venue_checked_out))
                FiveStarMe.showRateDialogIfMeetsConditions(requireActivity())
            }
        }
        observe(viewModel.isLoading) { loading: Boolean? ->
            if (!loading!!) {
                binding.slideToActView.resetSlider()
            }
        }
    }

    private fun updateAutoCheckoutViewsVisibility() {
        val hasLocationRestriction = viewModel.hasLocationRestriction.value!!
        val enable = hasLocationRestriction && viewModel.isGeofencingSupported.value!!
        binding.automaticCheckoutGroup.visibility = if (enable) View.VISIBLE else View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ENABLE_LOCATION_SERVICES) {
            return
        }
        Completable.defer {
            if (viewModel.isLocationServiceEnabled) {
                Timber.i("Successfully enabled location services")
                handleGrantedLocationAccess
            } else {
                Timber.i("Failed to enable location services")
                handleDeniedLocationAccess
            }
        }
            .doOnError { throwable: Throwable -> Timber.e("Unable to handle location service change: %s", throwable.toString()) }
            .onErrorComplete()
            .doFinally { clearRequestResultActions() }
            .subscribe()
            .addTo(viewDisposable)
    }

    @SuppressLint("NewApi")
    override fun onPermissionResult(permission: Permission) {
        super.onPermissionResult(permission)
        val isLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION == permission.name
        val isBackgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION == permission.name
        if (permission.granted || !(isLocationPermission || isBackgroundLocationPermission)) {
            return
        }
        if (permission.shouldShowRequestPermissionRationale) {
            showRequestLocationPermissionRationale(permission, false)
        } else {
            showLocationPermissionPermanentlyDeniedError(permission)
        }
    }

    private fun processBundle(bundle: Bundle?) {
        if (bundle == null) {
            return
        }
        val barcode = bundle.getString(BaseQrCodeViewModel.BARCODE_DATA_KEY)
        if (barcode != null) {
            // is supposed to check-in into different location
            showLocationChangeDialog()
        }
    }

    private fun showRequestLocationPermissionRationale(permission: Permission, permanentlyDenied: Boolean) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setNegativeButton(R.string.action_cancel) { _, _ -> viewModel.onEnablingAutomaticCheckOutFailed() }
            .setOnCancelListener { _ -> viewModel.onEnablingAutomaticCheckOutFailed() }
            .setOnDismissListener { _ -> viewModel.onEnablingAutomaticCheckOutFailed() }
        if (Manifest.permission.ACCESS_FINE_LOCATION == permission.name || Manifest.permission.ACCESS_COARSE_LOCATION == permission.name || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setTitle(R.string.auto_checkout_location_access_title)
            builder.setMessage(R.string.auto_checkout_location_access_description)
        } else {
            builder.setTitle(R.string.auto_checkout_background_location_access_title)
            builder.setMessage(
                getString(
                    R.string.auto_checkout_background_location_access_description,
                    application.packageManager.backgroundPermissionOptionLabel
                )
            )
        }
        if (permanentlyDenied) {
            builder.setPositiveButton(R.string.action_settings) { _, _ -> application.openAppSettings() }
        } else {
            builder.setPositiveButton(R.string.action_grant) { _, _ ->
                if (Manifest.permission.ACCESS_FINE_LOCATION == permission.name || Manifest.permission.ACCESS_COARSE_LOCATION == permission.name) {
                    viewModel.requestLocationPermissionForAutomaticCheckOut()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    viewModel.requestBackgroundLocationPermissionForAutomaticCheckOut()
                }
            }
        }
        BaseDialogFragment(builder).show()
    }

    private fun showAutomaticCheckOutInfoDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auto_checkout_info_title)
            .setMessage(R.string.auto_checkout_info_description)
            .setPositiveButton(R.string.action_ok) { dialog, _ -> dialog.cancel() }
        BaseDialogFragment(builder).show()
    }

    private fun showGrantLocationAccessDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auto_checkout_location_access_title)
            .setMessage(R.string.auto_checkout_location_access_description)
            .setPositiveButton(R.string.action_enable) { _, _ ->
                viewModel.setLocationConsentGiven()
                viewModel.enableAutomaticCheckout()
            }
            .setNegativeButton(R.string.action_cancel) { dialog, _ ->
                binding.automaticCheckoutToggle.isChecked = false
                dialog.cancel()
            }
        BaseDialogFragment(builder).show()
    }

    private fun showLocationServicesDisabledDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auto_checkout_enable_location_title)
            .setMessage(R.string.auto_checkout_enable_location_description)
            .setPositiveButton(R.string.action_settings) { _, _ -> requestLocationServiceActivation() }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                if (handleDeniedLocationAccess != null) {
                    handleDeniedLocationAccess!!.onErrorComplete()
                        .doFinally { clearRequestResultActions() }
                        .subscribe()
                }
            }
        BaseDialogFragment(builder).show()
    }

    private fun showLocationChangeDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.venue_change_location_title)
            .setMessage(R.string.venue_change_location_description)
            .setPositiveButton(R.string.action_change) { _, _ -> viewModel.changeLocation() }
            .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.cancel() }
            .setOnCancelListener { viewModel.setBundle(null) }
        BaseDialogFragment(builder).show()
    }

    private fun requestLocationServiceActivation() {
        Timber.d("Requesting to enable location services")
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivityForResult(intent, REQUEST_ENABLE_LOCATION_SERVICES)
    }

    private fun clearRequestResultActions() {
        handleGrantedLocationAccess = null
        handleDeniedLocationAccess = null
    }

    private fun showLocationPermissionPermanentlyDeniedError(permission: Permission) {
        val context = context ?: return
        val viewError = ViewError.Builder(context)
            .withTitle(getString(R.string.missing_permission_arg, getString(R.string.permission_name_location)))
            .withDescription(getString(R.string.missing_permission_arg, getString(R.string.permission_name_location)))
            .withResolveLabel(getString(R.string.action_resolve))
            .withResolveAction(Completable.fromAction { showRequestLocationPermissionRationale(permission, true) })
            .build()
        showErrorAsSnackbar(viewError)
    }

    private fun setAdditionalDataVisibility(visibility: Int) {
        binding.additionalDataTitleTextView.visibility = visibility
        binding.additionalDataValueTextView.visibility = visibility
    }

    /*
        URLs
     */

    private fun initializeUrlViews() {
        urlTypeToButtonMap = mapOf(
            UrlType.MENU to binding.menuButton,
            UrlType.PROGRAM to binding.programButton,
            UrlType.MAP to binding.mapButton,
            UrlType.WEBSITE to binding.websiteButton,
            UrlType.GENERAL to binding.generalButton
        )
        urlTypeToButtonMap.forEach { (type, itemView) ->
            with(itemView) {
                setOnClickListener { viewModel.openProvidedUrlOrAskConsent(type) }
                setOnLongClickListener {
                    copyUrlToClipboard(type)
                    true
                }
            }
        }
        observe(viewModel.providedUrls, this::showUrls)
        binding.reportImageView.setOnClickListener { viewModel.reportAbuse(requireActivity()) }
    }

    private fun showUrls(providedUrls: List<Pair<UrlType, String>>) {
        binding.urlsScrollView.isVisible = providedUrls.isNotEmpty()
        urlTypeToButtonMap.map { it.value }.forEach { it.isVisible = false } // hide all items
        providedUrls.map { urlTypeToButtonMap[it.first] }.forEach { it?.isVisible = true } // show available items
    }

    private fun copyUrlToClipboard(urlType: UrlType) {
        val locationName = viewModel.checkInData.value?.locationDisplayName
        val readableUrlType = urlTypeToButtonMap[urlType]?.text
        val label = getString(R.string.venue_url_clipboard_label, locationName, readableUrlType)
        val url = viewModel.getProvidedUrl(urlType)
        ClipboardUtil.copy(requireContext(), label, url!!)
        Toast.makeText(requireContext(), R.string.venue_url_clipboard_toast, Toast.LENGTH_SHORT).show()
    }

    private fun showOpenUrlConsentBottomSheet(urlType: UrlType) {
        val locationName = viewModel.checkInData.value?.locationDisplayName
        val readableUrlType = urlTypeToButtonMap[urlType]?.text
        val url = viewModel.getProvidedUrl(urlType)
        VenueUrlConsentBottomSheetFragment.newInstance(locationName, readableUrlType?.toString(), url)
            .show(parentFragmentManager, VenueUrlConsentBottomSheetFragment.TAG)
    }

    companion object {
        private const val REQUEST_ENABLE_LOCATION_SERVICES = 2
    }
}
