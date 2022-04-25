package de.culture4life.luca.ui.history

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.dataaccess.AccessedTraceData
import de.culture4life.luca.dataaccess.DataAccessManager
import de.culture4life.luca.history.CheckOutItem
import io.reactivex.rxjava3.core.Observable
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class HistoryViewModelTest : LucaUnitTest() {

    private val checkOutItem = mock<CheckOutItem> {
        on { timestamp } doReturn 0
        on { displayName } doReturn ""
        on { relatedId } doReturn ""
    }

    private val dataAccessManager = mock<DataAccessManager> {
        on { getPreviouslyAccessedTraceData(any()) } doReturn Observable.empty()
    }

    private val items = listOf(
        HistoryListItem.CheckOutListItem(application, checkOutItem, dataAccessManager),
        HistoryListItem.CheckOutListItem(application, checkOutItem, dataAccessManager)
            .apply {
                accessedTraceData = listOf(AccessedTraceData().apply { warningLevel = 1 })
            }
    )

    @Test
    fun filterHistoryListItems_forWarningLevel_filtersCorrectly() {
        Assert.assertEquals(1, HistoryViewModel.filterHistoryListItems(items, 1).size)
        Assert.assertEquals(0, HistoryViewModel.filterHistoryListItems(items, 3).size)
        Assert.assertEquals(2, HistoryViewModel.filterHistoryListItems(items, -1).size)
    }
}
