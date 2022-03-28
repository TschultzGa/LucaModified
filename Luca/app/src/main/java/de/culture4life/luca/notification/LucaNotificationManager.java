package de.culture4life.luca.notification;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Objects;
import java.util.Random;

import de.culture4life.luca.Manager;
import de.culture4life.luca.R;
import de.culture4life.luca.connect.ConnectMessage;
import de.culture4life.luca.service.LucaService;
import de.culture4life.luca.ui.MainActivity;
import de.culture4life.luca.whatisnew.WhatIsNewMessage;
import io.reactivex.rxjava3.core.Completable;
import timber.log.Timber;

public class LucaNotificationManager extends Manager {

    public static final int NOTIFICATION_ID_STATUS = 1;
    public static final int NOTIFICATION_ID_EVENT = 3;
    public static final int NOTIFICATION_ID_CHECKOUT_REMINDER = 4;
    public static final int NOTIFICATION_ID_CHECKOUT_TRIGGERED = 5;
    public static final int NOTIFICATION_ID_NEWS_MESSAGE = 1000; // incremented by news message ID
    public static final int NOTIFICATION_ID_DATA_ACCESS = 2000; // incremented by warning level
    public static final int NOTIFICATION_ID_CONNECT_MESSAGE = 3000; // incremented by connect message ID
    private static final String NOTIFICATION_CHANNEL_ID_PREFIX = "channel_";
    private static final String NOTIFICATION_CHANNEL_ID_STATUS = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_STATUS;
    private static final String NOTIFICATION_CHANNEL_ID_DATA_ACCESS = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_DATA_ACCESS;
    public static final String NOTIFICATION_CHANNEL_ID_CONNECT = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_CONNECT_MESSAGE;
    public static final String NOTIFICATION_CHANNEL_ID_EVENT = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_EVENT;
    private static final String NOTIFICATION_CHANNEL_ID_CHECKOUT_REMINDER = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_CHECKOUT_REMINDER;

    private static final String KEY_BUNDLE = "notification_bundle";
    private static final String KEY_ACTION = "notification_action";
    private static final String KEY_DEEPLINK = "notification_deeplink";

    public static final int ACTION_NAVIGATE = 1;
    public static final int ACTION_STOP = 2;
    public static final int ACTION_CHECKOUT = 3;
    public static final int ACTION_END_MEETING = 4;

    private static final long VIBRATION_DURATION = 200;

    private NotificationManager notificationManager;

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.fromAction(() -> {
            this.notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannels();
            }
        });
    }

    /**
     * Will show the specified notification.
     */
    public Completable showNotification(int notificationId, @NonNull Notification notification) {
        return Completable.fromAction(() -> notificationManager.notify(notificationId, notification));
    }

    /**
     * Will hide the notification with the specified ID.
     */
    public Completable hideNotification(int notificationId) {
        return Completable.fromAction(() -> notificationManager.cancel(notificationId));
    }

    /**
     * Will show the specified notification until the subscription gets disposed.
     */
    public Completable showNotificationUntilDisposed(int notificationId, @NonNull Notification notification) {
        return Completable.fromAction(() -> notificationManager.notify(notificationId, notification))
                .andThen(Completable.never())
                .doFinally(() -> notificationManager.cancel(notificationId));
    }

    public Completable vibrate() {
        return Completable.fromAction(() -> {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATION_DURATION);
            }
        });
    }

    /*
        Notification channels
     */

    /**
     * Creates all notification channels that might be used by the app, if targeting Android O or
     * later.
     *
     * @see <a href="https://developer.android.com/preview/features/notification-channels.html">Notification
     * Channels</a>
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannels() {
        createStatusNotificationChannel();
        createAccessNotificationChannel();
        createConnectNotificationChannel();
        createEventNotificationChannel();
        createCheckoutReminderNotificationChannel();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createStatusNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_status_title);
        String channelDescription = context.getString(R.string.notification_channel_status_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_STATUS, channelName, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(channelDescription);
        channel.enableLights(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createAccessNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_access_title);
        String channelDescription = context.getString(R.string.notification_channel_access_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_DATA_ACCESS, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(channelDescription);
        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createConnectNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_connect_title);
        String channelDescription = context.getString(R.string.notification_channel_connect_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_CONNECT, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(channelDescription);
        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createEventNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_event_title);
        String channelDescription = context.getString(R.string.notification_channel_event_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_EVENT, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(channelDescription);
        channel.enableLights(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createCheckoutReminderNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_check_out_reminder_title);
        String channelDescription = context.getString(R.string.notification_channel_check_out_reminder_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_CHECKOUT_REMINDER, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(channelDescription);
        channel.enableLights(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    /*
        Status notifications
     */

    public NotificationCompat.Builder createCheckedInNotificationBuilder() {
        return createStatusNotificationBuilder(createNavigationDeepLinkIntent(Uri.parse(context.getString(R.string.deeplink_check_in))), R.string.notification_service_title, R.string.notification_service_description)
                .addAction(createCheckoutAction());
    }

    public NotificationCompat.Builder createMeetingHostNotificationBuilder() {
        return createStatusNotificationBuilder(createNavigationDeepLinkIntent(Uri.parse(context.getString(R.string.deeplink_check_in))), R.string.notification_meeting_host_title, R.string.notification_meeting_host_description)
                .addAction(createEndMeetingAction());
    }

    /**
     * Creates the default status notification builder, intended to serve the ongoing foreground
     * service notification.
     */
    public NotificationCompat.Builder createStatusNotificationBuilder(
            PendingIntent intent,
            @StringRes int titleResourceId,
            @StringRes int descriptionResourceId
    ) {
        return createBaseNotificationBuilder(intent, NOTIFICATION_CHANNEL_ID_STATUS, titleResourceId, descriptionResourceId)
                .setSmallIcon(R.drawable.ic_person_pin)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_STATUS);
    }

    /*
        Event notifications
     */

    @SuppressWarnings("ConstantConditions")
    public Completable showNewsMessageNotification(WhatIsNewMessage message) {
        return Completable.defer(() -> {
            int id = getNotificationId(NOTIFICATION_ID_NEWS_MESSAGE, message.getId());
            Notification notification = createNewsMessageNotificationBuilder(
                    message.getDestination(), message.getTitle(), message.getContent()
            ).build();
            return showNotification(id, notification);
        });
    }

    public NotificationCompat.Builder createNewsMessageNotificationBuilder(Uri destination, String title, String description) {
        return createEventNotificationBuilder(createNavigationDeepLinkIntent(destination), title, description);
    }

    public NotificationCompat.Builder createErrorNotificationBuilder(@NonNull String title, @NonNull String description) {
        return createEventNotificationBuilder(createOpenAppIntent(), title, description)
                .setCategory(Notification.CATEGORY_ERROR);
    }

    public NotificationCompat.Builder createEventNotificationBuilder(
            PendingIntent intent,
            String title,
            String description
    ) {
        return createBaseNotificationBuilder(intent, NOTIFICATION_CHANNEL_ID_EVENT, title, description)
                .setSmallIcon(R.drawable.ic_information_outline)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_EVENT);
    }

    /*
        Other notifications
     */

    public Completable showConnectMessageNotification(ConnectMessage message) {
        return Completable.defer(() -> {
            int id = getNotificationId(NOTIFICATION_ID_CONNECT_MESSAGE, message.getId());
            Notification notification = createConnectMessageNotificationBuilder(
                    message.getTitle(), message.getContent()
            ).build();
            return showNotification(id, notification);
        });
    }

    private NotificationCompat.Builder createConnectMessageNotificationBuilder(@NonNull String title, @NonNull String description) {
        return createBaseNotificationBuilder(createNavigationDeepLinkIntent(Uri.parse(context.getString(R.string.deeplink_connect))), NOTIFICATION_CHANNEL_ID_CONNECT, title, description)
                .setSmallIcon(R.drawable.ic_information_outline)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE);
    }

    /**
     * Creates the default data access notification builder, intended to inform the user that an
     * health department has accessed data related to the user.
     */
    public NotificationCompat.Builder createDataAccessedNotificationBuilder(String traceId) {
        return createBaseNotificationBuilder(
                createNavigationDeepLinkIntent(Uri.parse(context.getString(R.string.deeplink_messages) + "/" + traceId)),
                NOTIFICATION_CHANNEL_ID_DATA_ACCESS,
                R.string.notification_data_accessed_title,
                R.string.notification_data_accessed_description).setSmallIcon(R.drawable.ic_information_outline)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE);
    }

    public NotificationCompat.Builder createCheckOutReminderNotificationBuilder() {
        String title = context.getString(R.string.notification_check_out_reminder_title);
        String description = context.getString(R.string.notification_check_out_reminder_description);
        return createBaseNotificationBuilder(createNavigationDeepLinkIntent(Uri.parse(context.getString(R.string.deeplink_check_in))), NOTIFICATION_CHANNEL_ID_CHECKOUT_REMINDER, title, description)
                .setSmallIcon(R.drawable.ic_hourglass_time_over)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? Notification.CATEGORY_REMINDER : Notification.CATEGORY_EVENT)
                .addAction(createCheckoutAction());
    }

    private NotificationCompat.Builder createBaseNotificationBuilder(
            PendingIntent intent,
            String notificationChannelId,
            @StringRes int titleResource,
            @StringRes int descriptionResource
    ) {
        String title = context.getString(titleResource);
        String description = context.getString(descriptionResource);
        return createBaseNotificationBuilder(intent, notificationChannelId, title, description);
    }

    private NotificationCompat.Builder createBaseNotificationBuilder(
            PendingIntent intent,
            String notificationChannelId,
            String title,
            String description
    ) {
        return new NotificationCompat.Builder(context, notificationChannelId)
                .setContentTitle(title)
                .setContentText(description)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(intent)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(description));
    }

    /*
        Intent creation
     */

    @NonNull
    public PendingIntent createNavigationDeepLinkIntent(@NonNull Uri deepLink) {
        Bundle notificationBundle = new Bundle();
        notificationBundle.putInt(KEY_ACTION, ACTION_NAVIGATE);
        notificationBundle.putParcelable(KEY_DEEPLINK, deepLink);
        return createActivityIntent(MainActivity.class, notificationBundle);
    }

    public PendingIntent createOpenAppIntent() {
        return createActivityIntent(MainActivity.class, null);
    }

    /**
     * Creates a pending intent that will start the specified activity when invoked.
     */
    @SuppressWarnings("rawtypes")
    public PendingIntent createActivityIntent(Class intentClass, @Nullable Bundle notificationBundle) {
        Intent contentIntent = new Intent(context, intentClass);
        if (notificationBundle != null) {
            contentIntent.putExtra(KEY_BUNDLE, notificationBundle);
        }
        contentIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Use random request code so extras from pending intent to same activity are not overriden by different notifications
        // @see https://stackoverflow.com/a/24666194
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getActivity(context, (new Random()).nextInt(), contentIntent, FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
        } else {
            return PendingIntent.getActivity(context, (new Random()).nextInt(), contentIntent, FLAG_UPDATE_CURRENT);
        }
    }

    /**
     * Creates a pending intent that will start the luca service when invoked.
     */
    public PendingIntent createServiceIntent(@Nullable Bundle notificationBundle) {
        Intent contentIntent = new Intent(context, LucaService.class);
        if (notificationBundle != null) {
            contentIntent.putExtra(KEY_BUNDLE, notificationBundle);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, 0, contentIntent, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getService(context, 0, contentIntent, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
        } else {
            return PendingIntent.getService(context, 0, contentIntent, FLAG_UPDATE_CURRENT);
        }
    }

    /*
        Actions
     */

    private NotificationCompat.Action createCheckoutAction() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_ACTION, ACTION_CHECKOUT);
        PendingIntent pendingIntent = createServiceIntent(bundle);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_check_out),
                pendingIntent
        ).build();
    }

    private NotificationCompat.Action createEndMeetingAction() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_ACTION, ACTION_END_MEETING);
        PendingIntent pendingIntent = createServiceIntent(bundle);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_end_meeting),
                pendingIntent
        ).build();
    }

    /*
        Utilities
     */

    /**
     * Attempts to retrieve the bundle that has been added when using {@link
     * #createServiceIntent(Bundle)}.
     */
    @Nullable
    public static Bundle getBundleFromIntentIfAvailable(@Nullable Intent intent) {
        Bundle bundle = intent != null ? intent.getExtras() : null;
        Object value = getObjectFromBundle(bundle, KEY_BUNDLE);
        return value != null ? (Bundle) value : null;
    }

    @Nullable
    public static Integer getActionFromBundleIfAvailable(@Nullable Bundle bundle) {
        Object value = getObjectFromBundle(bundle, KEY_ACTION);
        return value != null ? (Integer) value : null;
    }

    @Nullable
    public static Uri getDeepLinkFromBundleIfAvailable(@Nullable Bundle bundle) {
        Object value = getObjectFromBundle(bundle, KEY_DEEPLINK);
        return value != null ? (Uri) value : null;
    }

    @Nullable
    public static Object getObjectFromBundle(@Nullable Bundle bundle, String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return null;
        }
        return bundle.get(key);
    }

    /**
     * Returns a value in range [baseNotificationId, baseNotificationId + 999] that
     * should be used as notification ID in cases where there may be more than one
     * notification for a given base ID.
     */
    public static int getNotificationId(int baseNotificationId, Object subject) {
        return baseNotificationId + (subject.hashCode() % 1000);
    }

    private boolean areNotificationsEnabled() {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public boolean isNotificationChannelEnabled(String notificationChannelId) {
        if (!areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = NotificationManagerCompat.from(context).getNotificationChannel(notificationChannelId);
            Objects.requireNonNull(channel);
            return channel.getImportance() > NotificationManager.IMPORTANCE_NONE;
        } else {
            return true;
        }
    }

    public void openNotificationSettings(@NonNull String notificationChannelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If notifications are not enabled for the app, the channel can not be enabled.
            if (areNotificationsEnabled()) {
                openNotificationChannelSettings(notificationChannelId);
            } else {
                openNotificationSettingsApi26();
            }
        } else {
            openNotificationSettings();
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent();
        // APP_NOTIFICATION_SETTINGS is not officially supported in Android 5-7,
        // but it works just fine. It IS officially supported as of Android 8.
        // https://stackoverflow.com/questions/32366649/any-way-to-link-to-the-android-notification-settings-for-my-app
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("app_package", context.getPackageName());
        intent.putExtra("app_uid", context.getApplicationInfo().uid);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        Timber.d("Request to open notification settings");
        context.startActivity(intent);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void openNotificationSettingsApi26() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        Timber.d("Request to open notification settings (api 26)");
        context.startActivity(intent);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void openNotificationChannelSettings(String notificationChannelId) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, notificationChannelId);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        Timber.d("Request to open notification channel settings: %s", notificationChannelId);
        context.startActivity(intent);
    }

}
