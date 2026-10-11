package fi.nikosavola.wristbeat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fi.nikosavola.wristbeat.ui.BroadcastRoute
import fi.nikosavola.wristbeat.ui.theme.WristbeatTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { WristbeatTheme { BroadcastRoute() } }
  }
}
