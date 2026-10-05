package io.github.theminionooo.tokenmonitor.widget

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import io.github.theminionooo.tokenmonitor.MainActivity
import io.github.theminionooo.tokenmonitor.data.network.LocalNetworkAccess
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore

/** A user-visible activity supplies the foreground-start gesture and first-use permission prompt. */
class WidgetControlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = requestedAction()
        if (action == ACTION_OPEN) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        } else if (action == WidgetLiveService.ACTION_LIVE && WidgetRuntime.session.enabled) {
            WidgetLiveService.stop(this)
            finish()
        } else if (action !in listOf(WidgetLiveService.ACTION_LIVE, WidgetLiveService.ACTION_REFRESH)) {
            finish()
        } else if (savedInstanceState == null) requestAccessAndStart()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestAccessAndStart()
        else {
            val message = if (requestCode == 2) LocalNetworkAccess.deniedMessage
                else "Allow notifications to use widget updates with a visible Stop control."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun requestAccessAndStart() {
        val pairing = SecureConnectionStore(this).read()
        when {
            pairing != null && LocalNetworkAccess.allowedRoutes(pairing, null, false).isEmpty() && !LocalNetworkAccess.granted(this) ->
                requestPermissions(arrayOf(LocalNetworkAccess.permission), 2)
            Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else -> startUpdates()
        }
    }

    private fun startUpdates() {
        try {
            startForegroundService(Intent(this, WidgetLiveService::class.java).setAction(requestedAction()))
        } catch (_: RuntimeException) {
            Toast.makeText(this, "Android could not start widget updates. Tap again to retry.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun requestedAction(): String? = intent.action

    companion object {
        internal const val ACTION_OPEN = "widget.OPEN"
    }
}

class WidgetStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = WidgetLiveService.stop(context)
}
