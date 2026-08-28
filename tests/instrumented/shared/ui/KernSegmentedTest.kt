package dev.kern.shared.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kern.shared.theme.KernTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tapping a segment reports the selected index. */
@RunWith(AndroidJUnit4::class)
class KernSegmentedTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selecting_an_item_invokes_callback() {
        var selected = 0
        compose.setContent {
            KernTheme {
                KernSegmented(
                    items = listOf(SegmentItem("Recent"), SegmentItem("All files")),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
        }
        compose.onNodeWithText("Recent").assertIsDisplayed()
        compose.onNodeWithText("All files").performClick()
        assertEquals(1, selected)
    }
}
