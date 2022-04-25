package de.culture4life.luca.ui.myluca.listitems;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import de.culture4life.luca.R;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.ui.myluca.DynamicContent;
import de.culture4life.luca.util.TimeUtil;

public class AppointmentItem extends TestResultItem {

    public AppointmentItem(@NonNull Context context, @NonNull Document document) {
        super(context, document);

        this.title = context.getString(R.string.appointment_title, document.getFirstName());
        this.color = ContextCompat.getColor(context, R.color.document_appointment);
        this.deleteButtonText = context.getString(R.string.appointment_delete_action);
        this.provider = null;
        String time = context.getString(R.string.document_time, TimeUtil.getReadableTime(context, document.getResultTimestamp()));

        topContent.clear();
        addTopContent(new DynamicContent(document.getLabName(), null, null));
        addTopContent(new DynamicContent(context.getString(R.string.appointment_date), time, null));

        collapsedContent.clear();
        addCollapsedContent(new DynamicContent(context.getString(R.string.appointment_address), document.getLastName(), null));
    }

}
