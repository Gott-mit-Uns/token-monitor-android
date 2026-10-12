package dev.bing.tokenmonitor

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Dns
import okhttp3.tls.HandshakeCertificates
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Actual peer TCP/TLS gating. Test root/controlled DNS are injected only into this test client. */
@SdkSuppress(minSdkVersion = 37)
class LocalHttpsPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun privateHttpsLiteralAndResolvedDomainRespectPermissionAndHostname() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val app = context.applicationContext as Application
        assertFalse("HTTPS check starts denied", HubLocalPermission.granted(context))
        val port = args.getString("fixturePort")?.toIntOrNull() ?: error("Synthetic TLS fixture port required")
        require(port in 1024..65535)
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(
            Base64.decode(args.getString("fixturePublicCertificate") ?: error("Public test certificate required"), Base64.DEFAULT))) as X509Certificate
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate).build()
        val host = "permission-fixture.test"
        val resolved = AtomicInteger()
        val client = HubViewModel.defaultClient().newBuilder()
            .sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .dns { name -> if (name in setOf(host, "wrong-host.test")) {
                resolved.incrementAndGet(); listOf(InetAddress.getByName("10.0.2.2"))
            } else Dns.SYSTEM.lookup(name) }
            .connectTimeout(1, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
        val store = HubStore(app, "test-https-permission-${UUID.randomUUID()}.bin")
        val generated = UUID.randomUUID().toString()
        lateinit var model: HubViewModel
        instrumentation.runOnMainSync { model = HubViewModel(app, store, client); model.setForeground(true) }
        fun connect(address: String) {
            instrumentation.runOnMainSync { model.connect(address, generated, "") }
            compose.waitUntil(10000) { !model.state.value.loading }
        }
        fun button(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.viewIdResourceName?.endsWith(":id/permission_allow_button") == true) return node
            for (index in 0 until node.childCount) button(node.getChild(index))?.let { return it }
            return null
        }
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        val previousFlags = service.flags
        service.flags = previousFlags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = service
        try {
            compose.waitUntil(10000) { !model.state.value.loading }
            val base = "https://$host:$port"
            if (args.getString("permissionPhase") == "denied") {
                connect(base)
                assertTrue("Controlled private DNS must actually run", resolved.get() > 0)
                assertEquals(R.string.error_connection_local_check, model.state.value.error)
                assertFalse(model.state.value.live)
                connect("https://10.0.2.2:$port")
                assertEquals(R.string.error_connection_local_check, model.state.value.error)
                assertFalse(model.state.value.live)
                assertNull(store.load())
            } else {
                val strings = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("zh")) }).resources
                compose.onNodeWithTag("hub-content").performScrollToNode(hasText(strings.getString(R.string.allow_local_access)))
                compose.onNodeWithText(strings.getString(R.string.allow_local_access)).performClick()
                var allow: AccessibilityNodeInfo? = null
                compose.waitUntil(10000) { allow = button(automation.rootInActiveWindow); allow != null }
                assertTrue(allow!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.waitUntil(10000) { HubLocalPermission.granted(context) }
                for (address in listOf(base, "https://10.0.2.2:$port")) {
                    connect(address)
                    compose.waitUntil(10000) { model.state.value.live }
                    assertEquals(123L, model.state.value.snapshot?.periods?.get("today")?.tokens)
                    assertNotNull(store.load())
                }
                connect("https://wrong-host.test:$port")
                assertEquals(R.string.error_connection, model.state.value.error)
                assertFalse(model.state.value.live)
                // Host verifies both SSEs close and invalid hostname sends no HTTP or Authorization.
            }
        } finally {
            instrumentation.runOnMainSync { model.setForeground(false); model.forget() }
            compose.waitUntil(10000) { !model.state.value.loading }
            store.clear()
            service.flags = previousFlags
            automation.serviceInfo = service
        }
    }
}
