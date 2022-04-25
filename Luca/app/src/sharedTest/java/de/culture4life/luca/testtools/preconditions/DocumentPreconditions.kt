package de.culture4life.luca.testtools.preconditions

import androidx.test.core.app.ApplicationProvider
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.testtools.samples.SampleDocuments

class DocumentPreconditions {

    private val application = ApplicationProvider.getApplicationContext<LucaApplication>()
    private val documentManager by lazy { application.getInitializedManager(application.documentManager).blockingGet() }

    fun givenAddedDocument(document: SampleDocuments) {
        documentManager.parseAndValidateEncodedDocument(document.qrCodeContent)
            .flatMapCompletable(documentManager::addDocument)
            .blockingAwait()
    }
}
