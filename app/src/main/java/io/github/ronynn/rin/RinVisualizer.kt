package io.github.ronynn.rin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RinVisualizer(
    wave: State<FloatArray>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalRinColors.current
    Canvas(modifier.background(c.bg).border(2.dp, c.border)) {
        val mid = size.height / 2f
        val w = wave.value

        if (!isPlaying || w.isEmpty()) {
            drawLine(
                c.fg,
                Offset(0f, mid),
                Offset(size.width, mid),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val step = size.width / w.size
        val main = Path().apply {
            moveTo(0f, mid + w[0] * mid * 0.95f)
            for (i in 1 until w.size) lineTo(i * step, mid + w[i] * mid * 0.95f)
        }
        val refl = Path().apply {
            moveTo(0f, size.height - (mid + w[0] * mid * 0.95f))
            for (i in 1 until w.size) lineTo(i * step, size.height - (mid + w[i] * mid * 0.95f))
        }

        val cap = StrokeCap.Round
        val join = StrokeJoin.Round

        drawPath(refl, c.accent.copy(alpha = 0.22f), style = Stroke(2.dp.toPx(), cap = cap, join = join))
        drawPath(main, c.accent.copy(alpha = 0.12f), style = Stroke(10.dp.toPx(), cap = cap, join = join))
        drawPath(main, c.accent.copy(alpha = 0.20f), style = Stroke(6.dp.toPx(), cap = cap, join = join))
        drawPath(main, c.fg, style = Stroke(2.5.dp.toPx(), cap = cap, join = join))
    }
}