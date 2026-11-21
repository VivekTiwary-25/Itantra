package com.chmod777.itantra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chmod777.itantra.network.ItantraNetwork
import com.chmod777.itantra.ui.app.AppStore
import com.chmod777.itantra.ui.app.ProductApp
import com.chmod777.itantra.ui.app.Route
import com.chmod777.itantra.ui.theme.SIH_iTantraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opens the networking store and identity before any screen reads them.
        ItantraNetwork.init(this)
        AppStore.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        openFromNotification(intent)
        setContent {
            SIH_iTantraTheme(darkTheme = true, dynamicColor = false) {
                ProductApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromNotification(intent)
    }

    override fun onStart() {
        super.onStart()
        ItantraNetwork.appInForeground = true
    }

    override fun onStop() {
        ItantraNetwork.appInForeground = false
        super.onStop()
    }

    /** An urgent-arrival notification opens that stored message's detail, which marks it read. */
    private fun openFromNotification(intent: Intent?) {
        val bundleIdHex = intent?.getStringExtra(EXTRA_OPEN_BUNDLE) ?: return
        intent.removeExtra(EXTRA_OPEN_BUNDLE)
        AppStore.arrival = null
        AppStore.markRead(bundleIdHex)
        AppStore.home()
        AppStore.go(Route.Detail(bundleIdHex))
    }

    companion object {
        const val EXTRA_OPEN_BUNDLE = "com.chmod777.itantra.extra.OPEN_BUNDLE"
    }
}
