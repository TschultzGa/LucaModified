package de.culture4life.luca.ui.myluca

import android.content.Context
import androidx.core.content.ContextCompat
import de.culture4life.luca.R
import de.culture4life.luca.document.Document
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.getReadableDate
import de.culture4life.luca.util.toDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

/**
 * Item shown on UI for a vaccination certificate
 */
open class VaccinationItem(context: Context, document: Document) :
    TestResultItem(context, document) {

    init {
        val firstProcedure = document.procedures.first()
        val procedureNumber = String.format("(%d/%d)", firstProcedure.doseNumber, firstProcedure.totalSeriesOfDoses)
        title = context.getString(R.string.document_type_vaccination, procedureNumber)
        provider = getReadableProvider(context, document.provider)
        deleteButtonText = context.getString(R.string.delete_certificate_action)
        resultTimestamp = document.resultTimestamp
        setupColors(context, document)
        setupTopContent(context)
        setupCollapsedContent(context)
    }

    private fun setupTopContent(context: Context) {
        topContent.clear()
        addTopContent(DynamicContent("${document.firstName} ${document.lastName}", ""))
        val validityStartTime = context.getString(
            R.string.document_result_time,
            context.getReadableDate(document.validityStartTimestamp)
        )
        addTopContent(DynamicContent(context.getString(R.string.vaccination_valid_from_date_label), validityStartTime))

        val now = DateTime.now(DateTimeZone.getDefault())
        val vaccinationDate = document.testingTimestamp.toDateTime()
        val newerThanThreeMonths = vaccinationDate.isAfter(now.minusMonths(3))
        addTopContent(
            DynamicContent(
                context.getString(R.string.vaccination_done_before_label),
                TimeUtil.getReadableDateTimeDifference(context, vaccinationDate, now),
                if (newerThanThreeMonths) R.drawable.ic_rocket else null
            )
        )
    }

    private fun setupCollapsedContent(context: Context) {
        collapsedContent.clear()
        for (testProcedure in getTestProcedures(context, document)) {
            addCollapsedContent(DynamicContent(testProcedure.name, testProcedure.date))
        }
        addCollapsedContent(DynamicContent(context.getString(R.string.document_lab_issuer), document.labName))
        val date = context.getReadableDate(document.dateOfBirth)
        addCollapsedContent(DynamicContent(context.getString(R.string.birthday_label), date))
    }

    private fun setupColors(context: Context, document: Document) {
        color = when {
            !document.isValidVaccination -> ContextCompat.getColor(context, R.color.document_outcome_expired)
            document.outcome == Document.OUTCOME_PARTIALLY_IMMUNE -> ContextCompat.getColor(context, R.color.document_outcome_partially_vaccinated)
            document.outcome == Document.OUTCOME_FULLY_IMMUNE -> {
                val timeUntilValid = document.validityStartTimestamp - TimeUtil.getCurrentMillis()
                ContextCompat.getColor(
                    context,
                    if (timeUntilValid <= 0) R.color.document_outcome_fully_vaccinated else R.color.document_outcome_fully_vaccinated_but_not_yet_valid
                )
            }
            else -> ContextCompat.getColor(context, R.color.document_outcome_unknown)
        }
    }

    private fun getTestProcedures(context: Context, document: Document): List<TestProcedure> {
        val testProcedures = arrayListOf<TestProcedure>()
        document.procedures?.let { procedures ->
            for (procedure in procedures) {
                val label = context.getString(
                    R.string.document_vaccination_procedure,
                    procedure.doseNumber.toString()
                )
                val description = getProcedureDescription(context, procedure)
                testProcedures.add(TestProcedure(label, description))
            }
        }
        return testProcedures
    }

    private fun getProcedureDescription(
        context: Context,
        procedure: Document.Procedure
    ): String {
        val procedureName = context.getString(
            when (procedure.type) {
                Document.Procedure.Type.VACCINATION_COMIRNATY -> R.string.vaccine_comirnaty
                Document.Procedure.Type.VACCINATION_JANNSEN -> R.string.vaccine_jannsen
                Document.Procedure.Type.VACCINATION_MODERNA -> R.string.vaccine_moderna
                Document.Procedure.Type.VACCINATION_VAXZEVRIA -> R.string.vaccine_vaxzevria
                Document.Procedure.Type.VACCINATION_SPUTNIK_V -> R.string.vaccine_sputnik
                Document.Procedure.Type.RECOVERY -> R.string.procedure_recovery
                else -> R.string.unknown
            }
        )
        val time = context.getString(
            R.string.document_result_time,
            context.getReadableDate(procedure.timestamp)
        )
        return time + "\n" + procedureName
    }
}
