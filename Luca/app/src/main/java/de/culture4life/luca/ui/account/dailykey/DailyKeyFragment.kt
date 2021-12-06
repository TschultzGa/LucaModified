package de.culture4life.luca.ui.account.dailykey

import android.view.View
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentDailyKeyBinding
import de.culture4life.luca.ui.BaseFragment
import io.reactivex.rxjava3.core.Completable
import org.joda.time.format.DateTimeFormat

class DailyKeyFragment : BaseFragment<DailyKeyViewModel>() {

    private lateinit var binding: FragmentDailyKeyBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentDailyKeyBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<DailyKeyViewModel> =
        DailyKeyViewModel::class.java

    override fun initializeViews(): Completable {
        return super.initializeViews()
            .andThen { initializeObservers() }
    }

    private fun initializeObservers() {
        viewModel.dailyPublicKeyLiveData.observe(viewLifecycleOwner, {
            val readableDateFormat = DateTimeFormat.forPattern(application.getString(R.string.time_format))
            binding.dateValueTextView.text = readableDateFormat.print(it.creationTimestamp)
        })

        viewModel.keyIssuerLiveData.observe(viewLifecycleOwner, {
            binding.issuerValueTextView.text = it.name
        })

        viewModel.hasVerifiedDailyPublicKey.observe(viewLifecycleOwner, ::updateSignedIndicator)

        viewModel.isLoading.observe(viewLifecycleOwner, ::updateLoadingView)

        binding.downloadCertificateButton.setOnClickListener {
            viewModel.exportDailyKey(getFileExportUri("luca-daily-key-chain.txt"))
        }
    }

    private fun updateLoadingView(isLoading: Boolean) {
        if (isLoading) {
            binding.contentGroup.visibility = View.INVISIBLE
            binding.loadingProgressBar.visibility = View.VISIBLE
        } else {
            binding.contentGroup.visibility = View.VISIBLE
            binding.loadingProgressBar.visibility = View.INVISIBLE
        }
    }

    private fun updateSignedIndicator(isSigned: Boolean) {
        if (isSigned) {
            binding.signedImageView.apply {
                setImageResource(R.drawable.ic_key_signed)
                contentDescription = getString(R.string.action_yes)
            }
        } else {
            binding.signedImageView.apply {
                setImageResource(R.drawable.ic_key_unsigned)
                contentDescription = getString(R.string.action_no)
            }
        }
    }

}