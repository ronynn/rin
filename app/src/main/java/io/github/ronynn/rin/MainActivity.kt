package io.github.ronynn.rin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Match window background to stored theme so no black flash before Compose draws
        val prefs = getSharedPreferences("rin", 0)
        val themeIdx = prefs.getInt("theme", 0)
        val colors = Themes.getOrNull(themeIdx)?.second ?: Themes[0].second
        window.setBackgroundDrawable(ColorDrawable(colors.bg.toArgb()))

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val mediaOk = result[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                        result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                if (mediaOk) vm.scanDevice() else vm.status = "MEDIA PERMISSION DENIED"
            }

            LaunchedEffect(Unit) {
                vm.connect()
                val mediaPerm = if (Build.VERSION.SDK_INT >= 33)
                    Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE

                val needed = mutableListOf(mediaPerm, Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33)
                    needed += Manifest.permission.POST_NOTIFICATIONS

                val missing = needed.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) vm.scanDevice()
                else permLauncher.launch(missing.toTypedArray())
            }

            RinTheme(vm.themeIndex) {
                PlayerScreen(vm)
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = extractUri(intent) ?: return
        vm.playExternalUri(uri)
    }

    @Suppress("DEPRECATION")
    private fun extractUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else
            intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
        else -> null
    }
}