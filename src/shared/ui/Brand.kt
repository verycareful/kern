package dev.kern.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import dev.kern.R
import dev.kern.shared.theme.KernTheme

/**
 * Brand chevron mark (the "k"). Theme-aware: navy on light surfaces, light on
 * dark. Size it via [modifier] (e.g. `Modifier.size(26.dp)`). Used in the
 * browser header.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val res = if (KernTheme.colors.dark) R.drawable.kern_mark_light else R.drawable.kern_mark_navy
    Image(
        painter = painterResource(res),
        contentDescription = "Kern",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * Full "kern" wordmark lockup. Theme-aware. Size via [modifier] height (e.g.
 * `Modifier.height(34.dp)`). Featured on the Settings screen.
 */
@Composable
fun BrandLockup(modifier: Modifier = Modifier) {
    val res = if (KernTheme.colors.dark) R.drawable.kern_lockup_light else R.drawable.kern_lockup_navy
    Image(
        painter = painterResource(res),
        contentDescription = "Kern",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
