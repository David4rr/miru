package id.miru.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyrollOrange

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)

val NAV_TABS = listOf(
    NavTabItem("Beranda", Icons.Default.Home, 0),
    NavTabItem("Cari", Icons.Default.Search, 1),
    NavTabItem("Koleksi", Icons.Default.VideoLibrary, 2),
    NavTabItem("Pengaturan", Icons.Default.Settings, 3)
)

/**
 * Ultra-modern, compact floating dock navigation bar.
 * Replaces standard bulky Material 3 NavigationBar with a sleek OLED-styled pill dock.
 */
@Composable
fun ModernFloatingNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(29.dp), ambientColor = Color.Black, spotColor = CrunchyrollOrange.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(29.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xF01C1D24),
                            Color(0xF5131419)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF2E303B),
                    shape = RoundedCornerShape(29.dp)
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NAV_TABS.forEach { tab ->
                val isSelected = selectedTab == tab.index
                val interactionSource = remember { MutableInteractionSource() }

                val animatedBgColor by animateColorAsState(
                    targetValue = if (isSelected) CrunchyrollOrange.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabBg"
                )

                val animatedIconColor by animateColorAsState(
                    targetValue = if (isSelected) CrunchyrollOrange else Color.White.copy(alpha = 0.45f),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabIcon"
                )

                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(animatedBgColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, CrunchyrollOrange.copy(alpha = 0.35f), RoundedCornerShape(21.dp))
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onTabSelected(tab.index)
                        }
                        .padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 8.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = animatedIconColor,
                        modifier = Modifier.size(if (isSelected) 20.dp else 22.dp)
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            color = CrunchyrollOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
