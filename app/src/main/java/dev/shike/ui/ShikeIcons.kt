package dev.shike.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Selected Lucide paths. See docs/lucide-LICENSE. */
object ShikeIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val vector =
            ImageVector.Builder(
                name,
                24.dp,
                24.dp,
                24f,
                24f,
                autoMirror = name in setOf("ArrowBack", "Undo", "Redo"),
            )
        for (path in paths) {
            vector.addPath(
                PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return vector.build()
    }

    val Add by lazy { icon("Add", "M5 12h14", "M12 5v14") }
    val ArrowBack by lazy { icon("ArrowBack", "m12 19-7-7 7-7", "M19 12H5") }
    val ArrowDownward by lazy { icon("ArrowDownward", "M12 5v14", "m19 12-7 7-7-7") }
    val ArrowUpward by lazy { icon("ArrowUpward", "m5 12 7-7 7 7", "M12 19V5") }
    val AutoStories by lazy {
        icon(
            "AutoStories",
            "M12 7v14",
            "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z",
        )
    }
    val BarChart by lazy { icon("BarChart", "M18 20L18 10", "M12 20L12 4", "M6 20L6 14") }
    val CalendarToday by lazy {
        icon(
            "CalendarToday",
            "M8 2v4",
            "M16 2v4",
            "M5.0 4.0H19.0Q21.0 4.0 21.0 6.0V20.0Q21.0 22.0 19.0 22.0H5.0Q3.0 22.0 3.0 20.0V6.0Q3.0 4.0 5.0 4.0Z",
            "M3 10h18",
            "M8 14h.01",
            "M12 14h.01",
            "M16 14h.01",
            "M8 18h.01",
            "M12 18h.01",
            "M16 18h.01",
        )
    }
    val Category by lazy {
        icon(
            "Category",
            "M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z",
            "M4.0 14.0H9.0Q10.0 14.0 10.0 15.0V20.0Q10.0 21.0 9.0 21.0H4.0Q3.0 21.0 3.0 20.0V15.0Q3.0 14.0 4.0 14.0Z",
            "M14.0 17.5a3.5 3.5 0 1 0 7.0 0a3.5 3.5 0 1 0 -7.0 0",
        )
    }
    val Check by lazy { icon("Check", "M20 6 9 17l-5-5") }
    val CheckCircle by lazy {
        icon(
            "CheckCircle",
            "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0",
            "m9 12 2 2 4-4",
        )
    }
    val ChevronLeft by lazy { icon("ChevronLeft", "m15 18-6-6 6-6") }
    val ChevronRight by lazy { icon("ChevronRight", "m9 18 6-6-6-6") }
    val Circle by lazy {
        icon("Circle", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0")
    }
    val Close by lazy { icon("Close", "M18 6 6 18", "m6 6 12 12") }
    val DeleteOutline by lazy {
        icon(
            "DeleteOutline",
            "M3 6h18",
            "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
            "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
            "M10 11L10 17",
            "M14 11L14 17",
        )
    }
    val Description by lazy {
        icon(
            "Description",
            "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z",
            "M14 2v4a2 2 0 0 0 2 2h4",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8",
        )
    }
    val Edit by lazy {
        icon(
            "Edit",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }
    val EditNote by lazy {
        icon(
            "EditNote",
            "M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4",
            "M2 6h4",
            "M2 10h4",
            "M2 14h4",
            "M2 18h4",
            "M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z",
        )
    }
    val ExpandLess by lazy { icon("ExpandLess", "m18 15-6-6-6 6") }
    val ExpandMore by lazy { icon("ExpandMore", "m6 9 6 6 6-6") }
    val FilterList by lazy { icon("FilterList", "M3 6h18", "M7 12h10", "M10 18h4") }
    val Flag by lazy {
        icon("Flag", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22L4 15")
    }
    val Folder by lazy {
        icon(
            "Folder",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }
    val History by lazy {
        icon(
            "History",
            "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5",
            "M12 7v5l4 2",
        )
    }
    val Home by lazy {
        icon(
            "Home",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }
    val Inventory2 by lazy {
        icon(
            "Inventory2",
            "M3.0 3.0H21.0Q22.0 3.0 22.0 4.0V7.0Q22.0 8.0 21.0 8.0H3.0Q2.0 8.0 2.0 7.0V4.0Q2.0 3.0 3.0 3.0Z",
            "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8",
            "M10 12h4",
        )
    }
    val Lock by lazy {
        icon(
            "Lock",
            "M5.0 11.0H19.0Q21.0 11.0 21.0 13.0V20.0Q21.0 22.0 19.0 22.0H5.0Q3.0 22.0 3.0 20.0V13.0Q3.0 11.0 5.0 11.0Z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }
    val MoreHoriz by lazy {
        icon(
            "MoreHoriz",
            "M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0",
            "M18.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0",
            "M4.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0",
        )
    }
    val OpenInNew by lazy {
        icon(
            "OpenInNew",
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        )
    }
    val RadioButtonUnchecked by lazy {
        icon("RadioButtonUnchecked", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0")
    }
    val Redo by lazy {
        icon("Redo", "m15 14 5-5-5-5", "M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13")
    }
    val Repeat by lazy {
        icon(
            "Repeat",
            "m17 2 4 4-4 4",
            "M3 11v-1a4 4 0 0 1 4-4h14",
            "m7 22-4-4 4-4",
            "M21 13v1a4 4 0 0 1-4 4H3",
        )
    }
    val School by lazy {
        icon(
            "School",
            "M21.42 10.922a1 1 0 0 0-.019-1.838L12.83 5.18a2 2 0 0 0-1.66 0L2.6 9.08a1 1 0 0 0 0 1.832l8.57 3.908a2 2 0 0 0 1.66 0z",
            "M22 10v6",
            "M6 12.5V16a6 3 0 0 0 12 0v-3.5",
        )
    }
    val Search by lazy {
        icon("Search", "M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0", "m21 21-4.3-4.3")
    }
    val Settings by lazy {
        icon(
            "Settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            "M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0",
        )
    }
    val Spa by lazy {
        icon(
            "Spa",
            "M7 20h10",
            "M10 20c5.5-2.5.8-6.4 3-10",
            "M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z",
            "M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z",
        )
    }
    val StarOutline by lazy {
        icon(
            "StarOutline",
            "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
        )
    }
    val Tag by lazy {
        icon(
            "Tag",
            "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z",
            "M7.0 7.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0",
        )
    }
    val Tune by lazy {
        icon(
            "Tune",
            "M21 4L14 4",
            "M10 4L3 4",
            "M21 12L12 12",
            "M8 12L3 12",
            "M21 20L16 20",
            "M12 20L3 20",
            "M14 2L14 6",
            "M8 10L8 14",
            "M16 18L16 22",
        )
    }
    val Undo by lazy {
        icon("Undo", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11")
    }
    val Widgets by lazy {
        icon(
            "Widgets",
            "M4.0 3.0H9.0Q10.0 3.0 10.0 4.0V9.0Q10.0 10.0 9.0 10.0H4.0Q3.0 10.0 3.0 9.0V4.0Q3.0 3.0 4.0 3.0Z",
            "M15.0 3.0H20.0Q21.0 3.0 21.0 4.0V9.0Q21.0 10.0 20.0 10.0H15.0Q14.0 10.0 14.0 9.0V4.0Q14.0 3.0 15.0 3.0Z",
            "M15.0 14.0H20.0Q21.0 14.0 21.0 15.0V20.0Q21.0 21.0 20.0 21.0H15.0Q14.0 21.0 14.0 20.0V15.0Q14.0 14.0 15.0 14.0Z",
            "M4.0 14.0H9.0Q10.0 14.0 10.0 15.0V20.0Q10.0 21.0 9.0 21.0H4.0Q3.0 21.0 3.0 20.0V15.0Q3.0 14.0 4.0 14.0Z",
        )
    }
    val WorkOutline by lazy {
        icon(
            "WorkOutline",
            "M12 12h.01",
            "M16 6V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2",
            "M22 13a18.15 18.15 0 0 1-20 0",
            "M4.0 6.0H20.0Q22.0 6.0 22.0 8.0V18.0Q22.0 20.0 20.0 20.0H4.0Q2.0 20.0 2.0 18.0V8.0Q2.0 6.0 4.0 6.0Z",
        )
    }
}
