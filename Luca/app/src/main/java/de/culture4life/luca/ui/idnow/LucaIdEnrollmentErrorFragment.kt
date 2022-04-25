package de.culture4life.luca.ui.idnow

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentLucaIdEnrollmentErrorBinding
import de.culture4life.luca.ui.BaseFragment

class LucaIdEnrollmentErrorFragment : BaseFragment<LucaIdEnrollmentErrorViewModel>() {

    private lateinit var binding: FragmentLucaIdEnrollmentErrorBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaIdEnrollmentErrorBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass() = LucaIdEnrollmentErrorViewModel::class.java
}
