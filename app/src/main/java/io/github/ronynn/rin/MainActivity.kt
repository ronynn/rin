package io.github.ronynn.rin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("rin", 0)
        val themeIdx = prefs.getInt("theme", 0)

        var dynamicRin: RinColors? = null
        if (themeIdx == 7) {
            val extracted = DynamicTheme.extract(this)
            if (extracted != null) {
                val rin = DynamicTheme.toRinColors(extracted)
                dynamicRin = rin
                vm.dynamicColors = rin
                android.util.Log.d("RinDynamic", "Theme source: ${extracted.source}")
            } else if (!prefs.getBoolean("dynamic_warned", false)) {
                prefs.edit().putBoolean("dynamic_warned", true).apply()
                android.util.Log.w("RinDynamic", "Wallpaper theme unavailable — static preset used")
            }
        }

        val colors: RinColors = when {
            themeIdx == 7 && dynamicRin != null -> dynamicRin
            else -> Themes.getOrNull(themeIdx)?.second ?: Themes[0].second
        }
        window.setBackgroundDrawable(buildWindowBackground(colors))

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val currentColors = LocalRinColors.current

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = currentColors.isLight
                    isAppearanceLightNavigationBars = currentColors.isLight
                }
            }

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

                val needed = mutableListOf(mediaPerm)
                if (Build.VERSION.SDK_INT >= 33)
                    needed += Manifest.permission.POST_NOTIFICATIONS

                val missing = needed.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) vm.scanDevice() else permLauncher.launch(missing.toTypedArray())
            }

            RinTheme(vm.themeIndex, vm.fontIndex, vm.dynamicColors) {
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

    private fun buildWindowBackground(colors: RinColors): Drawable {
        if (!colors.isGlass || colors.blobs.isEmpty()) {
            return ColorDrawable(colors.bg.toArgb())
        }
        val minDim = minOf(resources.displayMetrics.widthPixels,
                          resources.displayMetrics.heightPixels).toFloat()
        val layers = mutableListOf<Drawable>()
        layers.add(ColorDrawable(colors.bg.toArgb()))
        colors.blobs.forEach { blob ->
            val g = GradientDrawable()
            g.gradientType = GradientDrawable.RADIAL_GRADIENT
            g.colors = intArrayOf(blob.color.toArgb(), 0)
            g.setGradientCenter(blob.cx, blob.cy)
            g.gradientRadius = blob.r * minDim
            layers.add(g)
        }
        return LayerDrawable(layers.toTypedArray())
    }
}