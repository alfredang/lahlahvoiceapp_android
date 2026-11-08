package sg.com.tertiarycourses.lahlahvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import sg.com.tertiarycourses.lahlahvoice.core.Store
import sg.com.tertiarycourses.lahlahvoice.ui.AboutScreen
import sg.com.tertiarycourses.lahlahvoice.ui.Brand
import sg.com.tertiarycourses.lahlahvoice.ui.DictateScreen
import sg.com.tertiarycourses.lahlahvoice.ui.LahLahTheme
import sg.com.tertiarycourses.lahlahvoice.ui.PersonalizeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LahLahTheme { App() } }
    }
}

@Composable
private fun App() {
    val store = Store.get(LocalContext.current)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Dictate" to Icons.Filled.Mic, "Personalize" to Icons.Filled.Person, "About" to Icons.Filled.Info)
    Scaffold(
        containerColor = Brand.sand,
        bottomBar = {
            NavigationBar(containerColor = Brand.panel) {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand.ember, selectedTextColor = Brand.ember,
                            indicatorColor = Brand.emberSoft,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> DictateScreen(store, modifier)
            1 -> PersonalizeScreen(store, modifier)
            else -> AboutScreen(modifier)
        }
    }
}
