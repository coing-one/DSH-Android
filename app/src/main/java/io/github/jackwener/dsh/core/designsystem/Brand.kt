package io.github.jackwener.dsh.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jackwener.dsh.R

/** The DeepSeek whale (dsh FishLogo), drawn in the current text color. */
@Composable
fun Whale(modifier: Modifier = Modifier, width: Dp = 34.dp, tint: Color = Dsh.colors.labelPrimary) =
    Icon(painterResource(R.drawable.dsw_whale), null, modifier.size(width, width * 17.04f / 23.16f), tint = tint)

/**
 * Running indicator: three pulsing dots (the old whale sprite is gone with the rebrand).
 * Static when reduced motion is on.
 */
@Composable
fun SwimmingWhale(width: Dp = 14.dp, tint: Color = Dsh.colors.deepDiving) {
    if (LocalReducedMotion.current) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { Box(Modifier.size(width / 3).clip(CircleShape).background(tint)) } }
        return
    }
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 160)), label = "dot$i")
            Box(Modifier.size(width / 3).alpha(a).clip(CircleShape).background(tint))
        }
    }
}

/** coing wordmark: plain text, no upstream animal mark. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text("coing", modifier, style = DshType.l20.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight(600), letterSpacing = (-0.3).sp), color = Dsh.colors.labelPrimary)
}

/** Small square brand badge (about row, empty states): blue squircle with a white "c". */
@Composable
fun CoingBadge(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(Dsh.colors.business),
        contentAlignment = Alignment.Center,
    ) {
        Text("c", style = TextStyle(fontSize = (size.value * 0.62f).sp, lineHeight = (size.value * 0.62f).sp, fontWeight = FontWeight(700)), color = Color.White)
    }
}
