package id.miru.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.DarkOled
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

data class NavFabTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val index: Int
)

val NAV_FAB_TABS = listOf(
    NavFabTab("Beranda", Icons.Filled.Home, Icons.Outlined.Home, 0),
    NavFabTab("Jelajah", Icons.Filled.Search, Icons.Outlined.Search, 1),
    NavFabTab("Koleksi", Icons.Filled.Bookmark, Icons.Filled.BookmarkBorder, 2),
    NavFabTab("Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings, 3)
)

/**
 * Floating Action Button Navigation with expandable menu.
 * Auto-hides when scrolling and reappears when idle.
 * Strictly under 200 LOC limit.
 */
@Composable
fun FloatingNavFab(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (!isVisible) isExpanded = false
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fabRotation"
    )

    Box(modifier = modifier) {
        // Outside scrim to dismiss menu
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = false }
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeIn(tween(220)),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it * 2 } + fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 20.dp)
            ) {
                // Expanded Menu Card
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(200)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 },
                    exit = fadeOut(tween(150)) + slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 }
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xF2141519),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .width(190.dp)
                            .border(1.dp, Brush.verticalGradient(listOf(Color(0x4DFF640A), Color(0x1AFFFFFF))), RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            NAV_FAB_TABS.forEach { tab ->
                                val isSelected = tab.index == selectedTab
                                val bgColor by animateColorAsState(if (isSelected) CrunchyrollOrange.copy(alpha = 0.16f) else Color.Transparent, label = "bg")
                                val tintColor by animateColorAsState(if (isSelected) CrunchyrollOrange else TextMuted, label = "tint")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bgColor)
                                        .clickable { onTabSelected(tab.index); isExpanded = false }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (isSelected) tab.selectedIcon else tab.unselectedIcon, tab.title, tint = tintColor, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(tab.title, color = if (isSelected) CrunchyrollOrange else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                                    if (isSelected) Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CrunchyrollOrange))
                                }
                            }
                        }
                    }
                }

                // Main Floating Action Button
                FloatingActionButton(
                    onClick = { isExpanded = !isExpanded },
                    containerColor = DarkOled,
                    contentColor = CrunchyrollOrange,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.5.dp, CrunchyrollOrange.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = CrunchyrollOrange)
                ) {
                    val currentTabObj = NAV_FAB_TABS.getOrNull(selectedTab) ?: NAV_FAB_TABS[0]
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else currentTabObj.selectedIcon,
                        contentDescription = "Menu Navigasi",
                        tint = CrunchyrollOrange,
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(rotationAngle)
                    )
                }
            }
        }
    }
}
