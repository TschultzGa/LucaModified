package de.culture4life.luca.util;

import static android.content.res.Configuration.KEYBOARD_QWERTY;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;

import java.util.List;

import timber.log.Timber;

/**
 * Helper for AccessibilityServices
 */
public class AccessibilityServiceUtil {

    /**
     * This method checks if a screen reader is enabled by using the {@link AccessibilityManager}.
     */
    public static boolean isScreenReaderActive(@NonNull Context context) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> accessibilityServiceInfoList = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
        return !accessibilityServiceInfoList.isEmpty();
    }

    /**
     * Speak the talkbackText as feedback to the user when a screen reader is active.
     *
     * @param context      Context for getting the AccessibilityManager
     * @param talkbackText text to speak
     */
    public static void speak(@NonNull Context context, @NonNull String talkbackText) {
        if (!isScreenReaderActive(context)) {
            return;
        }
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
        accessibilityEvent.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        accessibilityEvent.getText().add(talkbackText);
        accessibilityManager.sendAccessibilityEvent(accessibilityEvent);
    }

    /**
     * This method checks if there is a keyboard connected capable of navigating the App.
     *
     * @param context Context for getting the keyboard configuration.
     */
    public static boolean isKeyboardConnected(@NonNull Context context) {
        return context.getResources().getConfiguration().keyboard == KEYBOARD_QWERTY;
    }

    /**
     * Useful for detecting when users have a bigger font size selected in the system preferences.
     *
     * @param context Context for getting the ContentResolver
     */
    public static float getFontScale(@NonNull Context context) {
        try {
            return context.getResources().getConfiguration().fontScale;
        } catch (Exception e) {
            Timber.e("Exception while getting font scale factor: %s", e.toString());
            return -1;
        }
    }

    /**
     * This method checks if a key event is a confirm button.
     *
     * @param event KeyEvent to be evaluated
     */
    public static boolean isKeyConfirmButton(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A;
    }

}
