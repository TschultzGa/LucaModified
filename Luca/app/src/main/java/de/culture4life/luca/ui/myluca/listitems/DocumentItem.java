package de.culture4life.luca.ui.myluca.listitems;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.EncodeHintType;

import net.glxn.qrgen.android.QRCode;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import de.culture4life.luca.R;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.ui.myluca.DynamicContent;
import io.reactivex.rxjava3.core.Single;
import kotlin.Pair;

public abstract class DocumentItem extends MyLucaListItem implements Serializable {

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_TEST_RESULT = 1;
    public static final int TYPE_APPOINTMENT = 2;

    @IntDef({TYPE_UNKNOWN, TYPE_TEST_RESULT, TYPE_APPOINTMENT})
    @Retention(SOURCE)
    public @interface Type {

    }

    @Type
    protected final int type;

    @Nonnull
    protected final Document document;

    protected final List<DynamicContent> topContent = new ArrayList<>();
    protected final List<DynamicContent> collapsedContent = new ArrayList<>();

    @Nullable
    protected String sectionHeader;

    @Nullable
    protected String title;

    @Nullable
    protected String provider;

    @Nullable
    protected Bitmap barcode;

    @Nullable
    protected String deleteButtonText;

    @ColorInt
    protected int color;

    @DrawableRes
    protected int imageResource;
    protected boolean isExpanded = false;

    public DocumentItem(@Type int type, @Nonnull Document document) {
        this.type = type;
        this.document = document;
    }

    @Nonnull
    protected static Single<Bitmap> generateQrCode(@NonNull String data) {
        return Single.fromCallable(() -> QRCode.from(data)
                .withSize(500, 500)
                .withHint(EncodeHintType.MARGIN, 0)
                .bitmap());
    }

    @Nonnull
    protected static String getReadableProvider(@NonNull Context context, @Nullable String provider) {
        if (provider == null || TextUtils.isEmpty(provider)) {
            return context.getString(R.string.unknown);
        } else {
            return provider;
        }
    }

    public int getType() {
        return type;
    }

    @Nonnull
    public Document getDocument() {
        return document;
    }

    @Nullable
    public String getSectionHeader() {
        return sectionHeader;
    }

    public void setSectionHeader(@Nonnull String sectionHeader) {
        this.sectionHeader = sectionHeader;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getProvider() {
        return provider;
    }

    @Nullable
    public Bitmap getBarcode() {
        return barcode;
    }

    @Nullable
    public String getDeleteButtonText() {
        return deleteButtonText;
    }

    @Nonnull
    public List<DynamicContent> getTopContent() {
        return topContent;
    }

    @Nonnull
    public List<DynamicContent> getCollapsedContent() {
        return collapsedContent;
    }

    protected void addTopContent(@NonNull DynamicContent dynamicContent) {
        topContent.add(dynamicContent);
    }

    protected void addCollapsedContent(@NonNull DynamicContent dynamicContent) {
        collapsedContent.add(dynamicContent);
    }

    @ColorInt
    public int getColor() {
        return color;
    }

    @DrawableRes
    public int getImageResource() {
        return imageResource;
    }

    @Override
    public long getTimestamp() {
        return document.getResultTimestamp();
    }
}
