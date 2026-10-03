package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.theminionooo.tokenmonitor.BuildConfig

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun AppUpdatesPanel(onOpenReleasePage: () -> Unit) {
    StatusLine("Installed", BuildConfig.VERSION_NAME)
    StatusLine("Edition", "Cloudflare compatible")
    Text(
        "This independent edition uses a separate package and signing key. Automatic upstream update checks and APK installation are disabled. Install future updates supplied for this edition; upstream APKs do not update it.",
        color = Muted,
        style = MaterialTheme.typography.bodySmall,
    )
}
