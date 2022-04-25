/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.culture4life.luca.testtools.matcher

import android.view.View
import androidx.annotation.ColorInt
import androidx.cardview.widget.CardView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

class CardViewMatchers {

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun hasBackgroundColor(@ColorInt colorId: Int): Matcher<View> = HasCardBackgroundColorMatcher(colorId) as Matcher<View>
    }

    class HasCardBackgroundColorMatcher(@ColorInt private val colorId: Int) : TypeSafeMatcher<CardView>() {
        override fun matchesSafely(view: CardView): Boolean {
            return view.cardBackgroundColor.defaultColor == view.resources.getColor(colorId, view.context.theme)
        }

        override fun describeTo(description: Description) {
            description.appendText("has card background color ID: $colorId")
        }
    }
}
