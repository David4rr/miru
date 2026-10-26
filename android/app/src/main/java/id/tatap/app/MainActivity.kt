package id.tatap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import id.tatap.app.ui.theme.CrunchyrollOrange
import id.tatap.app.ui.theme.DarkOled
import id.tatap.app.ui.theme.SurfaceDark
import id.tatap.app.ui.theme.TatapTheme
import id.tatap.app.ui.theme.TextMuted
import id.tatap.app.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TatapTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Browse", "Calendar", "Library")
    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.Explore,
        Icons.Default.CalendarToday,
        Icons.Default.VideoLibrary
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextPrimary
            ) {
                items.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = title) },
                        label = { Text(title) },
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CrunchyrollOrange,
                            selectedTextColor = CrunchyrollOrange,
                            indicatorColor = SurfaceDark,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkOled)
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tatap • Crunchyroll Style (${items[selectedIndex]})",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
    }
}
