package de.culture4life.luca.ui.account

import android.app.Application
import android.content.ActivityNotFoundException
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.checkin.CheckInData
import de.culture4life.luca.document.Document
import de.culture4life.luca.document.provider.ProvidedDocument
import de.culture4life.luca.document.provider.appointment.Appointment
import de.culture4life.luca.document.provider.eudcc.EudccDocument
import de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocument
import de.culture4life.luca.history.CheckOutItem
import de.culture4life.luca.history.HistoryItem
import de.culture4life.luca.history.MeetingEndedItem
import de.culture4life.luca.idnow.LucaIdData
import de.culture4life.luca.meeting.MeetingManager
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.myluca.listitems.TestResultItem
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.getReadableDate
import de.culture4life.luca.util.getReadableTime
import dgca.verifier.app.decoder.model.GreenCertificate
import dgca.verifier.app.decoder.model.RecoveryStatement
import dgca.verifier.app.decoder.model.Test
import dgca.verifier.app.decoder.model.Vaccination
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class AccountViewModel(application: Application) : BaseViewModel(application) {

    private val connectManager = this.application.connectManager
    private val idNowManager = this.application.idNowManager

    val connectEnrollmentSupportedStatus = MutableLiveData<Boolean>()
    val idNowEnrollmentVerifiedStatus = MutableLiveData<Boolean>()
    val documentsAvailableStatus = MutableLiveData<Boolean>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    connectManager.initialize(application),
                    idNowManager.initialize(application)
                )
            ).andThen(updateDocumentsAvailabilityImmediately())
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepConnectEnrollmentSupportedStatusUpdated(),
            keepVerificationStatusUpdated()
        )
    }

    private fun keepConnectEnrollmentSupportedStatusUpdated(): Completable {
        return connectManager.getEnrollmentSupportedStatusAndChanges()
            .flatMapCompletable { updateIfRequired(connectEnrollmentSupportedStatus, it) }
    }

    private fun keepVerificationStatusUpdated(): Completable {
        return idNowManager.isEnrolled()
            .flatMapCompletable { updateIfRequired(idNowEnrollmentVerifiedStatus, it) }
    }

    private fun updateDocumentsAvailabilityImmediately(): Completable {
        return application.documentManager.getOrRestoreDocuments()
            .isEmpty
            .flatMapCompletable { updateIfRequired(documentsAvailableStatus, !it) }
    }

    fun openPostalCodeView() {
        navigationController?.navigate(R.id.action_accountFragment_to_postalCodeFragment)
    }

    fun openLucaConnectView() {
        navigationController?.navigate(R.id.action_accountFragment_to_lucaConnectFragment)
    }

    fun openVoluntaryCheckInView() {
        navigationController?.navigate(R.id.action_accountFragment_to_voluntaryCheckInFragment)
    }

    fun openDirectCheckInView() {
        navigationController?.navigate(R.id.action_accountFragment_to_directCheckInFragment)
    }

    fun openEntryPolicyView() {
        navigationController?.navigate(R.id.action_accountFragment_to_entryPolicyPreferencesFragment)
    }

    fun openDailyKeyView() {
        navigationController?.navigate(R.id.action_accountFragment_to_dailyKeyFragment)
    }

    fun openNewsView() {
        navigationController?.navigate(R.id.action_accountFragment_to_newsFragment)
    }

    fun requestSupportMail() {
        try {
            application.openSupportMailIntent()
        } catch (exception: ActivityNotFoundException) {
            val viewError = createErrorBuilder(exception)
                .withTitle(R.string.menu_support_error_title)
                .withDescription(R.string.menu_support_error_description)
                .removeWhenShown()
                .build()
            addError(viewError)
        }
    }

    /**
     * Delete the account data on backend and clear data locally. Restart the app from scratch when
     * successful, show error dialog when an error occurred.
     */
    fun deleteAccount() {
        modelDisposable.add(
            application.deleteAccount()
                .doOnSubscribe { updateAsSideEffect(isLoading, true) }
                .doFinally { updateAsSideEffect(isLoading, false) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Timber.i("Account deleted")
                    application.restart()
                }) { throwable: Throwable ->
                    Timber.w("Unable to delete account: %s", throwable.toString())
                    val viewError = createErrorBuilder(throwable)
                        .withTitle(R.string.error_request_failed_title)
                        .removeWhenShown()
                        .build()
                    addError(viewError)
                }
        )
    }

    /*
        Data reports
     */

    fun exportTracingDataRequest(uri: Single<Uri>) {
        val content = Single.mergeArray(
            serializeContactData(),
            serializeCheckInDataIfAvailable(),
            serializeMeetingDataIfAvailable()
        )
            .filter { it.isNotEmpty() } // check-in data might have no entries
            .collectInto(StringBuilder()) { builder, content -> builder.appendLineWithDelimiter(content, DATA_REPORT_CONTENT_DELIMITER) }
            .map { content ->
                StringBuilder().appendLine(application.getString(R.string.data_request_tracing_content_prefix))
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(content)
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(application.getString(R.string.data_request_privacy_policy_prefix))
                    .appendLine(application.getString(R.string.data_request_privacy_policy_infix))
                    .appendLine(application.getString(R.string.data_request_privacy_policy_suffix))
                    .replaceMultipleLineBreaks()
            }

        export(uri, content)
    }

    fun exportDocumentsDataRequest(uri: Single<Uri>) {
        val content = serializeDocuments()
            .map { content ->
                StringBuilder().appendLine(application.getString(R.string.data_request_documents_content_prefix))
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(content)
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(application.getString(R.string.data_request_privacy_policy_prefix))
                    .appendLine(application.getString(R.string.data_request_privacy_policy_infix))
                    .appendLine(application.getString(R.string.data_request_privacy_policy_suffix))
                    .replaceMultipleLineBreaks()
            }

        export(uri, content)
    }

    fun exportLucaIdDataRequest(uri: Single<Uri>) {
        val content = serializeLucaId()
            .map { (idData, codeData) ->
                StringBuilder().appendLine(application.getString(R.string.data_request_luca_id_content_prefix))
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(idData)
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(application.getString(R.string.data_request_privacy_policy_prefix))
                    .appendLine(application.getString(R.string.data_request_privacy_policy_infix))
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(codeData)
                    .appendLine(DATA_REPORT_CONTENT_DELIMITER)
                    .appendLine(application.getString(R.string.data_request_privacy_policy_suffix))
                    .replaceMultipleLineBreaks()
            }

        export(uri, content)
    }

    private fun serializeContactData(): Single<String> {
        return application.registrationManager.getRegistrationData()
            .map { registrationData ->
                StringBuilder().appendLine(application.getString(R.string.data_request_contact_data_title))
                    .appendLine(printProperty(R.string.data_request_contact_data_name, registrationData.fullName))
                    .appendLine(printProperty(R.string.data_request_contact_data_address, registrationData.address))
                    .appendLine(printProperty(R.string.data_request_contact_data_phone_number, registrationData.phoneNumber))
                    .appendLine(printProperty(R.string.data_request_contact_data_mail, registrationData.email))
                    .appendLine(printProperty(R.string.data_request_storage_location, R.string.data_request_storage_location_local_and_server))
                    .appendLine(printProperty(R.string.data_request_storage_period, R.string.data_request_storage_period_contact_data))
                    .toString()
            }
    }

    private fun serializeCheckInDataIfAvailable(): Single<String> {
        return application.checkInManager.archivedCheckInData
            .flatMapSingle { checkIn ->
                application.historyManager.items
                    .filter { it.relatedId.equals(checkIn.traceId) }
                    .filter { it.type == HistoryItem.TYPE_CHECK_OUT }
                    .firstElement()
                    .cast(CheckOutItem::class.java)
                    .flatMapSingle { checkOut -> serializeCheckInData(checkIn, checkOut) }
                    .switchIfEmpty(serializeCheckInData(checkIn, null))
            }
            .switchIfEmpty(Observable.just(NO_DATA_FALLBACK))
            .collectInto(StringBuilder()) { builder, checkIn -> builder.appendLineWithDelimiter(checkIn, DATA_REPORT_CONTENT_DELIMITER) }
            .map { checkIns ->
                val builder = StringBuilder()
                if (checkIns.isNotEmpty()) {
                    builder.appendLine(application.getString(R.string.data_request_location_data_title))
                        .appendLine(checkIns)
                }
                builder.toString()
            }
    }

    private fun serializeMeetingDataIfAvailable(): Single<String> {
        return application.historyManager.items
            .filter { it.type == HistoryItem.TYPE_MEETING_STARTED || it.type == HistoryItem.TYPE_MEETING_ENDED }
            .toList()
            .map { meetingItems ->
                val builder = StringBuilder()
                for (item in meetingItems) {
                    if (item.type == HistoryItem.TYPE_MEETING_STARTED) {
                        val endItem = meetingItems.find { it.relatedId == item.relatedId && it is MeetingEndedItem } as MeetingEndedItem?
                        builder.appendLineWithDelimiter(serializeMeetingData(item, endItem).blockingGet(), DATA_REPORT_CONTENT_DELIMITER)
                    }
                }
                builder.toString()
            }
    }

    private fun serializeMeetingData(startItem: HistoryItem, endItem: MeetingEndedItem?): Single<String> {
        return Single.defer {
            val guests = endItem?.guests?.joinToString()
                ?: application.meetingManager.getCurrentMeetingDataIfAvailable()
                    .flatMapObservable {
                        Observable.fromIterable(it.guestData)
                    }
                    .map(MeetingManager::getReadableGuestName)
                    .toList()
                    .map { it.joinToString() }
                    .blockingGet()
            val checkOutTimestamp = endItem?.timestamp ?: TimeUtil.getCurrentMillis()
            val duration = TimeUtil.getReadableTimeDuration(checkOutTimestamp - startItem.timestamp)
            serializeMeeting(startItem.timestamp, duration, guests)
        }
    }

    private fun serializeMeeting(timestamp: Long, duration: String, guests: String): Single<String> {
        return Single.fromCallable {
            val builder = StringBuilder().appendLine(application.getString(R.string.data_request_check_in_private))

            if (guests.isNotEmpty()) {
                builder.appendLine(R.string.data_request_check_in_guests, guests)
            }

            builder.appendLine(R.string.data_request_check_in_time, TimeUtil.getReadableTime(application, timestamp))
                .appendLine(R.string.data_request_check_in_duration, duration)
                .appendLine(R.string.data_request_storage_location, R.string.data_request_storage_location_local)
                .appendLine(R.string.data_request_storage_period, R.string.data_request_storage_period_meeting_data)
                .toString()
        }
    }

    private fun serializeCheckInData(
        checkInData: CheckInData,
        checkOutItem: CheckOutItem?
    ): Single<String> {
        return Single.fromCallable {
            val builder = StringBuilder()
            if (checkInData.isPrivateMeeting) {
                builder.append(serializePrivateCheckIn(checkInData, checkOutItem).blockingGet())
            } else {
                builder.append(serializeLocationCheckIn(checkInData, checkOutItem).blockingGet())
            }

            builder.appendLine(R.string.data_request_check_in_trace_id, checkInData.traceId)
                .appendLine(R.string.data_request_storage_location, R.string.data_request_storage_location_local_and_server)
                .appendLine(R.string.data_request_storage_period, R.string.data_request_storage_period_location_data)

            val accessedData = application.dataAccessManager
                .getPreviouslyAccessedTraceData(checkInData.traceId!!)
                .map {
                    if (TextUtils.isEmpty(it.healthDepartment.name)) {
                        application.getString(R.string.unknown)
                    } else {
                        it.healthDepartment.name
                    }
                }
                .distinct()
                .toList()
                .map { it.joinToString() }
                .onErrorReturnItem(application.getString(R.string.unknown))
                .blockingGet()

            if (!TextUtils.isEmpty(accessedData)) {
                builder.appendLine(R.string.data_request_check_in_data_accessed, accessedData)
            }

            builder.toString()
        }
    }

    private fun serializePrivateCheckIn(checkInData: CheckInData, checkOutItem: CheckOutItem?): Single<String> {
        return Single.fromCallable {
            val checkOutTimestamp = checkOutItem?.timestamp ?: TimeUtil.getCurrentMillis()
            StringBuilder().appendLine(application.getString(R.string.data_request_check_in_private))
                .appendLine(R.string.data_request_check_in_host, checkInData.locationAreaName)
                .appendLine(R.string.data_request_check_in_time, TimeUtil.getReadableTime(application, checkInData.timestamp))
                .appendLine(R.string.data_request_check_in_duration, TimeUtil.getReadableTimeDuration(checkOutTimestamp - checkInData.timestamp))
                .toString()
        }
    }

    private fun serializeLocationCheckIn(checkInData: CheckInData, checkOutItem: CheckOutItem?): Single<String> {
        return Single.fromCallable {
            val checkOutTimestamp = checkOutItem?.timestamp ?: TimeUtil.getCurrentMillis()
            val builder = StringBuilder()
                .appendLine(R.string.data_request_check_in_time, TimeUtil.getReadableTime(application, checkInData.timestamp))
                .appendLine(R.string.data_request_check_in_duration, TimeUtil.getReadableTimeDuration(checkOutTimestamp - checkInData.timestamp))
                .appendLine(R.string.data_request_check_in_name, checkInData.locationDisplayName)

            if (checkOutItem?.children?.isNotEmpty() == true) {
                builder.appendLine(R.string.data_request_check_in_children, checkOutItem.children.joinToString())
            }

            builder.toString()
        }
    }

    private fun serializeDocuments(): Single<String> {
        return application.documentManager.getOrRestoreDocuments()
            .flatMapSingle(this::serializeDocument)
            .switchIfEmpty(Observable.just(NO_DATA_FALLBACK))
            .filter { it.isNotEmpty() }
            .collectInto(StringBuilder()) { builder, document -> builder.appendLineWithDelimiter(document, DATA_REPORT_CONTENT_DELIMITER) }
            .map { documents -> documents.toString() }
    }

    private fun serializeDocument(document: Document): Single<String> {
        return application.documentManager.parseEncodedDocument(document.encodedData)
            .flatMap { document ->
                when (document) {
                    is EudccDocument -> serializeGreenCertificate(document)
                    is OpenTestCheckDocument -> serializeOpenTestCheckDocument(document)
                    is Appointment -> serializeAppointment(document)
                    else -> serializeGenericDocument(document)
                }
            }
            .onErrorReturnItem(NO_DATA_FALLBACK)
            .map { serializedDocument ->
                val builder = StringBuilder()
                if (serializedDocument.isNotEmpty()) {
                    builder.appendLine(serializedDocument)
                        .appendLine(printProperty(R.string.data_request_document_data, document.encodedData))
                        .appendLine(printProperty(R.string.data_request_storage_location, R.string.data_request_storage_location_local))
                        .appendLine(printProperty(R.string.data_request_storage_period, R.string.data_request_storage_period_document_data))
                }
                builder.toString()
            }
    }

    private fun serializeGenericDocument(document: ProvidedDocument): Single<String> {
        return Single.just(document.javaClass.simpleName)
    }

    private fun serializeGreenCertificate(document: EudccDocument): Single<String> {
        return Single.fromCallable {
            val certificate = document.getCertificate()
            val builder = StringBuilder()
            certificate?.let {
                builder.appendLineWithDelimiter(serializeGreenCertificateOwner(certificate).blockingGet(), DATA_REPORT_CONTENT_DELIMITER)
                it.vaccinations?.forEach { vaccination ->
                    builder.appendLine(serializeGreenCertificateVaccination(vaccination).blockingGet())
                }
                it.recoveryStatements?.forEach { recoveryStatement ->
                    builder.appendLine(serializeGreenCertificateRecovery(recoveryStatement).blockingGet())
                }
                it.tests?.forEach { test ->
                    builder.appendLine(serializeGreenCertificateTest(test).blockingGet())
                }
            }
            builder.toString()
        }
    }

    private fun serializeGreenCertificateOwner(certificate: GreenCertificate): Single<String> {
        return Single.fromCallable {
            StringBuilder().appendLine(printProperty(R.string.data_request_document_first_name, certificate.person.givenName))
                .appendLine(printProperty(R.string.data_request_document_last_name, certificate.person.familyName))
                .appendLine(printProperty(R.string.data_request_document_birthdate, certificate.dateOfBirth))
                .toString()
        }
    }

    private fun serializeGreenCertificateVaccination(vaccination: Vaccination): Single<String> {
        return Single.fromCallable {
            StringBuilder().appendLine(printProperty(R.string.data_request_document_type, R.string.data_request_document_type_vaccination))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_tg, vaccination.disease))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_vp, vaccination.vaccine))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_mp, vaccination.medicinalProduct))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_ma, vaccination.manufacturer))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_dn, vaccination.doseNumber.toString()))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_sd, vaccination.totalSeriesOfDoses.toString()))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_dt, vaccination.dateOfVaccination))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_co, vaccination.countryOfVaccination))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_is, vaccination.certificateIssuer))
                .appendLine(printProperty(R.string.data_request_green_cert_vaccination_ci, vaccination.certificateIdentifier))
                .toString()
        }
    }

    private fun serializeGreenCertificateRecovery(recovery: RecoveryStatement): Single<String> {
        return Single.fromCallable {
            StringBuilder().appendLine(printProperty(R.string.data_request_document_type, R.string.data_request_document_type_recovery))
                .appendLine(printProperty(R.string.data_request_green_cert_recovery_tg, recovery.disease))
                .appendLine(printProperty(R.string.data_request_green_cert_recovery_fr, recovery.dateOfFirstPositiveTest))
                .appendLine(printProperty(R.string.data_request_green_cert_recovery_co, recovery.countryOfVaccination))
                .appendLine(printProperty(R.string.data_request_green_cert_recovery_df, recovery.certificateValidFrom))
                .appendLine(printProperty(R.string.data_request_green_cert_recovery_du, recovery.certificateValidUntil))
                .appendLine(printProperty(R.string.data_request_green_cert_recovery_is, recovery.certificateIssuer))
                .appendLine(printProperty(R.string.data_request_green_cert_recovery_ci, recovery.certificateIdentifier))
                .toString()
        }
    }

    private fun serializeGreenCertificateTest(test: Test): Single<String> {
        return Single.fromCallable {
            StringBuilder().appendLine(printProperty(R.string.data_request_document_type, R.string.data_request_document_type_test))
                .appendLine(printProperty(R.string.data_request_green_cert_test_tg, test.disease))
                .appendLine(printProperty(R.string.data_request_green_cert_test_tt, test.typeOfTest))
                .appendLine(printProperty(R.string.data_request_green_cert_test_nm, test.testName))
                .appendLine(printProperty(R.string.data_request_green_cert_test_ma, test.testNameAndManufacturer))
                .appendLine(printProperty(R.string.data_request_green_cert_test_sc, test.dateTimeOfCollection))
                .appendLine(printProperty(R.string.data_request_green_cert_test_dr, test.dateTimeOfTestResult))
                .appendLine(printProperty(R.string.data_request_green_cert_test_tr, test.testResult))
                .appendLine(printProperty(R.string.data_request_green_cert_test_tc, test.testingCentre))
                .appendLine(printProperty(R.string.data_request_green_cert_test_co, test.countryOfVaccination))
                .appendLine(printProperty(R.string.data_request_green_cert_test_is, test.certificateIssuer))
                .appendLine(printProperty(R.string.data_request_green_cert_test_ci, test.certificateIdentifier))
                .toString()
        }
    }

    private fun serializeOpenTestCheckDocument(document: OpenTestCheckDocument): Single<String> {
        return Single.fromCallable {
            StringBuilder().appendLine(serializeDocumentOwner(document.document))
                .appendLine(printProperty(R.string.data_request_document_type, R.string.data_request_document_type_test))
                .appendLine(printProperty(R.string.data_request_test_type, TestResultItem.getReadableTestType(application, document.document)))
                .appendLine(printProperty(R.string.data_request_test_result, TestResultItem.getReadableResult(application, document.document)))
                .appendLine(printProperty(R.string.data_request_test_date, application.getReadableTime(document.document.resultTimestamp)))
                .appendLine(printProperty(R.string.data_request_test_provider, document.document.provider))
                .appendLine(printProperty(R.string.data_request_test_lab, document.document.labName))
                .appendLine(printProperty(R.string.data_request_test_doctor, document.document.labDoctorName))
                .toString()
        }
    }

    private fun serializeAppointment(document: Appointment): Single<String> {
        return Single.fromCallable {
            StringBuilder().appendLine(serializeDocumentOwner(document.document))
                .appendLine(printProperty(R.string.data_request_document_type, R.string.data_request_document_type_appointment))
                .appendLine(printProperty(R.string.data_request_appointment_type, document.type))
                .appendLine(printProperty(R.string.data_request_appointment_date, application.getReadableTime(document.document.testingTimestamp)))
                .appendLine(printProperty(R.string.data_request_appointment_lab, document.lab))
                .appendLine(printProperty(R.string.data_request_appointment_address, document.address))
                .appendLine(printProperty(R.string.data_request_appointment_qr_code, document.qrCode))
                .toString()
        }
    }

    private fun serializeDocumentOwner(document: Document): Single<String> {
        return Single.fromCallable {
            StringBuilder().appendLine(printProperty(R.string.data_request_document_first_name, document.firstName))
                .appendLine(printProperty(R.string.data_request_document_last_name, document.lastName))
                .appendLine(printProperty(R.string.data_request_document_birthdate, application.getReadableDate(document.dateOfBirth)))
                .toString()
        }
    }

    private fun serializeLucaId(): Single<Pair<String, String>> {
        return idNowManager.getLucaIdDataIfAvailable()
            .flatMapSingle { lucaIdData ->
                Single.zip(
                    serializeLucaIdData(lucaIdData),
                    serializeLucaIdCodeData(lucaIdData)
                ) { idData, codeData -> Pair(idData, codeData) }
            }
            .switchIfEmpty(Single.just(Pair(NO_DATA_FALLBACK, NO_DATA_FALLBACK)))
    }

    private fun serializeLucaIdData(lucaIdData: LucaIdData): Single<String> {
        return Single.fromCallable {
            val builder = StringBuilder()
            lucaIdData.encryptedIdData?.let {
                builder.appendLine(serializeLucaIdEncryptedData(it).blockingGet())
            }
            lucaIdData.decryptedIdData?.let {
                builder.appendLineWithDelimiter(serializeLucaIdDecryptedData(it).blockingGet(), DATA_REPORT_CONTENT_DELIMITER)
            }
            builder.appendLineWithDelimiter(serializeLucaIdSignedData(lucaIdData).blockingGet(), DATA_REPORT_CONTENT_DELIMITER)
                .toString()
        }
    }

    private fun serializeLucaIdEncryptedData(encryptedData: LucaIdData.EncryptedIdData): Single<String> {
        return Single.fromCallable {
            StringBuilder()
                .appendLine(application.getString(R.string.data_request_idnow_encrypted_data))
                .appendLine(printProperty(R.string.data_request_idnow_encrypted_data_face, encryptedData.faceJwe))
                .appendLine(printProperty(R.string.data_request_idnow_encrypted_data_identity, encryptedData.identityJwe))
                .appendLine(printProperty(R.string.data_request_idnow_encrypted_data_minimal_dentity, encryptedData.minimalIdentityJwe))
                .toString()
        }
    }

    private fun serializeLucaIdDecryptedData(decryptedData: LucaIdData.DecryptedIdData): Single<String> {
        return Single.fromCallable {
            StringBuilder()
                .appendLine(application.getString(R.string.data_request_idnow_decrypted_data))
                .appendLine(printProperty(R.string.data_request_idnow_decrypted_data_first_name, decryptedData.firstName))
                .appendLine(printProperty(R.string.data_request_idnow_decrypted_data_last_name, decryptedData.lastName))
                .appendLine(printProperty(R.string.data_request_idnow_decrypted_data_birthday_timestamp, decryptedData.birthdayTimestamp.toString()))
                .toString()
        }
    }

    private fun serializeLucaIdSignedData(signedData: LucaIdData): Single<String> {
        return Single.fromCallable {
            StringBuilder()
                .appendLine(application.getString(R.string.data_request_idnow_signed_data))
                .appendLine(printProperty(R.string.data_request_idnow_signed_data_revocation, signedData.revocationCode))
                .appendLine(printProperty(R.string.data_request_idnow_signed_data_enrollment, signedData.enrollmentToken))
                .appendLine(printProperty(R.string.data_request_idnow_signed_data_verification, signedData.verificationStatus.name))
                .toString()
        }
    }

    private fun serializeLucaIdCodeData(lucaIdData: LucaIdData): Single<String> {
        return Single.fromCallable {
            StringBuilder()
                .appendLine(application.getString(R.string.data_request_idnow_code_content))
                .appendLine(printProperty(R.string.data_request_idnow_code_identity, lucaIdData.enrollmentToken))
                .appendLine(printProperty(R.string.data_request_idnow_code_revocation, lucaIdData.revocationCode))
                .toString()
        }
    }

    private fun printProperty(
        @StringRes key: Int,
        @StringRes value: Int
    ): String {
        return printProperty(key, application.getString(value))
    }

    private fun printProperty(
        @StringRes key: Int,
        value: String?
    ): String {
        return "${application.getString(key)}: ${if (TextUtils.isEmpty(value)) application.getString(R.string.unknown) else value}"
    }

    private fun StringBuilder.appendLine(
        @StringRes key: Int,
        @StringRes value: Int
    ): StringBuilder {
        return this.appendLine(printProperty(key, value))
    }

    private fun StringBuilder.appendLine(
        @StringRes key: Int,
        value: String?
    ): StringBuilder {
        return this.appendLine(printProperty(key, value))
    }

    // Append delimiter before new line if it's not the first line
    private fun StringBuilder.appendLineWithDelimiter(
        line: String,
        delimiter: String
    ): StringBuilder {
        return if (this.isEmpty()) this.appendLine(line) else this.appendLine(delimiter).appendLine(line)
    }

    private fun StringBuilder.replaceMultipleLineBreaks(): String {
        return this.replace(Regex("\n\n+"), "\n")
    }

    companion object {
        private const val NO_DATA_FALLBACK = ""
        private const val DATA_REPORT_CONTENT_DELIMITER = "-"
    }
}
