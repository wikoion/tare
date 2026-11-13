package tare.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24-unit grid, drawn for this app. Tint them with Icon's `tint`. */
object TareIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) {
                addPath(
                    pathData = addPathNodes(p),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        }.build()

    val Back = icon("back", "M15 5L8 12L15 19")
    val Forward = icon("forward", "M9 5L16 12L9 19")
    val Plus = icon("plus", "M12 5V19M5 12H19")
    val ArrowDown = icon("down", "M12 4V19M6.5 13.5L12 19L17.5 13.5", stroke = 2.2f)
    val ArrowUp = icon("up", "M12 20V5M6.5 10.5L12 5L17.5 10.5", stroke = 2.2f)
    val Clock = icon("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 7V12L15.5 14")
    val Share = icon("share", "M8.5 9.5H6.5V20H17.5V9.5H15.5", "M12 3V14", "M8.5 6.5L12 3L15.5 6.5")
    val Trends = icon("trends", "M4 18L9 12L13 15L20 7", "M15 7H20V12")
    val Flag = icon("flag", "M6 21V4", "M6 4H17L14.5 8L17 12H6")
    val Pencil = icon("pencil", "M4 20L4.6 16.4L15.8 5.2L18.8 8.2L7.6 19.4Z", "M13.6 7.4L16.6 10.4")
    val Settings = icon(
        "settings",
        "M4 7H13", "M19 7H20", "M14 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
        "M4 17H5", "M11 17H20", "M5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
    )
    val Bluetooth = icon("bluetooth", "M7 7.5L17 16L12 20.5V3.5L17 8L7 16.5")
    val Scale = icon(
        "scale",
        "M8 3.5H16A4.5 4.5 0 0 1 20.5 8V16A4.5 4.5 0 0 1 16 20.5H8A4.5 4.5 0 0 1 3.5 16V8A4.5 4.5 0 0 1 8 3.5Z",
        "M8.5 10.5A3.5 3.5 0 0 1 15.5 10.5", "M12 10.5L13.6 8.6",
    )
    val Trash = icon("trash", "M5 7H19", "M10 4H14", "M7 7L8 20H16L17 7", "M10.5 11V16M13.5 11V16")
    val Check = icon("check", "M5 12.5L10 17L19 7", stroke = 2.2f)
    val Close = icon("close", "M6 6L18 18M18 6L6 18")
    val Person = icon("person", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M4.5 20C4.5 16.4 7.9 14.5 12 14.5C16.1 14.5 19.5 16.4 19.5 20")
    val Info = icon("info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 11V16.5", "M12 7.6V7.7")
    val Heart = filled(
        "heart",
        "M12 20.5C5.2 16 3 12.4 3 9.1C3 6.4 5 4.5 7.4 4.5C9.3 4.5 10.9 5.6 12 7.3C13.1 5.6 14.7 4.5 16.6 4.5C19 4.5 21 6.4 21 9.1C21 12.4 18.8 16 12 20.5Z",
    )
    val Dot = filled("dot", "M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0")
}
