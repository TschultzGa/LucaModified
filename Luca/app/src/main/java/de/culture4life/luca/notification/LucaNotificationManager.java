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
import de.culture4life.luca.service.LucaService;
import de.culture4life.luca.ui.MainActivity;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import timber.log.Timber;

public class LucaNotificationManager extends Manager {

    public static final int NOTIFICATION_ID_STATUS = 1;
    public static final int NOTIFICATION_ID_DATA_ACCESS = 2000; // dynamically incremented by warning level
    public static final int NOTIFICATION_ID_EVENT = 3;
    public static final int NOTIFICATION_ID_CHECKOUT_REMINDER = 4;
    public static final int NOTIFICATION_ID_CONNECT_ENROLLMENT_SUPPORTED = 5;
    public static final int NOTIFICATION_ID_CONNECT_MESSAGE = 3000; // dynamically incremented by message ID
    private static final String NOTIFICATION_CHANNEL_ID_PREFIX = "channel_";
    private static final String NOTIFICATION_CHANNEL_ID_STATUS = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_STATUS;
    private static final String NOTIFICATION_CHANNEL_ID_DATA_ACCESS = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_DATA_ACCESS;
    public static final String NOTIFICATION_CHANNEL_ID_CONNECT = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_CONNECT_MESSAGE;
    public static final String NOTIFICATION_CHANNEL_ID_EVENT = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_EVENT;
    private static final String NOTIFICATION_CHANNEL_ID_CHECKOUT_REMINDER = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_CHECKOUT_REMINDER;

    private static final String BUNDLE_KEY = "notification_bundle";
    private static final String ACTION_KEY = "notification_action";

    public static final int ACTION_STOP = 1;
    public static final int ACTION_CHECKOUT = 2;
    public static final int ACTION_END_MEETING = 3;
    public static final int ACTION_SHOW_MESSAGES = 4;
    public static final int ACTION_SHOW_CHECKIN_TAB = 5;

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

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createCheckedInNotificationBuilder(Class activityClass) {
        Bundle notificationBundle = new Bundle();
        notificationBundle.putInt(ACTION_KEY, ACTION_SHOW_CHECKIN_TAB);
        return createStatusNotificationBuilder(activityClass, notificationBundle, R.string.notification_service_title, R.string.notification_service_description)
                .addAction(createCheckoutAction());
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createMeetingHostNotificationBuilder(Class activityClass) {
        return createStatusNotificationBuilder(activityClass, null, R.string.notification_meeting_host_title, R.string.notification_meeting_host_description)
                .addAction(createEndMeetingAction());
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createCheckOutReminderNotificationBuilder(Class activityClass) {
        Bundle notificationBundle = new Bundle();
        notificationBundle.putInt(ACTION_KEY, ACTION_SHOW_CHECKIN_TAB);
        return createCheckOutReminderNotificationBuilder(activityClass, notificationBundle, R.string.notification_check_out_reminder_title, R.string.notification_check_out_reminder_description)
                .addAction(createCheckoutAction());
    }

    /**
     * Creates the default data access notification builder, intended to inform the user that an
     * health department has accessed data related to the user.
     */
    public NotificationCompat.Builder createDataAccessedNotificationBuilder() {
        Bundle notificationBundle = new Bundle();
        notificationBundle.putInt(ACTION_KEY, ACTION_SHOW_MESSAGES);

        return createBaseNotificationBuilder(MainActivity.class, NOTIFICATION_CHANNEL_ID_DATA_ACCESS, R.string.notification_data_accessed_title, R.string.notification_data_accessed_description, notificationBundle)
                .setSmallIcon(R.drawable.ic_information_outline)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE);
    }

    public NotificationCompat.Builder createConnectEnrollmentSupportedNotificationBuilder() {
        Bundle notificationBundle = new Bundle();
        notificationBundle.putInt(ACTION_KEY, ACTION_SHOW_MESSAGES);

        return createBaseNotificationBuilder(MainActivity.class, NOTIFICATION_CHANNEL_ID_CONNECT, R.string.notification_luca_connect_supported_title, R.string.notification_luca_connect_supported_description, notificationBundle)
                .setSmallIcon(R.drawable.ic_information_outline)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_RECOMMENDATION);
    }

    public NotificationCompat.Builder createConnectMessageNotificationBuilder(@NonNull String title, @NonNull String description) {
        Bundle notificationBundle = new Bundle();
        notificationBundle.putInt(ACTION_KEY, ACTION_SHOW_MESSAGES);

        return createBaseNotificationBuilder(MainActivity.class, NOTIFICATION_CHANNEL_ID_CONNECT, title, description, notificationBundle)
                .setSmallIcon(R.drawable.ic_information_outline)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE);
    }

    /**
     * Creates the default status notification builder, intended to serve the ongoing foreground
     * service notification.
     */
    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createStatusNotificationBuilder(
            Class activityClass,
            @Nullable Bundle notificationBundle,
            @StringRes int titleResourceId,
            @StringRes int descriptionResourceId
    ) {
        return createBaseNotificationBuilder(activityClass, NOTIFICATION_CHANNEL_ID_STATUS, titleResourceId, descriptionResourceId, notificationBundle)
                .setSmallIcon(R.drawable.ic_person_pin)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_STATUS);
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createEventNotificationBuilder(Class activityClass, @StringRes int titleResourceId, @NonNull String description) {
        return createBaseNotificationBuilder(activityClass, NOTIFICATION_CHANNEL_ID_EVENT, titleResourceId, description)
                .setSmallIcon(R.drawable.ic_information_outline)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_EVENT);
    }

    @SuppressWarnings("rawtypes")
    private NotificationCompat.Builder createCheckOutReminderNotificationBuilder(
            Class activityClass,
            @Nullable Bundle notificationBundle,
            @StringRes int titleResourceId,
            @StringRes int descriptionResourceId
    ) {
        return createBaseNotificationBuilder(activityClass, NOTIFICATION_CHANNEL_ID_CHECKOUT_REMINDER, titleResourceId, descriptionResourceId, notificationBundle)
                .setSmallIcon(R.drawable.ic_hourglass_time_over)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? Notification.CATEGORY_REMINDER : Notification.CATEGORY_EVENT);
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createErrorNotificationBuilder(Class activityClass, @NonNull String title, @NonNull String description) {
        return createBaseNotificationBuilder(activityClass, NOTIFICATION_CHANNEL_ID_EVENT, R.string.notification_error_title, description)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_error_outline)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_ERROR);
    }

    @SuppressWarnings("rawtypes")
    private NotificationCompat.Builder createBaseNotificationBuilder(
            Class activityClass,
            String notificationChannelId,
            @StringRes int titleResourceId,
            @StringRes int descriptionResourceId
    ) {
        return createBaseNotificationBuilder(activityClass, notificationChannelId, context.getText(titleResourceId).toString(), context.getText(descriptionResourceId).toString(), null);
    }

    @SuppressWarnings("rawtypes")
    private NotificationCompat.Builder createBaseNotificationBuilder(
            Class activityClass, String notificationChannelId,
            @StringRes int titleResourceId,
            @StringRes int descriptionResourceId,
            @Nullable Bundle notificationBundle
    ) {
        return createBaseNotificationBuilder(activityClass, notificationChannelId, context.getText(titleResourceId).toString(), context.getText(descriptionResourceId).toString(), notificationBundle);
    }

    @SuppressWarnings("rawtypes")
    private NotificationCompat.Builder createBaseNotificationBuilder(
            Class activityClass,
            String notificationChannelId,
            @StringRes int titleResourceId,
            String description
    ) {
        return createBaseNotificationBuilder(activityClass, notificationChannelId, context.getText(titleResourceId).toString(), description, null);
    }

    @SuppressWarnings("rawtypes")
    private NotificationCompat.Builder createBaseNotificationBuilder(
            Class activityClass,
            String notificationChannelId,
            String title,
            String description,
            @Nullable Bundle notificationBundle
    ) {
        return new NotificationCompat.Builder(context, notificationChannelId)
                .setContentTitle(title)
                .setContentText(description)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(createActivityIntent(activityClass, notificationBundle))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(description));
    }

    /**
     * Creates a pending intent that will start the specified activity when invoked.
     */
    @SuppressWarnings("rawtypes")
    public PendingIntent createActivityIntent(Class intentClass, @Nullable Bundle notificationBundle) {
        Intent contentIntent = new Intent(context, intentClass);
        if (notificationBundle != null) {
            contentIntent.putExtra(BUNDLE_KEY, notificationBundle);
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
     * Creates a pending intent that will start the specified service when invoked.
     */
    public PendingIntent createServiceIntent(@Nullable Bundle notificationBundle) {
        Intent contentIntent = new Intent(context, LucaService.class);
        if (notificationBundle != null) {
            contentIntent.putExtra(BUNDLE_KEY, notificationBundle);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, 0, contentIntent, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getService(context, 0, contentIntent, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
        } else {
            return PendingIntent.getService(context, 0, contentIntent, FLAG_UPDATE_CURRENT);
        }
    }

    private NotificationCompat.Action createCheckoutAction() {
        Bundle bundle = new Bundle();
        bundle.putInt(ACTION_KEY, ACTION_CHECKOUT);
        PendingIntent pendingIntent = createServiceIntent(bundle);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_check_out),
                pendingIntent
        ).build();
    }

    private NotificationCompat.Action createEndMeetingAction() {
        Bundle bundle = new Bundle();
        bundle.putInt(ACTION_KEY, ACTION_END_MEETING);
        PendingIntent pendingIntent = createServiceIntent(bundle);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_end_meeting),
                pendingIntent
        ).build();
    }

    /**
     * Attempts to retrieve the bundle that has been added when using {@link
     * #createServiceIntent(Bundle)}.
     */
    public static Maybe<Bundle> getBundleFromIntent(@Nullable Intent intent) {
        return Maybe.defer(() -> {
            if (intent != null && intent.getExtras() != null) {
                Bundle extras = intent.getExtras();
                if (extras.containsKey(BUNDLE_KEY)) {
                    return Maybe.fromCallable(() -> extras.getBundle(BUNDLE_KEY));
                }
            }
            return Maybe.empty();
        });
    }

    public static Maybe<Integer> getActionFromBundle(@Nullable Bundle bundle) {
        return Maybe.defer(() -> {
            if (bundle != null && bundle.containsKey(ACTION_KEY)) {
                return Maybe.fromCallable(() -> bundle.getInt(ACTION_KEY));
            }
            return Maybe.empty();
        });
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

    public void openNotificationSettings(String notificationChannelId) {
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
