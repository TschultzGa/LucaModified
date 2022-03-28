package de.culture4life.luca.whatisnew

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.R
import de.culture4life.luca.notification.LucaNotificationManager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.RegistrationManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class WhatIsNewManager(
    private val preferencesManager: PreferencesManager,
    private val notificationManager: LucaNotificationManager,
    private val registrationManager: RegistrationManager
) : Manager() {

    private var cachedContentPages: Observable<WhatIsNewPage>? = null
    private var cachedMessages: Observable<WhatIsNewMessage>? = null
    private val messageUpdatesSubject: PublishSubject<WhatIsNewMessage> = PublishSubject.create()
    var isFirstSessionAfterAppUpdate: Boolean? = null

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            notificationManager.initialize(context),
            registrationManager.initialize(context)
        )
            .andThen(
                invoke(
                    Completable.defer {
                        if (LucaApplication.isRunningUnitTests() || LucaApplication.isRunningInstrumentationTests()) {
                            checkAndUpdateLastUsedVersionNumber()
                                .delay(1, TimeUnit.SECONDS)
                                .andThen(showNotificationsForUnseenMessagesIfRequired())
                        } else {
                            Completable.complete()
                        }
                    }
                )
            )
    }

    override fun dispose() {
        cachedContentPages = null
        cachedMessages = null
        isFirstSessionAfterAppUpdate = null
        super.dispose()
    }

    fun shouldWhatIsNewBeShown(): Single<Boolean> {
        return Single.zip(
            getIndexOfLastSeenPage(),
            getIndexOfMostRecentPage()
        ) { lastSeen, mostRecent -> lastSeen < mostRecent }
    }

    fun disableWhatIsNewScreenForCurrentVersion(): Completable {
        return getIndexOfMostRecentPage()
            .flatMapCompletable(this::saveLastSeenPageIndex)
    }

    /*
        Pages
     */

    fun markPageAsSeen(seenPageIndex: Int): Completable {
        return getIndexOfLastSeenPage()
            .flatMapCompletable { lastSeenIndex ->
                if (seenPageIndex > lastSeenIndex) {
                    saveLastSeenPageIndex(seenPageIndex)
                } else {
                    Completable.complete()
                }
            }
    }

    private fun saveLastSeenPageIndex(index: Int): Completable {
        return preferencesManager.persist(KEY_LAST_WHAT_IS_NEW_PAGE_SEEN_INDEX, index)
    }

    private fun getIndexOfMostRecentPage(): Single<Int> {
        return getOrLoadContentPages()
            .lastElement()
            .map(WhatIsNewPage::index)
            .defaultIfEmpty(-1)
    }

    private fun getIndexOfLastSeenPage(): Single<Int> {
        return preferencesManager.restoreOrDefault(KEY_LAST_WHAT_IS_NEW_PAGE_SEEN_INDEX, -1)
    }

    /**
     * Contains the intro page, all content pages and the outro page.
     */
    fun getAllPages(): Observable<WhatIsNewPage> {
        return Observable.merge(
            Observable.just(getIntroPage()),
            getOrLoadContentPages(),
            Observable.just(getOutroPage())
        )
    }

    /**
     * Contains the intro page, unseen content pages and the outro page.
     */
    fun getUnseenPages(): Observable<WhatIsNewPage> {
        val unseenContentPages = getIndexOfLastSeenPage()
            .flatMapObservable { lastSeenIndex ->
                getOrLoadContentPages()
                    .filter { it.index > lastSeenIndex }
            }

        return Observable.merge(
            Observable.just(getIntroPage()),
            unseenContentPages,
            Observable.just(getOutroPage())
        )
    }

    private fun getIntroPage(): WhatIsNewPage {
        return WhatIsNewPage(
            image = R.drawable.g_star,
            heading = context.getString(R.string.what_is_new_intro_heading),
            description = context.getString(R.string.what_is_new_intro_description)
        )
    }

    private fun getOutroPage(): WhatIsNewPage {
        return WhatIsNewPage(
            image = R.drawable.g_flag,
            heading = context.getString(R.string.what_is_new_outro_heading),
            description = context.getString(R.string.what_is_new_outro_description)
        )
    }

    private fun getOrLoadContentPages(): Observable<WhatIsNewPage> {
        return Observable.defer {
            if (cachedContentPages == null) {
                cachedContentPages = loadContentPages()
                    .sorted { first, second -> first.index.compareTo(second.index) }
                    .cache()
            }
            cachedContentPages!!
        }
    }

    private fun loadContentPages(): Observable<WhatIsNewPage> {
        return Observable.defer {
            val indices = context.resources.obtainTypedArray(R.array.what_is_new_pages_indices)
            val indicesArray = IntArray(indices.length())
            for (i in indicesArray.indices) {
                indicesArray[i] = indices.getInt(i, -1)
            }
            indices.recycle()

            val images = context.resources.obtainTypedArray(R.array.what_is_new_pages_images)
            val imageResIdArray = IntArray(images.length())
            for (i in imageResIdArray.indices) {
                imageResIdArray[i] = images.getResourceId(i, 0)
            }
            images.recycle()

            val headings = context.resources.getStringArray(R.array.what_is_new_pages_headings).toList()
            val descriptions = context.resources.getStringArray(R.array.what_is_new_pages_descriptions).toList()
            val pages = mutableListOf<WhatIsNewPage>()

            for (i in indicesArray.indices) {
                pages.add(
                    WhatIsNewPage(
                        index = indicesArray[i],
                        image = imageResIdArray[i],
                        heading = headings[i],
                        description = descriptions[i]
                    )
                )
            }

            Observable.fromIterable(pages)
        }
    }

    enum class PageGroup(val value: PageGroupContent) {
        LUCA_2_0(
            PageGroupContent(
                titleRes = R.string.what_is_new_series_luca_2_0,
                startIndex = 0,
                size = 6
            )
        ),
        LUCA_2_2(
            PageGroupContent(
                titleRes = R.string.what_is_new_series_check_in,
                startIndex = 6,
                size = 2
            )
        ),
        LUCA_2_4(
            PageGroupContent(
                titleRes = R.string.what_is_new_series_notification_tab,
                startIndex = 8,
                size = 1
            )
        )
    }

    data class PageGroupContent(
        val titleRes: Int,
        val startIndex: Int,
        val size: Int
    )

    /*
        Messages
     */

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun showNotificationsForUnseenMessagesIfRequired(): Completable {
        return registrationManager.hasCompletedRegistration()
            .filter { hasCompletedRegistration -> isFirstSessionAfterAppUpdate == true && hasCompletedRegistration }
            .flatMapObservable { getAllMessages() }
            .filter { message -> !message.notified && !message.seen && message.enabled }
            .flatMapCompletable { showNotificationForMessage(it) }
    }

    fun showNotificationForMessage(message: WhatIsNewMessage): Completable {
        return notificationManager.initialize(application)
            .andThen(notificationManager.showNewsMessageNotification(message))
            .andThen(markMessageAsNotified(message.id!!))
    }

    fun updateMessage(id: String, applyBlock: WhatIsNewMessage.() -> WhatIsNewMessage): Completable {
        return restoreOrCreateMessage(id)
            .map { applyBlock(it) }
            .flatMapCompletable {
                preferencesManager.persist(id, it)
                    .doOnComplete {
                        cachedMessages = null
                        messageUpdatesSubject.onNext(it)
                        Timber.d("Message updated: $it")
                    }
            }
    }

    fun markMessageAsNotified(id: String): Completable {
        return updateMessage(id) { copy(notified = true) }
    }

    fun markMessageAsSeen(id: String): Completable {
        return updateMessage(id) { copy(seen = true) }
    }

    fun getMessage(id: String): Single<WhatIsNewMessage> {
        return getAllMessages()
            .filter { it.id == id }
            .firstOrError()
    }

    fun getAllMessages(): Observable<WhatIsNewMessage> {
        return Observable.defer {
            if (cachedMessages == null) {
                cachedMessages = Single.mergeArray(
                    restoreOrCreatePostalCodeMessage(),
                    restoreOrCreateLucaConnectMessage()
                ).toObservable().cache()
            }
            cachedMessages!!
        }
    }

    fun getMessageUpdates(): Observable<WhatIsNewMessage> {
        return messageUpdatesSubject
    }

    fun getMessageUpdates(id: String): Observable<WhatIsNewMessage> {
        return getMessageUpdates().filter { it.id == id }
    }

    private fun restoreOrCreateMessage(id: String, enabledByDefault: Boolean = true): Single<WhatIsNewMessage> {
        return preferencesManager.restoreOrDefault(id, WhatIsNewMessage(enabled = enabledByDefault))
            .map { it.copy(id = id) }
    }

    private fun restoreOrCreatePostalCodeMessage(): Single<WhatIsNewMessage> {
        return restoreOrCreateMessage(ID_POSTAL_CODE_MESSAGE)
            .map {
                it.copy(
                    title = context.getString(R.string.notification_postal_code_matching_title),
                    content = context.getString(R.string.notification_postal_code_matching_description),
                    destination = Uri.parse(context.getString(R.string.deeplink_postal_code))
                )
            }
    }

    private fun restoreOrCreateLucaConnectMessage(): Single<WhatIsNewMessage> {
        return restoreOrCreateMessage(ID_LUCA_CONNECT_MESSAGE, enabledByDefault = false)
            .map {
                it.copy(
                    title = context.getString(R.string.notification_luca_connect_supported_title),
                    content = context.getString(R.string.notification_luca_connect_supported_description),
                    destination = Uri.parse(context.getString(R.string.deeplink_connect))
                )
            }
    }

    /*
        Updates
     */

    /**
     * Required because the last used version number has not been persisted in app versions
     * before 2.4.1, but we still want to detect if the app has been updated from that version.
     */
    private fun migrateLastUsedVersionNumberIfRequired(): Completable {
        return restoreLastUsedVersionNumberIfAvailable()
            .isEmpty()
            .filter { it } // no version number available yet
            .flatMapSingle { registrationManager.hasCompletedRegistration() }
            .filter { it } // indicates that the app has been used in a previous version
            .flatMapCompletable {
                persistLastUsedVersionNumber(BuildConfig.VERSION_CODE - 1)
                    .doOnComplete { Timber.i("Migrated last used version number") }
            }
    }

    /**
     * Checks if the app has been updated since the last session and updates
     * [isFirstSessionAfterAppUpdate] accordingly.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkAndUpdateLastUsedVersionNumber(): Completable {
        return migrateLastUsedVersionNumberIfRequired()
            .andThen(restoreLastUsedVersionNumberIfAvailable())
            .defaultIfEmpty(BuildConfig.VERSION_CODE)
            .doOnSuccess {
                isFirstSessionAfterAppUpdate = it < BuildConfig.VERSION_CODE
                Timber.d("Is first session after app update: $isFirstSessionAfterAppUpdate")
            }
            .flatMapCompletable { persistLastUsedVersionNumber() }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun restoreLastUsedVersionNumberIfAvailable(): Maybe<Int> {
        return preferencesManager.restoreIfAvailable(KEY_LAST_USED_VERSION_NUMBER, Int::class.java)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun persistLastUsedVersionNumber(lastVersionNumber: Int = BuildConfig.VERSION_CODE): Completable {
        return preferencesManager.persist(KEY_LAST_USED_VERSION_NUMBER, lastVersionNumber)
    }

    companion object {

        private const val KEY_LAST_USED_VERSION_NUMBER = "last_used_version_number"
        private const val KEY_LAST_WHAT_IS_NEW_PAGE_SEEN_INDEX = "key_last_what_is_new_page_seen_index"
        const val ID_POSTAL_CODE_MESSAGE = "news_message_postal_code"
        const val ID_LUCA_CONNECT_MESSAGE = "news_message_luca_connect"
    }
}
