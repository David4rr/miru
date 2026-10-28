package id.miru.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyrollOrange
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
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fabRot"
    )

    val fabSrc = remember { MutableInteractionSource() }
    val isFabPressed by fabSrc.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (isFabPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fabPress"
    )

    Box(modifier = modifier) {
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
            enter = slideInVertically(spring(stiffness = Spring.StiffnessMedium)) { it * 2 } + fadeIn(tween(140)),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it * 2 } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 20.dp)
            ) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(120)) + scaleIn(spring(stiffness = Spring.StiffnessMedium), initialScale = 0.85f),
                    exit = fadeOut(tween(100)) + scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.85f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xF2141519),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .width(184.dp)
                            .border(1.dp, Color(0xFF282930), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            NAV_FAB_TABS.forEach { tab ->
                                val isSelected = tab.index == selectedTab
                                val bgColor by animateColorAsState(if (isSelected) Color(0x26FF640A) else Color.Transparent, label = "tabBg")
                                val tintColor by animateColorAsState(if (isSelected) CrunchyrollOrange else TextMuted, label = "tabTint")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bgColor)
                                        .clickable { onTabSelected(tab.index); isExpanded = false }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (isSelected) tab.selectedIcon else tab.unselectedIcon, tab.title, tint = tintColor, modifier = Modifier.size(19.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        tab.title,
                                        color = if (isSelected) CrunchyrollOrange else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF16171D))
                        .border(1.dp, if (isExpanded) CrunchyrollOrange else Color(0xFF2C2D35), RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = fabSrc,
                            indication = null,
                            onClick = { isExpanded = !isExpanded }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val currentTabObj = NAV_FAB_TABS.getOrNull(selectedTab) ?: NAV_FAB_TABS[0]
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else currentTabObj.selectedIcon,
                        contentDescription = "Navigasi",
                        tint = CrunchyrollOrange,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationAngle)
                    )
                }
            }
        }
    }
}
