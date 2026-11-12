package com.bamah.reelstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun uiColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

@Composable
fun ChallengeCardPreview(
    config: ChallengeConfig,
    word: WordItem,
    index: Int,
    total: Int,
    answerPhase: Boolean,
    questionProgress: Float,
    modifier: Modifier = Modifier
) {
    val accent = uiColor(config.accentColorHex, Color(0xFFE9A17B))
    val bg = uiColor(config.mainColorHex, Color(0xFF191817))
    val ink = uiColor(config.cardTextHex, Color(0xFFF2F0ED))
    val muted = ink.copy(alpha = 0.76f)
    Box(modifier = modifier.fillMaxWidth().aspectRatio(2.27f)) {
      Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(bg)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (config.showChallengeLabel && config.title.isNotBlank()) {
                Box(
                    Modifier.clip(RoundedCornerShape(7.dp))
                        .background(accent.copy(alpha = config.labelBackgroundOpacity / 100f))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(config.title.uppercase(), color = accent, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (config.showCta && config.ctaText.isNotBlank()) {
                Spacer(Modifier.width(9.dp))
                Text(config.ctaText.uppercase(), color = muted, fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
            } else Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
            Text("${(index + 1).toString().padStart(2, '0')} / ${total.toString().padStart(2, '0')}",
                color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.word, color = if (answerPhase) accent else ink, fontSize = 38.sp,
                lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = true))
            if (config.showIcon && word.emoji.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(43.dp).clip(RoundedCornerShape(10.dp))
                    .background(ink.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Text(word.emoji, fontSize = 24.sp, maxLines = 1)
                }
            }
        }
        if (word.definition.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(word.definition, color = muted, fontSize = 13.sp, lineHeight = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (config.showTimer) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp))
                .background(accent.copy(alpha = 0.16f))) {
                Box(Modifier.fillMaxWidth(if (answerPhase) 1f else questionProgress.coerceIn(0f, 1f))
                    .fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(accent))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (answerPhase) {
                if (word.pronunciation.isNotBlank()) {
                    val annotated = buildAnnotatedString {
                        word.pronunciation.forEach { ch ->
                            if (ch.isUpperCase()) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                                append(ch)
                            } else append(ch)
                        }
                    }
                    Text(annotated, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
            } else {
                Text("YOUR TURN", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
            Text("${(index + 1).toString().padStart(2, '0')} OF ${total.toString().padStart(2, '0')}",
                color = muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
        if (config.showBottomProgress) {
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(total.coerceAtMost(30)) { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i <= index) accent else ink.copy(alpha = 0.16f)))
                }
            }
        }
      }
      if (config.showTopTab) {
          Box(Modifier.align(Alignment.TopCenter).offset(x = (-20).dp, y = (-1).dp)
              .width(54.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(accent))
      }
    }
}
