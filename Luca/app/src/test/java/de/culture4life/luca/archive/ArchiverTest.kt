package de.culture4life.luca.archive

import com.google.gson.annotations.Expose
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Completable
import org.junit.After
import org.junit.Test
import java.util.concurrent.TimeUnit

class ArchiverTest : LucaUnitTest() {

    var preferencesManager: PreferencesManager = getInitializedManager(PreferencesManager())
    private val key = "my_key"
    private var archiver = Archiver(preferencesManager, key, ArchivedTestData::class.java) { it.timestamp }

    @After
    fun cleanUp() {
        preferencesManager.deleteAll().blockingAwait()
    }

    @Test
    fun addData_noOverridingConditions_archivesData() {
        val testData = TestTimeData()
        archiver.addData(testData)
            .andThen(preferencesManager.restore(key, ArchivedTestData::class.java))
            .map { it.getData().first() }
            .test()
            .assertValue(testData)
    }

    @Test
    fun addData_overridingConditions_archivesNewData() {
        val testData1 = TestTimeData()
        val testData2 = TestTimeData(testFlag = true)
        archiver.addData(testData1)
            .andThen(archiver.addData(testData2) { it.id == testData2.id })
            .andThen(archiver.getData())
            .firstOrError()
            .test()
            .assertValue(testData2)
    }

    @Test
    fun getData_hasArchivedData_restoresData() {
        val data = TestTimeData()
        val archiveData = ArchivedTestData(arrayListOf(data))
        preferencesManager.persist(key, archiveData)
            .andThen(archiver.getData())
            .firstOrError()
            .test()
            .assertValue(data)
    }

    @Test
    fun getData_hasArchivedData_restoresAllData() {
        val data1 = TestTimeData(TimeUtil.getCurrentMillis())
        val data2 = TestTimeData()
        archiver.addData(data1)
            .andThen(archiver.addData(data2))
            .andThen(archiver.getData())
            .test()
            .assertValueCount(2)
            .assertValueAt(1, data1)
            .assertValueAt(0, data2)
    }

    @Test
    fun deleteDataOlderThan_containsOldData_removesOldData() {
        val dataToBeKept = TestTimeData(TimeUtil.getCurrentMillis())
        val dataToBeDeleted = TestTimeData()
        archiver.addData(arrayListOf(dataToBeKept, dataToBeDeleted))
            .andThen(archiver.deleteDataOlderThan(TimeUnit.MINUTES.toMillis(1)))
            .andThen(archiver.getData())
            .test()
            .assertValueCount(1)
            .assertValue(dataToBeKept)
    }

    @Test
    fun deleteDataAddedBefore_containsOldData_removesOldData() {
        val deletionTimestamp = TimeUtil.getCurrentMillis() - TimeUnit.HOURS.toMillis(1)
        val dataToBeKept1 = TestTimeData(TimeUtil.getCurrentMillis())
        val dataToBeKept2 = TestTimeData(deletionTimestamp)
        val dataToBeDeleted = TestTimeData()
        archiver.addData(arrayListOf(dataToBeKept1, dataToBeKept2, dataToBeDeleted))
            .andThen(archiver.deleteDataAddedBefore(deletionTimestamp))
            .andThen(archiver.getData())
            .test()
            .assertValueCount(2)
            .assertValueAt(1, dataToBeKept1)
            .assertValueAt(0, dataToBeKept2)
    }

    @Test
    fun deleteData_containsOldData_removesOldData() {
        val dataToBeDeleted = TestTimeData(TimeUtil.getCurrentMillis(), testFlag = true)
        val dataToBeKept = TestTimeData()
        archiver.addData(arrayListOf(dataToBeDeleted, dataToBeKept))
            .andThen(archiver.deleteData { it.testFlag })
            .andThen(archiver.getData())
            .test()
            .assertValueCount(1)
            .assertValue(dataToBeKept)
    }

    @Test
    fun addData_cachesData_returnsCachedData() {
        val data = TestTimeData()
        archiver.addData(data)
            .andThen(injectUnCachedData())
            .andThen(archiver.getData())
            .test()
            .assertValue(data)
    }

    @Test
    fun getData_cachesData_returnsCachedData() {
        archiver.getData()
            .ignoreElements()
            .andThen(injectUnCachedData())
            .andThen(archiver.getData())
            .test()
            .assertValueCount(0)
    }

    @Test
    fun deleteData_clearsCache_returnsActualData() {
        val dataToBeDeleted = TestTimeData(testFlag = true)
        val dataToBeKept = TestTimeData()
        archiver.addData(dataToBeDeleted)
            .andThen(archiver.addData(dataToBeKept))
            .andThen(archiver.deleteData { it.testFlag })
            .andThen(injectUnCachedData(TimeUtil.getCurrentMillis()))
            .andThen(archiver.getData())
            .test()
            .assertValueAt(0, dataToBeKept)
            .assertValueAt(1) { it.id == UN_CACHED_ID }
    }

    @Test
    fun clearCachedData_hasCachedData_clearsCachedData() {
        archiver.getData()
            .ignoreElements()
            .andThen(Completable.fromAction { archiver.clearCachedData() })
            .andThen(injectUnCachedData())
            .andThen(archiver.getData())
            .test()
            .assertValueCount(1)
    }

    private fun injectUnCachedData(timestamp: Long = 0): Completable {
        val unCachedData = TestTimeData(timestamp = timestamp, id = UN_CACHED_ID)
        return preferencesManager.restoreOrDefault(key, ArchivedTestData())
            .map {
                it.setData(it.getData() + unCachedData)
                return@map it
            }
            .flatMapCompletable { preferencesManager.persist(key, it) }
    }

    data class TestTimeData(
        @Expose
        var timestamp: Long = 0,

        @Expose
        var id: Int = 0,

        @Expose
        var testFlag: Boolean = false
    )

    class ArchivedTestData(
        @Expose
        private var dataList: List<TestTimeData> = ArrayList()
    ) : ArchivedData<TestTimeData> {

        override fun getData(): List<TestTimeData> {
            return dataList
        }

        override fun setData(data: List<TestTimeData>) {
            dataList = data
        }

        override fun toString(): String {
            return "ArchivedTestData{" +
                "data=" + dataList +
                '}'
        }
    }

    companion object {
        private const val UN_CACHED_ID: Int = 13
    }
}
