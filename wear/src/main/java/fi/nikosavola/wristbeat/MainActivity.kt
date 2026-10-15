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
    // Before the first frame, so the devices page opens showing the choice already made rather than
    // the off state it would render while the store was still being read.
    ActiveMode.load(this)
    enableEdgeToEdge()
    setContent { WristbeatTheme { BroadcastRoute() } }
  }
}
