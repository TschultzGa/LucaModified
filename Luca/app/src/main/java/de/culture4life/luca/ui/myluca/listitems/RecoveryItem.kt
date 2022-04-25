package de.culture4life.luca.ui.myluca.listitems

import android.content.Context
import androidx.core.content.ContextCompat
import de.culture4life.luca.R
import de.culture4life.luca.document.Document
import de.culture4life.luca.ui.myluca.DynamicContent
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.getReadableDate
import de.culture4life.luca.util.toDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

/**
 * Item shown on UI for a Recovery certificate
 */
class RecoveryItem(context: Context, document: Document) : VaccinationItem(context, document) {

    init {
        setTitleAndColor(context, document)
        deleteButtonText = context.getString(R.string.certificate_delete_certificate_action)
        setupTopContent(context)
        setupCollapsedContent(context)
    }

    private fun setupTopContent(context: Context) {
        topContent.clear()
        addTopContent(DynamicContent("${document.firstName} ${document.lastName}", ""))
        val validUntil = context.getReadableDate(document.expirationTimestamp)
        addTopContent(DynamicContent(context.getString(R.string.certificate_valid_until), validUntil))

        val now = DateTime.now(DateTimeZone.getDefault())
        val recoveryDate = document.testingTimestamp.toDateTime()
        val newerThanThreeMonths = recoveryDate.isAfter(now.minusMonths(3))
        addTopContent(
            DynamicContent(
                context.getString(R.string.certificate_recovered_before),
                TimeUtil.getReadableDateTimeDifference(context, recoveryDate, now),
                if (newerThanThreeMonths) R.drawable.ic_rocket else null
            )
        )
    }

    private fun setupCollapsedContent(context: Context) {
        collapsedContent.clear()
        val time = context.getReadableDate(document.testingTimestamp)
        addCollapsedContent(DynamicContent(context.getString(R.string.certificate_issued), "$time\n${document.labName}"))
        val date = context.getReadableDate(document.dateOfBirth)
        addCollapsedContent(DynamicContent(context.getString(R.string.certificate_birthday), date))
    }

    private fun setTitleAndColor(context: Context, document: Document) {
        title = context.getString(R.string.certificate_type_recovery)
        color = when {
            !document.isValidRecovery -> ContextCompat.getColor(context, R.color.document_outcome_expired)
            document.outcome == Document.OUTCOME_PARTIALLY_IMMUNE -> ContextCompat.getColor(context, R.color.document_outcome_partially_vaccinated)
            document.outcome == Document.OUTCOME_FULLY_IMMUNE -> {
                val timeUntilValid = document.validityStartTimestamp - TimeUtil.getCurrentMillis()
                ContextCompat.getColor(
                    context,
                    if (timeUntilValid <= 0) R.color.document_outcome_fully_recovered else R.color.document_outcome_fully_vaccinated_but_not_yet_valid
                )
            }
            else -> ContextCompat.getColor(context, R.color.document_outcome_unknown)
        }
    }
}
