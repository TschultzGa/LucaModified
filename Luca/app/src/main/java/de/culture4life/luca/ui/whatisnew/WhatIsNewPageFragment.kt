package de.culture4life.luca.ui.whatisnew

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.culture4life.luca.databinding.FragmentWhatIsNewPageBinding
import de.culture4life.luca.util.safelySetImageResource

class WhatIsNewPageFragment : Fragment() {
    private lateinit var binding: FragmentWhatIsNewPageBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentWhatIsNewPageBinding.inflate(layoutInflater)
        arguments?.let { bundle ->
            if (bundle.containsKey(PAGE_IMAGE_RES_KEY)) {
                binding.whatIsNewHeaderImage.safelySetImageResource(bundle.getInt(PAGE_IMAGE_RES_KEY))
            }
            bundle.getString(PAGE_HEADING_KEY)?.let { heading ->
                binding.headingTextView.text = heading
            }
            bundle.getString(PAGE_DESCRIPTION_KEY)?.let { heading ->
                binding.descriptionTextView.text = heading
            }
        }
        return binding.root
    }

    companion object {
        const val PAGE_IMAGE_RES_KEY = "page_image_res_key"
        const val PAGE_HEADING_KEY = "page_heading_key"
        const val PAGE_DESCRIPTION_KEY = "page_description_key"
    }

}



