package de.culture4life.luca.ui.myluca.listitems

import android.content.Context
import androidx.core.content.ContextCompat
import de.culture4life.luca.R
import de.culture4life.luca.document.Document
import de.culture4life.luca.document.Document.OUTCOME_NEGATIVE
import de.culture4life.luca.document.Document.OUTCOME_POSITIVE
import de.culture4life.luca.ui.myluca.DynamicContent
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.TimeUtil.getReadableTime
import de.culture4life.luca.util.toDateTime
import org.joda.time.LocalDate

open class TestResultItem(context: Context, document: Document) : DocumentItem(TYPE_TEST_RESULT, document) {

    init {
        title = getReadableTestType(context, document) + ": " + getReadableOutcome(context, document)
        deleteButtonText = context.getString(R.string.certificate_delete_test_action)
        imageResource = if (document.isVerified) R.drawable.ic_verified else R.drawable.ic_warning_triangle_orange
        provider = getReadableProvider(context, document.provider)
        if (document.isEudcc) barcode = generateQrCode(document.encodedData).blockingGet()

        setupColor(context, document)
        setupTopContent(context)
        setupCollapsableContent(context)
    }

    private fun setupTopContent(context: Context) {
        topContent.clear()
        addTopContent(DynamicContent("${document.firstName} ${document.lastName}", ""))
        val time = context.getString(R.string.document_time, getReadableTime(context, document.resultTimestamp))
        addTopContent(DynamicContent(context.getString(R.string.certificate_issued), time))

        val resultDate = document.resultTimestamp.toDateTime()
        val duration = TimeUtil.getReadableDateTimeDifference(context, resultDate)
        addTopContent(
            DynamicContent(
                context.getString(R.string.certificate_testing_done_before),
                duration,
                if (resultDate.toLocalDate().equals(LocalDate.now())) R.drawable.ic_rocket else null
            )
        )
    }

    private fun setupCollapsableContent(context: Context) {
        addCollapsedContent(DynamicContent(context.getString(R.string.certificate_issuer), document.labName))
        addCollapsedContent(DynamicContent(context.getString(R.string.certificate_tester), document.labDoctorName))
    }

    private fun setupColor(context: Context, document: Document) {
        color = ContextCompat.getColor(
            context,
            when (document.outcome) {
                OUTCOME_POSITIVE -> {
                    when {
                        document.isValidRecovery -> R.color.document_outcome_partially_vaccinated
                        TimeUtil.getCurrentMillis() > document.expirationTimestamp -> R.color.document_outcome_expired
                        else -> R.color.document_outcome_positive
                    }
                }
                OUTCOME_NEGATIVE -> R.color.document_outcome_negative
                else -> R.color.document_outcome_unknown
            }
        )
    }

    data class TestProcedure(val name: String, val date: String)

    companion object {
        @JvmStatic
        fun getReadableTestType(context: Context, document: Document) = when (document.type) {
            Document.TYPE_FAST -> context.getString(R.string.certificate_type_test_fast)
            Document.TYPE_PCR -> context.getString(R.string.certificate_type_test_pcr)
            else -> context.getString(R.string.certificate_type_unknown)
        }

        @JvmStatic
        fun getReadableOutcome(context: Context, document: Document) = when (document.outcome) {
            OUTCOME_POSITIVE -> context.getString(R.string.certificate_test_outcome_positive)
            OUTCOME_NEGATIVE -> context.getString(R.string.certificate_test_outcome_negative)
            else -> context.getString(R.string.certificate_test_outcome_unknown)
        }

        @JvmStatic
        fun getReadableResult(context: Context, document: Document) =
            context.getString(R.string.certificate_test_outcome, getReadableOutcome(context, document))
    }
}
