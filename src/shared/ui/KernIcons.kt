package dev.kern.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Single semantic icon set for Kern. The design specifies a 24-viewBox line set
 * (2px stroke, round caps); the closest match shipping in the app is Material's
 * Outlined theme, mapped here so call sites stay semantic and any icon can later
 * be swapped for a pixel-exact custom vector in one place.
 *
 * Names mirror the design handoff's icon list (kern-theme.jsx `PATHS`).
 */
object KernIcons {
    val Search = Icons.Outlined.Search
    val Grid = Icons.Outlined.GridView
    val List = Icons.AutoMirrored.Outlined.ViewList
    val Settings = Icons.Outlined.Settings
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Plus = Icons.Outlined.Add
    val Shield = Icons.Outlined.Shield
    val Lock = Icons.Outlined.Lock
    val Sun = Icons.Outlined.LightMode
    val Moon = Icons.Outlined.DarkMode
    val Chevron = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val ChevronLeft = Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    val ChevronDown = Icons.Outlined.ExpandMore
    val More = Icons.Outlined.MoreVert
    val Check = Icons.Outlined.Check
    val Sort = Icons.AutoMirrored.Outlined.Sort
    val ArrowUp = Icons.Outlined.ArrowUpward
    val ArrowDown = Icons.Outlined.ArrowDownward
    val Clock = Icons.Outlined.Schedule
    val Star = Icons.Outlined.StarBorder
    val StarFilled = Icons.Filled.Star
    val Share = Icons.Outlined.Share
    val Save = Icons.Outlined.Save
    val Undo = Icons.AutoMirrored.Outlined.Undo
    val Redo = Icons.AutoMirrored.Outlined.Redo
    val Bold = Icons.Outlined.FormatBold
    val Italic = Icons.Outlined.FormatItalic
    val Underline = Icons.Outlined.FormatUnderlined
    val Strikethrough = Icons.Outlined.FormatStrikethrough
    val FontColor = Icons.Outlined.FormatColorText
    val FontSize = Icons.Outlined.FormatSize
    val Bullet = Icons.AutoMirrored.Outlined.FormatListBulleted
    val Table = Icons.Outlined.TableChart
    val Slides = Icons.Outlined.Slideshow
    val Pen = Icons.Outlined.Edit
    val Highlight = Icons.Outlined.BorderColor
    val Merge = Icons.Outlined.CallMerge
    val Split = Icons.Outlined.CallSplit
    val Close = Icons.Outlined.Close
    val Filter = Icons.Outlined.FilterList
    val Folder = Icons.Outlined.Folder
    val Doc = Icons.Outlined.Description
    val Page = Icons.Outlined.Article
    val Text = Icons.Outlined.TextFields
    val Zoom = Icons.Outlined.ZoomIn
    val Trash = Icons.Outlined.Delete
    val Download = Icons.Outlined.Download
    val Info = Icons.Outlined.Info
    val Link = Icons.Outlined.Link
    val Pin = Icons.Outlined.PushPin
    val WifiOff = Icons.Outlined.WifiOff

    /** GitHub mark: not in Material, built from the handoff path data. */
    val Github: ImageVector by lazy {
        ImageVector.Builder(
            name = "github",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M12 2a10 10 0 0 0-3.2 19.5c.5.1.7-.2.7-.5v-1.7c-2.8.6-3.4-1.3-3.4-1.3-.5-1.2-1.1-1.5-1.1-1.5-.9-.6.1-.6.1-.6 1 .1 1.5 1 1.5 1 .9 1.5 2.3 1.1 2.9.8.1-.6.3-1.1.6-1.3-2.2-.25-4.6-1.1-4.6-5a4 4 0 0 1 1-2.7c-.1-.3-.4-1.3.1-2.6 0 0 .8-.3 2.7 1a9.4 9.4 0 0 1 5 0c1.9-1.3 2.7-1 2.7-1 .5 1.3.2 2.3.1 2.6a4 4 0 0 1 1 2.7c0 3.9-2.4 4.7-4.6 5 .3.3.6.9.6 1.8v2.7c0 .3.2.6.7.5A10 10 0 0 0 12 2z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}
