package de.culture4life.luca.testtools.pages


import android.view.View
import de.culture4life.luca.R
import de.culture4life.luca.ui.myluca.MyLucaListItem
import io.github.kakaocup.kakao.recycler.KRecyclerItem
import io.github.kakaocup.kakao.recycler.KRecyclerView
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView
import org.hamcrest.Matcher

class MyLucaPage {


    val title = KTextView { withId(R.id.emptyTitleTextView) }
    val addDocumentButton = KButton { withId(R.id.primaryActionButton) }
    val addCertificateFlow = AddCertificateFlowPage()
    val documentList = KRecyclerView(
        { withId(R.id.myLucaRecyclerView) },
        { itemType(MyLucaPage::DocumentItem) }
    )

    class DocumentItem(parent: Matcher<View>) : KRecyclerItem<MyLucaListItem>(parent) {
        val title = KTextView(parent) { withId(R.id.itemTitleTextView) }
    }
}