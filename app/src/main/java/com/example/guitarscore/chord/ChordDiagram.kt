package com.example.guitarscore.chord

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun ChordDiagram(voicing: ChordVoicing, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.tertiary
    val accentColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        val positiveFrets = voicing.frets.filter { it > 0 }
        val minimumFret = positiveFrets.minOrNull() ?: 1
        val startFret = if (minimumFret > 4) minimumFret else 1
        val left = 42.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 38.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val stringGap = (right - left) / 5f
        val fretGap = (bottom - top) / 5f
        val dotRadius = min(12.dp.toPx(), min(stringGap, fretGap) * 0.32f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        for (string in 0..5) {
            val x = left + string * stringGap
            drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
        }
        for (line in 0..5) {
            val y = top + line * fretGap
            drawLine(
                lineColor,
                Offset(left, y),
                Offset(right, y),
                strokeWidth = if (startFret == 1 && line == 0) 6.dp.toPx() else 1.5.dp.toPx()
            )
        }

        textPaint.color = labelColor.toArgb()
        textPaint.textSize = 10.dp.toPx()
        for (fretOffset in 0 until 5) {
            val y = top + (fretOffset + 0.5f) * fretGap
            drawContext.canvas.nativeCanvas.drawText(
                "${startFret + fretOffset}",
                left - 22.dp.toPx(),
                y - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint
            )
        }
        val stringNames = listOf("E", "A", "D", "G", "B", "E")
        stringNames.forEachIndexed { string, label ->
            val x = left + string * stringGap
            drawContext.canvas.nativeCanvas.drawText(label, x, bottom + 19.dp.toPx(), textPaint)
        }

        voicing.frets.forEachIndexed { string, fret ->
            val x = left + string * stringGap
            textPaint.textSize = 13.dp.toPx()
            textPaint.color = if (fret < 0) mutedColor.toArgb() else accentColor.toArgb()
            val marker = when {
                fret < 0 -> "X"
                fret == 0 -> "O"
                else -> null
            }
            marker?.let {
                drawContext.canvas.nativeCanvas.drawText(
                    it,
                    x,
                    top - 13.dp.toPx() - (textPaint.ascent() + textPaint.descent()) / 2f,
                    textPaint
                )
            }
        }

        voicing.barres.forEach { barre ->
            if (barre.fret !in startFret until startFret + 5) return@forEach
            val y = top + (barre.fret - startFret + 0.5f) * fretGap
            val startX = left + barre.startString * stringGap
            val endX = left + barre.endString * stringGap
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(startX - dotRadius, y - dotRadius),
                size = androidx.compose.ui.geometry.Size(endX - startX + dotRadius * 2, dotRadius * 2),
                cornerRadius = CornerRadius(dotRadius, dotRadius)
            )
            textPaint.color = Color.White.toArgb()
            textPaint.textSize = 11.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                barre.finger.toString(),
                (startX + endX) / 2f,
                y - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint
            )
        }

        voicing.frets.forEachIndexed { string, fret ->
            if (fret <= 0 || fret !in startFret until startFret + 5) return@forEachIndexed
            val coveredByBarre = voicing.barres.any {
                fret == it.fret && string in it.startString..it.endString
            }
            if (coveredByBarre) return@forEachIndexed
            val x = left + string * stringGap
            val y = top + (fret - startFret + 0.5f) * fretGap
            drawCircle(accentColor, dotRadius, Offset(x, y))
            val finger = voicing.fingers.getOrElse(string) { 0 }
            if (finger > 0) {
                textPaint.color = Color.White.toArgb()
                textPaint.textSize = 11.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    finger.toString(),
                    x,
                    y - (textPaint.ascent() + textPaint.descent()) / 2f,
                    textPaint
                )
            }
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
