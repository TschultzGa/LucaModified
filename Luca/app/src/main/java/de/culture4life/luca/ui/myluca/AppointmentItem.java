package de.culture4life.luca.ui.myluca;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import de.culture4life.luca.R;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.util.TimeUtil;

public class AppointmentItem extends TestResultItem {

    public AppointmentItem(@NonNull Context context, @NonNull Document document) {
        super(context, document);

        this.title = context.getString(R.string.appointment_title, document.getFirstName());
        this.color = ContextCompat.getColor(context, R.color.document_appointment);
        this.deleteButtonText = context.getString(R.string.delete_appointment_action);
        this.provider = null;
        String time = context.getString(R.string.document_result_time, TimeUtil.getReadableTime(context, document.getResultTimestamp()));

        topContent.clear();
        addTopContent(new DynamicContent(document.getLabName(), null, null));
        addTopContent(new DynamicContent(context.getString(R.string.document_issued_at), time, null));

        collapsedContent.clear();
        addCollapsedContent(new DynamicContent(context.getString(R.string.appointment_address), document.getLastName(), null));
    }

}
