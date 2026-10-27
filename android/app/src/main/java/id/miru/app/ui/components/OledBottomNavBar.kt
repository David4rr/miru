package id.miru.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyrollOrange

data class OledNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val index: Int
)

val OLED_NAV_TABS = listOf(
    OledNavTab("Beranda", Icons.Filled.Home, Icons.Outlined.Home, 0),
    OledNavTab("Cari", Icons.Filled.Search, Icons.Outlined.Search, 1),
    OledNavTab("Koleksi", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, 2),
    OledNavTab("Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings, 3)
)

/**
 * Grounded OLED bottom navigation bar without floating cut-offs or voids.
 * Adheres strictly to the < 200 LOC rule.
 */
@Composable
fun OledBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0E12))
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFF1E2028)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            OLED_NAV_TABS.forEach { tab ->
                val isSelected = selectedTab == tab.index
                val interactionSource = remember { MutableInteractionSource() }

                val animatedColor by animateColorAsState(
                    targetValue = if (isSelected) CrunchyrollOrange else Color(0xFF757A8B),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabColor"
                )

                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "tabScale"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onTabSelected(tab.index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.title,
                        tint = animatedColor,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(animatedScale)
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = tab.title,
                        color = animatedColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 2.dp)
                                .clip(CircleShape)
                                .background(CrunchyrollOrange)
                        )
                    }
                }
            }
        }
    }
}
