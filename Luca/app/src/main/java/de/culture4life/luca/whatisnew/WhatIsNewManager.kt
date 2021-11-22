package de.culture4life.luca.whatisnew

import android.content.Context
import de.culture4life.luca.Manager
import de.culture4life.luca.R
import de.culture4life.luca.preference.PreferencesManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

class WhatIsNewManager(
    private val preferencesManager: PreferencesManager
) : Manager() {

    private var cachedContentPages: Observable<WhatIsNewPage>? = null

    override fun doInitialize(context: Context): Completable {
        return preferencesManager.initialize(context)
            .andThen(Completable.fromAction { this.context = context })
    }

    fun shouldWhatIsNewBeShown(): Single<Boolean> {
        return Single.zip(
            getIndexOfLastSeenPage(),
            getIndexOfMostRecentPage(),
            { lastSeen, mostRecent -> lastSeen < mostRecent }
        )
    }

    fun disableWhatIsNewScreenForCurrentVersion(): Completable {
        return getIndexOfMostRecentPage()
            .flatMapCompletable(this::saveLastSeenPageIndex)
    }

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
            image = R.drawable.g_rocket,
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

}

private const val KEY_LAST_WHAT_IS_NEW_PAGE_SEEN_INDEX = "key_last_what_is_new_page_seen_index"