package de.culture4life.luca.testtools.mocks

import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.matcher.UriMatchers
import org.hamcrest.Matchers

object IntentMocks {

    /**
     * Avoid the "No Activity found to handle Intent" for "market" scheme (aka PlayStore).
     */
    fun givenMarketIntentResponse(resultCode: Int = 12345, resultData: Intent = Intent()) {
        Intents.intending(
            Matchers.allOf(
                IntentMatchers.hasAction(Intent.ACTION_VIEW),
                IntentMatchers.hasData(UriMatchers.hasHost("play.google.com"))
            )
        ).respondWith(Instrumentation.ActivityResult(resultCode, resultData))
    }
}
