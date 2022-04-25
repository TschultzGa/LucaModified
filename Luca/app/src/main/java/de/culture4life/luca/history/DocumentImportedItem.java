package de.culture4life.luca.history;

import androidx.annotation.NonNull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import de.culture4life.luca.document.Document;

public class DocumentImportedItem extends HistoryItem {

    @SerializedName("testResult")
    @Expose
    private Document document;

    public DocumentImportedItem() {
        super(HistoryItem.TYPE_DOCUMENT_IMPORTED);
    }

    public DocumentImportedItem(@NonNull Document document) {
        this.document = document;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    @Override
    public String toString() {
        return "DocumentImportedItem{" +
                "document=" + document +
                ", type=" + type +
                ", relatedId='" + relatedId + '\'' +
                ", timestamp=" + timestamp +
                ", displayName='" + displayName + '\'' +
                "} " + super.toString();
    }

}
