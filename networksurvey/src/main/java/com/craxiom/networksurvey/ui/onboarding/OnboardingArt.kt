package com.craxiom.networksurvey.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The first card's picture: a walked path past two towers to the current fix, in the theme's
 * colors. Drawn on a 300 by 200 grid and scaled to fit; decorative, so it has no description.
 */
@Composable
fun OnboardingArt(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val towerFill = MaterialTheme.colorScheme.surface
    val fix = NsTheme.colors.status.success.content
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ART_HEIGHT)
            .clip(NsTheme.shapes.hero)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(width = ART_WIDTH, height = ART_INNER_HEIGHT)) {
            val unit = size.width / GRID_WIDTH
            val path = Path().apply {
                moveTo(40 * unit, 150 * unit)
                cubicTo(80 * unit, 110 * unit, 120 * unit, 140 * unit, 160 * unit, 100 * unit)
                cubicTo(200 * unit, 60 * unit, 220 * unit, 60 * unit, 260 * unit, 50 * unit)
            }
            drawPath(
                path,
                color = ink,
                style = Stroke(
                    width = 4 * unit,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * unit, 10 * unit)),
                ),
            )
            tower(Offset(70 * unit, 70 * unit), unit, ink, towerFill)
            tower(Offset(220 * unit, 120 * unit), unit, ink, towerFill)
            drawCircle(color = towerFill, radius = 11 * unit, center = Offset(260 * unit, 50 * unit))
            drawCircle(color = fix, radius = 8 * unit, center = Offset(260 * unit, 50 * unit))
        }
    }
}

private fun DrawScope.tower(center: Offset, unit: Float, ink: Color, fill: Color) {
    drawCircle(color = fill, radius = 16 * unit, center = center)
    drawCircle(color = ink, radius = 16 * unit, center = center, style = Stroke(width = 2 * unit))
    val mast = Path().apply {
        moveTo(center.x - 6 * unit, center.y + 8 * unit)
        lineTo(center.x, center.y - 8 * unit)
        lineTo(center.x + 6 * unit, center.y + 8 * unit)
        moveTo(center.x - 4 * unit, center.y + 3 * unit)
        lineTo(center.x + 4 * unit, center.y + 3 * unit)
    }
    drawPath(mast, color = ink, style = Stroke(width = 2 * unit, cap = StrokeCap.Round))
}

private const val GRID_WIDTH = 300f
private val ART_HEIGHT = 200.dp
private val ART_WIDTH = 270.dp
private val ART_INNER_HEIGHT = 180.dp
