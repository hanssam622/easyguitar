package com.example.guitarscore.chord

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 가로 방향 코드 다이어그램. 왼쪽이 헤드(넥 상단)이고 오른쪽으로 갈수록 프렛이 올라간다.
 * 위에서 아래로 6번 줄(저음 E) → 1번 줄(고음 E) 순서라, 기타를 눕혀 놓고 내려다보는 것과 방향이 같다.
 */
@Composable
fun ChordDiagram(voicing: ChordVoicing, modifier: Modifier = Modifier, showStringNames: Boolean = true) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.tertiary
    val accentColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        val positiveFrets = voicing.frets.filter { it > 0 }
        val minimumFret = positiveFrets.minOrNull() ?: 1
        val startFret = if (minimumFret > 4) minimumFret else 1

        val left = 26.dp.toPx()
        val right = size.width - (if (showStringNames) 24.dp.toPx() else 8.dp.toPx())
        val top = 20.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val fretGap = (right - left) / 5f
        val stringGap = (bottom - top) / 5f
        val dotRadius = min(11.dp.toPx(), min(stringGap, fretGap) * 0.36f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        // 줄은 저음현일수록 굵게 그려서 위아래 방향을 헷갈리지 않게 한다.
        for (string in 0..5) {
            val y = top + string * stringGap
            drawLine(lineColor, Offset(left, y), Offset(right, y), strokeWidth = (2.4f - string * 0.25f).dp.toPx())
        }
        for (fret in 0..5) {
            val x = left + fret * fretGap
            drawLine(
                lineColor,
                Offset(x, top),
                Offset(x, bottom),
                strokeWidth = if (startFret == 1 && fret == 0) 6.dp.toPx() else 1.5.dp.toPx()
            )
        }

        textPaint.color = labelColor.toArgb()
        textPaint.textSize = 9.dp.toPx()
        for (fretOffset in 0 until 5) {
            val x = left + (fretOffset + 0.5f) * fretGap
            drawContext.canvas.nativeCanvas.drawText("${startFret + fretOffset}", x, top - 7.dp.toPx(), textPaint)
        }
        if (showStringNames) {
            val stringNames = listOf("6E", "5A", "4D", "3G", "2B", "1E")
            stringNames.forEachIndexed { string, label ->
                val y = top + string * stringGap
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    right + 13.dp.toPx(),
                    y - (textPaint.ascent() + textPaint.descent()) / 2f,
                    textPaint
                )
            }
        }

        // 뮤트(X) / 개방현(O)은 넥 왼쪽 바깥에 표시한다.
        textPaint.textSize = 12.dp.toPx()
        voicing.frets.forEachIndexed { string, fret ->
            val marker = when {
                fret < 0 -> "X"
                fret == 0 -> "O"
                else -> null
            } ?: return@forEachIndexed
            textPaint.color = if (fret < 0) mutedColor.toArgb() else accentColor.toArgb()
            val y = top + string * stringGap
            drawContext.canvas.nativeCanvas.drawText(
                marker,
                left - 13.dp.toPx(),
                y - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint
            )
        }

        voicing.barres.forEach { barre ->
            if (barre.fret !in startFret until startFret + 5) return@forEach
            val x = left + (barre.fret - startFret + 0.5f) * fretGap
            val startY = top + barre.startString * stringGap
            val endY = top + barre.endString * stringGap
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(x - dotRadius, startY - dotRadius),
                size = Size(dotRadius * 2, endY - startY + dotRadius * 2),
                cornerRadius = CornerRadius(dotRadius, dotRadius)
            )
            textPaint.color = Color.White.toArgb()
            textPaint.textSize = 10.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                barre.finger.toString(),
                x,
                (startY + endY) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint
            )
        }

        voicing.frets.forEachIndexed { string, fret ->
            if (fret <= 0 || fret !in startFret until startFret + 5) return@forEachIndexed
            val coveredByBarre = voicing.barres.any {
                fret == it.fret && string in it.startString..it.endString
            }
            if (coveredByBarre) return@forEachIndexed
            val x = left + (fret - startFret + 0.5f) * fretGap
            val y = top + string * stringGap
            drawCircle(accentColor, dotRadius, Offset(x, y))
            val finger = voicing.fingers.getOrElse(string) { 0 }
            if (finger > 0) {
                textPaint.color = Color.White.toArgb()
                textPaint.textSize = 10.dp.toPx()
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
