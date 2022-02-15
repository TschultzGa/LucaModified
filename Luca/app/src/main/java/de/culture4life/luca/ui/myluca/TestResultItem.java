package de.culture4life.luca.ui.myluca;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import de.culture4life.luca.R;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.util.TimeUtil;

public class TestResultItem extends MyLucaListItem {

    public static class TestProcedure {

        private final String name;
        private final String date;

        public TestProcedure(@NonNull String name, @NonNull String date) {
            this.name = name;
            this.date = date;
        }

        public String getName() {
            return name;
        }

        public String getDate() {
            return date;
        }

    }

    public TestResultItem(@NonNull Context context, @NonNull Document document) {
        super(TYPE_TEST_RESULT, document);

        this.title = getReadableTestType(context, document) + ": " + getReadableOutcome(context, document);
        this.provider = getReadableProvider(context, document.getProvider());
        if (document.isEudcc()) {
            this.barcode = generateQrCode(document.getEncodedData()).blockingGet();
        }
        this.color = getColor(context, document);
        this.deleteButtonText = context.getString(R.string.delete_test_action);
        this.imageResource = document.isVerified() ? R.drawable.ic_verified : R.drawable.ic_warning_triangle_orange;

        String time = context.getString(R.string.document_result_time, TimeUtil.getReadableTime(context, document.getResultTimestamp()));
        addTopContent(context.getString(R.string.document_issued), time);

        String duration = TimeUtil.getReadableDurationWithPlural(TimeUtil.getCurrentMillis() - document.getResultTimestamp(), context).blockingGet();
        addTopContent(context.getString(R.string.document_created_before), duration);

        addCollapsedContent(context.getString(R.string.document_lab_issuer), document.getLabName());
        addCollapsedContent(context.getString(R.string.document_lab_tester), document.getLabDoctorName());
    }

    public static String getReadableTestType(@NonNull Context context, @NonNull Document document) {
        switch (document.getType()) {
            case Document.TYPE_FAST: {
                return context.getString(R.string.document_type_fast);
            }
            case Document.TYPE_PCR: {
                return context.getString(R.string.document_type_pcr);
            }
            default: {
                return context.getString(R.string.document_type_unknown);
            }
        }
    }

    public static String getReadableOutcome(@NonNull Context context, @NonNull Document document) {
        switch (document.getOutcome()) {
            case Document.OUTCOME_POSITIVE: {
                return context.getString(R.string.document_outcome_positive);
            }
            case Document.OUTCOME_NEGATIVE: {
                return context.getString(R.string.document_outcome_negative);
            }
            default: {
                return context.getString(R.string.document_outcome_unknown);
            }
        }
    }

    public static String getReadableResult(@NonNull Context context, @NonNull Document document) {
        return context.getString(R.string.document_outcome, getReadableOutcome(context, document));
    }

    @ColorInt
    private static int getColor(@NonNull Context context, @NonNull Document document) {
        switch (document.getOutcome()) {
            case Document.OUTCOME_POSITIVE: {
                if (document.isValidRecovery()) {
                    return ContextCompat.getColor(context, R.color.document_outcome_partially_vaccinated);
                } else {
                    return ContextCompat.getColor(context, R.color.document_outcome_positive);
                }
            }
            case Document.OUTCOME_NEGATIVE: {
                return ContextCompat.getColor(context, R.color.document_outcome_negative);
            }
            default: {
                return ContextCompat.getColor(context, R.color.document_outcome_unknown);
            }
        }
    }

}
