package de.culture4life.luca.ui.lucaconnect.children

import android.app.Application
import de.culture4life.luca.R
import de.culture4life.luca.notification.LucaNotificationManager
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel
import io.reactivex.rxjava3.core.Completable

class LucaConnectConsentViewModel(app: Application) : BaseFlowChildViewModel(app) {

    private val notificationManager = application.notificationManager
    private var notificationsDisabledError: ViewError? = null

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(notificationManager.initialize(application))
    }

    fun onActionButtonClicked() {
        (sharedViewModel as LucaConnectBottomSheetViewModel).onEnrollmentRequested()
        sharedViewModel?.navigateToNext()
    }

    fun checkIfNotificationsAreEnabled() {
        removeError(notificationsDisabledError)
        if (!notificationManager.isNotificationChannelEnabled(LucaNotificationManager.NOTIFICATION_CHANNEL_ID_CONNECT)) {
            notificationsDisabledError = ViewError.Builder(application)
                .withTitle(R.string.notification_setting_activation_title)
                .withDescription(R.string.notification_setting_activation_description)
                .withResolveLabel(R.string.action_settings)
                .withResolveAction(
                    Completable.fromAction {
                        notificationManager.openNotificationSettings(LucaNotificationManager.NOTIFICATION_CHANNEL_ID_CONNECT)
                    }
                )
                .build()
            addError(notificationsDisabledError)
        }
    }
}
