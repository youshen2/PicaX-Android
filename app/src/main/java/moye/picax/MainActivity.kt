package moye.picax

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import moye.picax.ui.PicaXApp

class MainActivity : FragmentActivity() {
    private val clipboardLink = mutableStateOf<String?>(null)
    private var resumedOnce = false
    private val incomingLink = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingLink.value = intent?.dataString
        val app = application as PicaXApplication
        setContent { PicaXApp(app.store, app.repository, incomingLink.value, { incomingLink.value = null }, clipboardLink.value, { clipboardLink.value = null }) }
    }
    override fun onResume() {
        super.onResume()
        val app = application as PicaXApplication
        val check = app.store.bool("behavior.clipboard") && (!resumedOnce || !app.store.bool("behavior.clipboardLaunchOnly", false))
        resumedOnce = true
        if(check) window.decorView.post {
            val manager = getSystemService(android.content.ClipboardManager::class.java)
            val value = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty().trim()
            if(value.isNotEmpty() && value != app.store.setting("behavior.lastClipboard") && runCatching { app.repository.fromLink(value) }.isSuccess) {
                app.store.set("behavior.lastClipboard", value); clipboardLink.value = value
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingLink.value = intent.dataString
    }
}
