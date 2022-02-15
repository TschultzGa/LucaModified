package de.culture4life.luca.ui.lucaconnect.children

import android.app.Application
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.registration.ConnectKritisData
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.util.StringSanitizeUtil
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class KritisViewModel(app: Application) : BaseFlowChildViewModel(app) {

    val industryInputError = MutableLiveData<Boolean>()
    val companyInputError = MutableLiveData<Boolean>()
    val hasErrors = MediatorLiveData<Boolean>().apply {
        fun combine() {
            val hasValidIndustry = industryInputError.value
            val hasValidCompany = companyInputError.value

            value = hasValidIndustry == false || hasValidCompany == false
        }

        addSource(industryInputError) { combine() }
        addSource(companyInputError) { combine() }
    }

    fun onActionButtonClicked(
        isCriticalInfrastructure: Boolean?,
        isWorkingWithVulnerableGroup: Boolean?,
        industry: String?,
        company: String?
    ) {
        createConnectKritisData(isCriticalInfrastructure, isWorkingWithVulnerableGroup, industry, company)
            .flatMapCompletable { connectKritisData ->
                Completable.mergeArray(
                    application.connectManager.initialize(application),
                    application.connectManager.persistConnectKritisData(connectKritisData)
                )
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorComplete()
            .subscribe {
                sharedViewModel?.navigateToNext()
            }
            .addTo(modelDisposable)
    }

    fun validateIndustryInput(value: String?) {
        updateAsSideEffect(industryInputError, isValidIndustry(value))
    }

    fun validateCompanyInput(value: String?) {
        updateAsSideEffect(companyInputError, isValidCompany(value))
    }

    private fun isValidIndustry(value: String?): Boolean {
        return value.isNullOrEmpty() || value.length <= 100
    }

    private fun isValidCompany(value: String?): Boolean {
        return value.isNullOrEmpty() || value.length <= 100
    }

    private fun createConnectKritisData(
        isCriticalInfrastructure: Boolean?,
        isWorkingWithVulnerableGroup: Boolean?,
        industry: String?,
        company: String?
    ): Single<ConnectKritisData> {
        return Single.just(
            ConnectKritisData.create(
                isCriticalInfrastructure = isCriticalInfrastructure,
                isWorkingWithVulnerableGroup = isWorkingWithVulnerableGroup,
                industry = industry,
                company = company
            )
        )
    }
}