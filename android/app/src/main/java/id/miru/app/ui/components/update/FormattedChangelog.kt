package id.miru.app.ui.components.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.TextPrimary
import id.miru.app.ui.theme.TextSecondary

@Composable
fun FormattedChangelogContent(
    body: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(body) { body.lines() }

    Column(modifier = modifier) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Spacer(modifier = Modifier.height(4.dp))
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### ").trim(),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("#### ") -> {
                    val sectionText = line.removePrefix("#### ").trim().trimEnd(':')
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(CrunchyrollOrange)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = sectionText, color = CrunchyrollOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val bulletText = line.substring(2).trim()
                    val regex = Regex("""^\*\*(.+?)\*\*:?\s*(.*)$""")
                    val match = regex.find(bulletText)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(CrunchyrollOrange.copy(alpha = 0.8f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (match != null) {
                            val title = match.groupValues[1]
                            val desc = match.groupValues[2]
                            Text(
                                text = buildAnnotatedString {
                                    pushStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                                    append(title)
                                    if (desc.isNotEmpty()) append(": ")
                                    pop()
                                    pushStyle(SpanStyle(color = TextSecondary))
                                    append(desc)
                                    pop()
                                },
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        } else {
                            Text(text = bulletText.replace("**", ""), color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
                else -> {
                    Text(text = line.replace("**", ""), color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}
