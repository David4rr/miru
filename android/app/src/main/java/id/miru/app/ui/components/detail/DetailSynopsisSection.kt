package id.miru.app.ui.components.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import id.miru.app.ui.theme.TextSecondary

@Composable
fun DetailSynopsisSection(
    synopsis: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isTranslating: Boolean,
    hasTranslation: Boolean,
    showingOriginal: Boolean,
    onTranslateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Sinopsis", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceVariant)
                    .clickable { onTranslateClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = CrunchyOrange)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Menerjemahkan...", fontSize = 11.sp, color = CrunchyOrange, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(imageVector = Icons.Default.Translate, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!hasTranslation) "Terjemahkan" else if (showingOriginal) "Lihat Terjemahan" else "Lihat Teks Asli",
                        fontSize = 11.sp,
                        color = CrunchyOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = synopsis,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 20.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.animateContentSize()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isExpanded) "Lebih Sedikit ▲" else "Selengkapnya ▼",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CrunchyOrange,
            modifier = Modifier.clickable { onToggleExpand() }
        )
    }
}
