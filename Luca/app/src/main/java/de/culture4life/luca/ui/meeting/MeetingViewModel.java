package de.culture4life.luca.ui.meeting;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.zxing.EncodeHintType;

import net.glxn.qrgen.android.QRCode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.R;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.meeting.MeetingAdditionalData;
import de.culture4life.luca.meeting.MeetingData;
import de.culture4life.luca.meeting.MeetingGuestData;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.venue.VenueDetailsViewModel;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class MeetingViewModel extends BaseViewModel {

    private final RegistrationManager registrationManager;
    private final MeetingManager meetingManager;
    private final CryptoManager cryptoManager;

    private final MutableLiveData<Boolean> isHostingMeeting = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> qrCode = new MutableLiveData<>();
    private final MutableLiveData<String> startTime = new MutableLiveData<>();
    private final MutableLiveData<String> duration = new MutableLiveData<>();
    private final MutableLiveData<List<Guest>> allGuests = new MutableLiveData<>();
    private final MutableLiveData<Bundle> bundle = new MutableLiveData<>();

    @Nullable
    private ViewError meetingError;

    public MeetingViewModel(@NonNull Application application) {
        super(application);
        this.registrationManager = this.application.getRegistrationManager();
        this.meetingManager = this.application.getMeetingManager();
        this.cryptoManager = this.application.getCryptoManager();
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.mergeArray(
                        registrationManager.initialize(application),
                        meetingManager.initialize(application),
                        cryptoManager.initialize(application)
                ))
                .andThen(meetingManager.isCurrentlyHostingMeeting()
                        .flatMapCompletable(hosting -> update(isHostingMeeting, hosting)))
                .andThen(
                        meetingManager.getCurrentMeetingDataIfAvailable().map(MeetingData::getCreationTimestamp)
                                .flatMapCompletable(creationTimeStamp -> update(startTime, TimeUtil.getReadableTime(application, creationTimeStamp))));
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                keepUpdatingMeetingHostState(),
                keepUpdatingMeetingData(),
                keepUpdatingMeetingDuration(),
                keepUpdatingQrCodes().delaySubscription(100, TimeUnit.MILLISECONDS)
        );
    }

    private Completable keepUpdatingMeetingHostState() {
        return meetingManager.getMeetingHostStateChanges()
                .flatMapCompletable(hostingMeeting -> update(isHostingMeeting, hostingMeeting));
    }

    private Completable keepUpdatingMeetingDuration() {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
                .flatMapMaybe(tick -> meetingManager.getCurrentMeetingDataIfAvailable())
                .map(meetingData -> TimeUtil.getCurrentMillis() - meetingData.getCreationTimestamp())
                .defaultIfEmpty(0L)
                .map(VenueDetailsViewModel::getReadableDuration)
                .flatMapCompletable(readableDuration -> update(duration, readableDuration));
    }

    private Completable keepUpdatingMeetingData() {
        return Observable.interval(0, 5, TimeUnit.SECONDS, Schedulers.io())
                .flatMapCompletable(tick -> meetingManager.updateMeetingGuestData()
                        .andThen(updateGuests())
                        .doOnError(throwable -> Timber.w("Unable to update guests: %s", throwable.toString()))
                        .onErrorComplete());
    }

    private Completable updateGuests() {
        return meetingManager.getCurrentMeetingDataIfAvailable()
                .flatMapCompletable(meetingData -> Completable.fromAction(() -> {
                    List<Guest> guests = new ArrayList<>();

                    for (MeetingGuestData guestData : meetingData.getGuestData()) {
                        String name = MeetingManager.getReadableGuestName(guestData);
                        boolean isCheckedOut = guestData.getCheckOutTimestamp() > 0 && guestData.getCheckOutTimestamp() < TimeUtil.getCurrentMillis();
                        guests.add(new Guest(name, !isCheckedOut));
                    }
                    updateAsSideEffect(allGuests, guests);
                }));
    }

    private Completable keepUpdatingQrCodes() {
        return Observable.interval(0, 1, TimeUnit.MINUTES, Schedulers.io())
                .flatMapCompletable(tick -> generateQrCodeData()
                        .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true))
                        .doOnSuccess(qrCodeData -> Timber.i("Generated new QR code data: %s", qrCodeData))
                        .flatMap(this::generateQrCode)
                        .flatMapCompletable(bitmap -> update(qrCode, bitmap))
                        .doFinally(() -> updateAsSideEffect(isLoading, false)));
    }

    private Single<String> generateQrCodeData() {
        Single<UUID> scannerId = meetingManager.restoreCurrentMeetingDataIfAvailable()
                .toSingle()
                .map(MeetingData::getScannerId);

        Single<String> additionalData = registrationManager.getRegistrationData()
                .map(MeetingAdditionalData::new)
                .map(meetingAdditionalData -> new Gson().toJson(meetingAdditionalData))
                .map(json -> json.getBytes(StandardCharsets.UTF_8))
                .flatMap(SerializationUtil::toBase64);

        return Single.zip(scannerId, additionalData, Pair::new)
                .flatMap(meetingIdAndData -> generateQrCodeData(meetingIdAndData.first, meetingIdAndData.second));
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    private Single<String> generateQrCodeData(@NonNull UUID scannerId, @NonNull String additionalData) {
        return Single.fromCallable(() -> new StringBuilder()
                .append(BuildConfig.API_BASE_URL)
                .append("/webapp/meeting/")
                .append(scannerId)
                .append("#")
                .append(additionalData)
                .toString());
    }

    private Single<Bitmap> generateQrCode(@NonNull String url) {
        return Single.fromCallable(() -> QRCode.from(url)
                .withSize(500, 500)
                .withHint(EncodeHintType.MARGIN, 0)
                .bitmap());
    }

    public void onMeetingEndRequested() {
        modelDisposable.add(endMeeting()
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .subscribe());
    }

    private Completable endMeeting() {
        return meetingManager.closePrivateLocation()
                .doOnSubscribe(disposable -> {
                    Timber.d("Ending meeting");
                    updateAsSideEffect(isLoading, true);
                    removeError(meetingError);
                })
                .doOnComplete(() -> updateAsSideEffect(isHostingMeeting, false))
                .doOnError(throwable -> {
                    Timber.w("Unable to end meeting: %s", throwable.toString());
                    meetingError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title)
                            .removeWhenShown()
                            .build();
                    addError(meetingError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    protected void changeLocation() {
        onMeetingEndRequested();
    }

    public LiveData<Boolean> getIsHostingMeeting() {
        return isHostingMeeting;
    }

    public LiveData<Bitmap> getQrCode() {
        return qrCode;
    }

    public LiveData<String> getDuration() {
        return duration;
    }

    public LiveData<String> getStartTime() {
        return startTime;
    }

    public LiveData<List<Guest>> getAllGuests() {
        return allGuests;
    }

    public LiveData<Bundle> getBundle() {
        return bundle;
    }

    public void setBundle(@Nullable Bundle bundle) {
        this.bundle.setValue(bundle);
    }
}
