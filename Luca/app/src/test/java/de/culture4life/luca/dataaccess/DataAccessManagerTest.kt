package de.culture4life.luca.dataaccess

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.checkin.CheckInManager
import de.culture4life.luca.children.ChildrenManager
import de.culture4life.luca.crypto.CryptoManager
import de.culture4life.luca.genuinity.GenuinityManager
import de.culture4life.luca.history.HistoryManager
import de.culture4life.luca.location.GeofenceManager
import de.culture4life.luca.location.LocationManager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.NotifyingHealthDepartment
import de.culture4life.luca.notification.LucaNotificationManager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class DataAccessManagerTest : LucaUnitTest() {

    private val preferencesManager = PreferencesManager()
    private val networkManager = NetworkManager()
    private val genuinityManager = GenuinityManager(preferencesManager, networkManager)
    private val cryptoManager = CryptoManager(preferencesManager, networkManager, genuinityManager)
    private val registrationManager = RegistrationManager(preferencesManager, networkManager, cryptoManager)
    private val childrenManager = ChildrenManager(preferencesManager, registrationManager)
    private val historyManager = HistoryManager(preferencesManager, childrenManager)
    private val notificationManager = spy(LucaNotificationManager())
    private val checkInManager = spy(
        CheckInManager(
            preferencesManager,
            networkManager,
            GeofenceManager(),
            LocationManager(),
            historyManager,
            cryptoManager,
            notificationManager,
            genuinityManager
        )
    )
    private val dataAccessManager = spy(
        getInitializedManager(
            DataAccessManager(
                preferencesManager,
                networkManager,
                notificationManager,
                checkInManager,
                historyManager,
                cryptoManager
            )
        )
    )
    private val previouslyAccessedTraceData = AccessedTraceData().apply {
        isNew = true
        traceId = "qiqA2+SpnoioxRMWb7IDsw=="
        hashedTraceId = "HASH_VALUE_FOR_WARNING_LEVEL_1"
        warningLevel = 1
    }

    @Test
    fun update_successful_updatesLastUpdateTimestamp() {
        `when`(dataAccessManager.fetchNotificationConfig()).thenReturn(Single.error(RuntimeException()))
        `when`(dataAccessManager.fetchNewRecentlyAccessedTraceData()).thenReturn(Observable.empty())
        val previousDuration = dataAccessManager.durationSinceLastUpdate.blockingGet()
        dataAccessManager.update()
            .andThen(dataAccessManager.durationSinceLastUpdate)
            .test()
            .assertValue { it < previousDuration }
    }

    @Test
    fun update_unsuccessful_doesNotUpdateLastUpdateTimestamp() {
        `when`(dataAccessManager.fetchNewRecentlyAccessedTraceData()).thenReturn(Observable.error(RuntimeException()))
        val previousDuration = dataAccessManager.durationSinceLastUpdate.blockingGet()
        val duration = dataAccessManager.update().onErrorComplete()
            .andThen(dataAccessManager.durationSinceLastUpdate)
            .blockingGet()
        Assert.assertEquals(duration.toFloat(), previousDuration.toFloat(), 20f)
    }

    @Test
    fun durationSinceLastUpdate_justUpdated_emitsLowDuration() {
        `when`(dataAccessManager.fetchNotificationConfig()).thenReturn(Single.error(RuntimeException()))
        `when`(dataAccessManager.fetchNewRecentlyAccessedTraceData()).thenReturn(Observable.empty())
        dataAccessManager.update()
            .andThen(dataAccessManager.durationSinceLastUpdate)
            .test()
            .assertValue { it < 1000 }
    }

    @Test
    fun durationSinceLastUpdate_neverUpdated_emitsHighDuration() {
        dataAccessManager.durationSinceLastUpdate
            .test()
            .assertValue { it > TimeUtil.getCurrentMillis() - 1000 }
    }

    @Test
    fun nextRecommendedUpdateDelay_justUpdated_emitsUpdateInterval() {
        `when`(dataAccessManager.durationSinceLastUpdate).thenReturn(Single.just(0L))
        dataAccessManager.nextRecommendedUpdateDelay
            .test()
            .assertValue(DataAccessManager.UPDATE_INTERVAL)
    }

    @Test
    fun nextRecommendedUpdateDelay_neverUpdated_emitsLowDelay() {
        `when`(dataAccessManager.durationSinceLastUpdate).thenReturn(Single.just(TimeUtil.getCurrentMillis()))
        dataAccessManager.nextRecommendedUpdateDelay
            .test()
            .assertValue(0L)
    }

    @Test
    @Throws(InterruptedException::class)
    fun processNewRecentlyAccessedTraceData_dataAvailable_performsProcessing() {
        val newAccessedTraceData = AccessedTraceData().apply {
            hashedTraceId = "LLJMzA/HqlS77qkpUGNJrA=="
            healthDepartment = createDummyHealthDepartment()
        }
        val process = dataAccessManager.processNewRecentlyAccessedTraceData(listOf(newAccessedTraceData))
            .test()
        rxSchedulersRule.testScheduler.triggerActions()
        process.assertComplete()
        verify(dataAccessManager, times(1))
            .addToAccessedData(ArgumentMatchers.any())
        verify(dataAccessManager, times(1)).addHistoryItems(ArgumentMatchers.any())
        verify(dataAccessManager, times(1))
            .notifyUserAboutDataAccess(ArgumentMatchers.any())
    }

    @Test
    @Throws(InterruptedException::class)
    fun processNewRecentlyAccessedTraceData_noDataAvailable_performsNothing() {
        `when`(dataAccessManager.fetchNotificationConfig()).thenReturn(Single.error(RuntimeException()))
        dataAccessManager.processNewRecentlyAccessedTraceData(emptyList())
            .test()
            .await()
            .assertComplete()
        verify(dataAccessManager, never()).addToAccessedData(ArgumentMatchers.any())
        verify(dataAccessManager, never()).addHistoryItems(ArgumentMatchers.any())
        verify(dataAccessManager, never())
            .notifyUserAboutDataAccess(ArgumentMatchers.any())
    }

    @Test
    fun notifyUserAboutDataAccess_validData_showsNotification() {
        `when`(dataAccessManager.fetchNotificationConfig()).thenReturn(Single.error(RuntimeException()))
        val newAccessedTraceData = AccessedTraceData().apply {
            hashedTraceId = "LLJMzA/HqlS77qkpUGNJrA=="
        }
        dataAccessManager.notifyUserAboutDataAccess(listOf(newAccessedTraceData))
            .test()
            .assertComplete()
        verify(notificationManager, times(1))
            .showNotification(
                ArgumentMatchers.eq(LucaNotificationManager.NOTIFICATION_ID_DATA_ACCESS),
                ArgumentMatchers.any()
            )
    }

    @Test
    fun recentTraceIds_checkInsAvailable_emitsTraceIdsFromCheckIns() {
        val traceId = "9bZZ5Ak465V60PXv92aMFA=="
        doReturn(Observable.just(traceId))
            .`when`(checkInManager)
            .archivedTraceIds
        dataAccessManager.recentTraceIds
            .test()
            .assertValues(traceId)
            .assertComplete()
    }

    @Test
    fun fetchRecentlyAccessedTraceData_noRecentTraceIds_completesEmpty() {
        `when`(dataAccessManager.fetchHealthDepartments()).thenReturn(Observable.just(createDummyHealthDepartment()))
        `when`(dataAccessManager.fetchUnprocessedChunks()).thenReturn(Observable.just(createDummyChunk()))
        doReturn(Observable.empty<Any>()).`when`(dataAccessManager).recentTraceIds
        dataAccessManager.fetchRecentlyAccessedTraceData()
            .test()
            .assertNoValues()
            .assertComplete()
    }

    @Test
    fun fetchRecentlyAccessedTraceData_noRecentAccessedHashedTraceIds_completesEmpty() {
        `when`(dataAccessManager.fetchUnprocessedChunks()).thenReturn(Observable.empty())
        `when`(dataAccessManager.recentTraceIds).thenReturn(Observable.just("hCvt6FNlhomxbBmL50PYDw=="))
        dataAccessManager.fetchRecentlyAccessedTraceData()
            .test()
            .assertNoValues()
            .assertComplete()
    }

    @Test
    fun fetchRecentlyAccessedTraceData_noDataAccessed_completesEmpty() {
        val healthDepartment = createDummyHealthDepartment()
        `when`(dataAccessManager.fetchHealthDepartments()).thenReturn(Observable.just(healthDepartment))
        val chunk = createDummyChunk()
        chunk.hashedTraceIds.add(
            dataAccessManager.getHashedTraceId(
                healthDepartment.id, 1, "99FmQcylJT5e/cyHOjT6Hw==", chunk.hashLength
            ).blockingGet()
        )
        `when`(dataAccessManager.fetchUnprocessedChunks()).thenReturn(Observable.just(chunk))
        `when`(dataAccessManager.recentTraceIds).thenReturn(Observable.just("hCvt6FNlhomxbBmL50PYDw=="))
        dataAccessManager.fetchRecentlyAccessedTraceData()
            .test()
            .assertNoValues()
            .assertComplete()
    }

    @Test
    fun fetchRecentlyAccessedTraceData_someDataAccessed_emitsAccessedData() {
        val healthDepartment = createDummyHealthDepartment()
        `when`(dataAccessManager.fetchHealthDepartments()).thenReturn(Observable.just(healthDepartment))
        val chunk = createDummyChunk()
        chunk.hashedTraceIds.add(
            dataAccessManager.getHashedTraceId(
                healthDepartment.id, 1, "9bZZ5Ak465V60PXv92aMFA==", chunk.hashLength
            ).blockingGet()
        )
        chunk.hashedTraceIds.add(
            dataAccessManager.getHashedTraceId(
                healthDepartment.id, 1, "99FmQcylJT5e/cyHOjT6Hw==", chunk.hashLength
            ).blockingGet()
        )
        `when`(dataAccessManager.fetchUnprocessedChunks()).thenReturn(Observable.just(chunk))
        `when`(dataAccessManager.recentTraceIds).thenReturn(Observable.just("9bZZ5Ak465V60PXv92aMFA==", "hCvt6FNlhomxbBmL50PYDw=="))
        dataAccessManager.fetchRecentlyAccessedTraceData()
            .map { it.traceId }
            .test()
            .assertValues("9bZZ5Ak465V60PXv92aMFA==")
            .assertComplete()
    }

    @Test
    fun fetchNewRecentlyAccessedTraceData_someNewDataAccessed_emitsNewAccessedData() {
        val newAccessedTraceData = AccessedTraceData().apply {
            traceId = "LLJMzA/HqlS77qkpUGNJrA=="
            hashedTraceId = "SOME_HASH_VALUE"
        }
        `when`(dataAccessManager.fetchRecentlyAccessedTraceData())
            .thenReturn(Observable.just(previouslyAccessedTraceData, newAccessedTraceData))
        `when`(dataAccessManager.previouslyAccessedTraceData).thenReturn(Observable.just(previouslyAccessedTraceData))
        dataAccessManager.fetchNewRecentlyAccessedTraceData()
            .test()
            .assertValues(newAccessedTraceData)
            .assertComplete()
    }

    @Test
    fun fetchNewRecentlyAccessedTraceData_newWarningLevelForSameTraceId_emitsNewAccessedData() {
        val newAccessedTraceData = AccessedTraceData().apply {
            traceId = "qiqA2+SpnoioxRMWb7IDsw=="
            hashedTraceId = "HASH_VALUE_FOR_WARNING_LEVEL_2"
            warningLevel = 2
        }
        `when`(dataAccessManager.fetchRecentlyAccessedTraceData())
            .thenReturn(Observable.just(previouslyAccessedTraceData, newAccessedTraceData))
        `when`(dataAccessManager.previouslyAccessedTraceData).thenReturn(Observable.just(previouslyAccessedTraceData))
        dataAccessManager.fetchNewRecentlyAccessedTraceData()
            .test()
            .assertValues(newAccessedTraceData)
            .assertComplete()
    }

    @Test
    fun previouslyAccessedTraceData_noDataPreviouslyAccessed_completesEmpty() {
        dataAccessManager.previouslyAccessedTraceData
            .test()
            .assertNoValues()
            .assertComplete()
    }

    @Test
    fun previouslyAccessedTraceData_someDataPreviouslyAccessed_emitsPreviouslyAccessedData() {
        dataAccessManager.addToAccessedData(listOf(previouslyAccessedTraceData))
            .andThen(dataAccessManager.previouslyAccessedTraceData)
            .map { it.traceId }
            .test()
            .assertValues(previouslyAccessedTraceData.traceId)
            .assertComplete()
    }

    @Test
    fun accessedTraceDataNotYetInformedAbout_noDataAvailable_completesEmpty() {
        dataAccessManager.addToAccessedData(listOf(previouslyAccessedTraceData))
            .andThen(dataAccessManager.markAllAccessedTraceDataAsInformedAbout())
            .andThen(dataAccessManager.accessedTraceDataNotYetInformedAbout)
            .test()
            .assertNoValues()
            .assertComplete()
    }

    @Test
    fun accessedTraceDataNotYetInformedAbout_someDataAvailable_emitsData() {
        previouslyAccessedTraceData.accessTimestamp = TimeUtil.getCurrentMillis() - 1000
        val newAccessedTraceData = AccessedTraceData().apply {
            traceId = "LLJMzA/HqlS77qkpUGNJrA=="
            accessTimestamp = TimeUtil.getCurrentMillis() + 1000
        }
        dataAccessManager.addToAccessedData(listOf(previouslyAccessedTraceData))
            .andThen(dataAccessManager.markAllAccessedTraceDataAsInformedAbout())
            .andThen(dataAccessManager.addToAccessedData(listOf(newAccessedTraceData)))
            .andThen(dataAccessManager.accessedTraceDataNotYetInformedAbout)
            .test()
            .assertValues(newAccessedTraceData)
            .assertComplete()
    }

    @Test
    fun hashedTraceId_validTraceId_emitsExpectedHash() {
        dataAccessManager.getHashedTraceId(
            "8fa43091-261a-45f0-a893-548fc1271025",
            1,
            "9bZZ5Ak465V60PXv92aMFA==",
            16
        ).test().assertValue("F2nfDmOZi7Zygyxivv4A2g==")
    }

    @Test
    fun hashedTraceId_validTraceId_emitsExpectedHash2() {
        dataAccessManager.getHashedTraceId(
            "8fa43091-261a-45f0-a893-548fc1271025",
            1,
            "99FmQcylJT5e/cyHOjT6Hw==",
            16
        ).test().assertValue("YvYkpM4yQ+qM2RpI+C8XbQ==")
    }

    @Test
    fun hashedTraceId_distinctTraceIds_emitsDistinctHashes() {
        val firstHash = dataAccessManager.getHashedTraceId(
            "8fa43091-261a-45f0-a893-548fc1271025",
            1,
            "9bZZ5Ak465V60PXv92aMFA==",
            16
        )
        val secondHash = dataAccessManager.getHashedTraceId(
            "8fa43091-261a-45f0-a893-548fc1271025",
            1,
            "99FmQcylJT5e/cyHOjT6Hw==",
            16
        )
        Single.zip(firstHash, secondHash, { a, b -> a == b })
            .test()
            .assertValue(false)
    }

    @Test
    fun hashedTraceId_distinctHealthDepartments_emitsDistinctHashes() {
        val firstHash = dataAccessManager.getHashedTraceId(
            "8fa43091-261a-45f0-a893-548fc1271025",
            1,
            "9bZZ5Ak465V60PXv92aMFA==",
            16
        )
        val secondHash = dataAccessManager.getHashedTraceId(
            "de4c27f1-2bda-4d50-90cf-7489207de45c",
            1,
            "9bZZ5Ak465V60PXv92aMFA==",
            16
        )
        Single.zip(firstHash, secondHash, { a, b -> a == b })
            .test()
            .assertValue(false)
    }

    @Test
    fun hashedTraceId_distinctWarningLevels_emitsDistinctHashes() {
        val firstHash = dataAccessManager.getHashedTraceId(
            "8fa43091-261a-45f0-a893-548fc1271025",
            1,
            "9bZZ5Ak465V60PXv92aMFA==",
            16
        )
        val secondHash = dataAccessManager.getHashedTraceId(
            "8fa43091-261a-45f0-a893-548fc1271025",
            2,
            "9bZZ5Ak465V60PXv92aMFA==",
            16
        )
        Single.zip(firstHash, secondHash, { a, b -> a == b })
            .test()
            .assertValue(false)
    }

    @Test
    fun updateIfNecessary_withCheckIns_callsUpdate() {
        `when`(dataAccessManager.fetchNotificationConfig()).thenReturn(Single.error(RuntimeException()))
        `when`(dataAccessManager.fetchRecentlyAccessedTraceData()).thenReturn(Observable.empty())
        `when`(checkInManager.archivedTraceIds).thenReturn(Observable.just("anything"))
        dataAccessManager.updateIfNecessary().blockingAwait()
        verify(dataAccessManager, times(1)).update()
    }

    @Test
    fun updateIfNecessary_withoutCheckIns_doesNotCallUpdate() {
        `when`(checkInManager.archivedTraceIds).thenReturn(Observable.empty())
        dataAccessManager.updateIfNecessary().blockingAwait()
        verify(dataAccessManager, never()).update()
    }

    private fun createDummyHealthDepartment(): NotifyingHealthDepartment {
        return NotifyingHealthDepartment(
            "8fa43091-261a-45f0-a893-548fc1271025",
            "Dummy Department",
            null,
            null
        )
    }

    private fun createDummyChunk(): NotificationDataChunk {
        return NotificationDataChunk(
            1,
            0,
            16,
            TimeUtil.getCurrentMillis() - TimeUnit.HOURS.toMillis(1),
            "3cYZ5Ak465V80PXv93aMFB==",
            ArrayList()
        )
    }

    @Test
    fun isNew_afterAdding_isTrue() {
        dataAccessManager.addToAccessedData(listOf(previouslyAccessedTraceData))
            .andThen(dataAccessManager.isNewNotification(previouslyAccessedTraceData.traceId))
            .test().assertValue(true)
    }

    @Test
    fun isNew_afterMarkingAsNotNew_isFalse() {
        dataAccessManager.addToAccessedData(listOf(previouslyAccessedTraceData))
            .andThen(dataAccessManager.markAsNotNew(previouslyAccessedTraceData.traceId, previouslyAccessedTraceData.warningLevel))
            .andThen(dataAccessManager.isNewNotification(previouslyAccessedTraceData.traceId))
            .test().assertValue(false)
    }

    @Test
    fun hasNewNotifications_initially_isFalse() {
        dataAccessManager.hasNewNotifications()
            .test().assertValue(false)
    }

    @Test
    fun hasNewNotifications_afterAddingOne_isTrue() {
        dataAccessManager.addToAccessedData(listOf(previouslyAccessedTraceData))
            .andThen(dataAccessManager.hasNewNotifications())
            .test().assertValue(true)
    }

}