package de.culture4life.luca.history;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import de.culture4life.luca.Manager;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.children.ChildrenManager;
import de.culture4life.luca.dataaccess.AccessedTraceData;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.meeting.MeetingData;
import de.culture4life.luca.meeting.MeetingGuestData;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.RegistrationData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.subjects.PublishSubject;
import timber.log.Timber;

public class HistoryManager extends Manager {

    public static final long MAXIMUM_CHECK_IN_DURATION = TimeUnit.DAYS.toMillis(1);
    public static final long SHARE_DATA_DURATION = TimeUnit.DAYS.toMillis(14);
    public static final int KEEP_DATA_DAYS = 28;
    public static final long KEEP_DATA_DURATION = TimeUnit.DAYS.toMillis(KEEP_DATA_DAYS);
    public static final String KEY_HISTORY_ITEMS = "history_items_2";

    private final PreferencesManager preferencesManager;
    private final ChildrenManager childrenManager;

    private final PublishSubject<HistoryItem> newItemPublisher;

    @Nullable
    private Observable<HistoryItem> cachedHistoryItems;

    public HistoryManager(@NonNull PreferencesManager preferencesManager, @NonNull ChildrenManager childrenManager) {
        this.preferencesManager = preferencesManager;
        this.childrenManager = childrenManager;
        this.newItemPublisher = PublishSubject.create();
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return preferencesManager.initialize(context)
                .andThen(deleteOldItems());
    }

    public Completable addCheckInItem(@NonNull CheckInData checkInData) {
        return Single.just(checkInData)
                .map(data -> {
                    CheckInItem item = new CheckInItem();
                    item.setRelatedId(checkInData.getTraceId());
                    item.setTimestamp(checkInData.getTimestamp());
                    item.setDisplayName(checkInData.getLocationDisplayName());
                    item.setContactDataMandatory(checkInData.isContactDataMandatory());
                    return item;
                })
                .flatMapCompletable(this::addItem);
    }

    public Completable addCheckOutItem(@NonNull CheckInData checkInData) {
        return Single.just(checkInData)
                .map(data -> {
                    CheckOutItem item = new CheckOutItem();
                    item.setRelatedId(checkInData.getTraceId());
                    item.setTimestamp(Math.min(checkInData.getTimestamp() + MAXIMUM_CHECK_IN_DURATION, System.currentTimeMillis()));
                    item.setDisplayName(checkInData.getLocationDisplayName());
                    return item;
                })
                .flatMap(this::setChildren)
                .flatMapCompletable(this::addItem)
                .andThen(childrenManager.clearCheckIns());
    }

    public Completable addContactDataUpdateItem(@NonNull RegistrationData registrationData) {
        return Single.just(registrationData)
                .map(data -> {
                    HistoryItem item = new HistoryItem(HistoryItem.TYPE_CONTACT_DATA_UPDATE);
                    item.setRelatedId(registrationData.getId() != null ? registrationData.getId().toString() : null);
                    item.setDisplayName(registrationData.getFullName());
                    return item;
                })
                .flatMapCompletable(this::addItem);
    }

    public Completable addMeetingStartedItem(@NonNull MeetingData meetingData) {
        return Single.fromCallable(() -> {
            HistoryItem item = new HistoryItem(HistoryItem.TYPE_MEETING_STARTED);
            item.setRelatedId(meetingData.getLocationId().toString());
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addMeetingEndedItem(@NonNull MeetingData meetingData) {
        return Single.fromCallable(() -> {
            MeetingEndedItem item = new MeetingEndedItem();
            item.setRelatedId(meetingData.getLocationId().toString());
            for (MeetingGuestData guestData : meetingData.getGuestData()) {
                item.getGuests().add(MeetingManager.getReadableGuestName(guestData));
            }
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addHistoryDeletedItem() {
        return Single.fromCallable(() -> {
            HistoryItem item = new HistoryItem(HistoryItem.TYPE_DATA_DELETED);
            item.setDisplayName("");
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addTraceDataAccessedItem(@NonNull AccessedTraceData accessedTraceData) {
        return Single.fromCallable(() -> {
            TraceDataAccessedItem item = new TraceDataAccessedItem();
            item.setHealthDepartmentId(accessedTraceData.getHealthDepartment().getId());
            item.setTraceId(accessedTraceData.getTraceId());
            item.setRelatedId(item.getHealthDepartmentId() + ";" + item.getTraceId());
            item.setTimestamp(accessedTraceData.getAccessTimestamp());
            item.setHealthDepartmentName(accessedTraceData.getHealthDepartment().getName());
            item.setLocationName(accessedTraceData.getLocationName());
            item.setCheckInTimestamp(accessedTraceData.getCheckInTimestamp());
            item.setCheckOutTimestamp(accessedTraceData.getCheckOutTimestamp());
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addDataSharedItem(@NonNull String tracingTan, int days) {
        return Single.fromCallable(() -> new DataSharedItem(tracingTan, days))
                .flatMapCompletable(this::addItem);
    }

    public Completable addDocumentImportedItem(@NonNull Document document) {
        return Single.fromCallable(() -> new DocumentImportedItem(document))
                .flatMapCompletable(this::addItem);
    }

    public Completable addItem(@NonNull HistoryItem historyItem) {
        return persistItemsToPreferences(getItems()
                .mergeWith(Observable.just(historyItem)))
                .andThen(invalidateItemCache())
                .doOnComplete(() -> newItemPublisher.onNext(historyItem))
                .doOnSubscribe(disposable -> Timber.d("Adding history item: %s", historyItem));
    }

    public Observable<HistoryItem> getItems() {
        return Observable.defer(() -> {
            if (cachedHistoryItems == null) {
                cachedHistoryItems = restoreItemsFromPreferences().cache();
            }
            return cachedHistoryItems;
        });
    }

    public Observable<HistoryItem> getNewItems() {
        return newItemPublisher
                .doOnNext(historyItem -> Timber.d("New history item: %s", historyItem));
    }

    public Completable clearItems() {
        return persistItemsToPreferences(Observable.empty())
                .andThen(invalidateItemCache())
                .andThen(addHistoryDeletedItem());
    }

    protected Completable deleteOldItems() {
        return Single.fromCallable(() -> System.currentTimeMillis() - KEEP_DATA_DURATION)
                .flatMapCompletable(this::deleteItemsCreatedBefore);
    }

    private Completable deleteItemsCreatedBefore(long timestamp) {
        return deleteItems(historyItem -> historyItem.getTimestamp() < timestamp)
                .doOnComplete(() -> Timber.d("Deleted history items created before %d", timestamp));
    }

    public Completable deleteItems(Predicate<HistoryItem> predicate) {
        Observable<HistoryItem> remainingItems = getItems()
                .filter(historyItem -> !predicate.test(historyItem));

        return persistItemsToPreferences(remainingItems)
                .andThen(invalidateItemCache());
    }

    private Completable invalidateItemCache() {
        return Completable.fromAction(() -> cachedHistoryItems = null);
    }

    private Observable<HistoryItem> restoreItemsFromPreferences() {
        return preferencesManager.restoreOrDefault(KEY_HISTORY_ITEMS, new HistoryItemContainer())
                .flatMapObservable(Observable::fromIterable)
                .distinct(historyItem -> historyItem.getRelatedId() + historyItem.getType())
                .sorted((first, second) -> Long.compare(second.getTimestamp(), first.getTimestamp()));
    }

    private Completable persistItemsToPreferences(Observable<HistoryItem> historyItems) {
        return historyItems.toList()
                .map(HistoryItemContainer::new)
                .flatMapCompletable(historyItemContainer -> preferencesManager.persist(KEY_HISTORY_ITEMS, historyItemContainer));
    }

    private Single<CheckOutItem> setChildren(@NonNull CheckOutItem item) {
        return childrenManager.getCheckedInChildren()
                .flatMap(children -> Observable.fromIterable(children)
                        .map(child -> child.getFirstName()).toList())
                .doOnSuccess(item::setChildren)
                .map(names -> item);
    }

    public static String createUnorderedList(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String item : items) {
            stringBuilder.append("- ").append(item).append(System.lineSeparator());
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1); // remove last line break
        return stringBuilder.toString();
    }

    public static String createOrderedList(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            stringBuilder.append(i + 1).append("\t\t\t").append(items.get(i)).append(System.lineSeparator());
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1); // remove last line break
        return stringBuilder.toString();
    }

    public static String createCsv(@NonNull List<String> items) {
        return items.stream().collect(Collectors.joining(", "));
    }

}
