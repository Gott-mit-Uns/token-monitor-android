package dev.bing.tokenmonitor

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.text.NumberFormat

class MainActivity : ComponentActivity() {
    private val model: HubViewModel by viewModels()
    private val localAllowed = mutableStateOf(true)
    private val localPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { localAllowed.value = HubLocalPermission.granted(this) }
    private val networks by lazy { HubNetworkObserver(this, model::updateNetworkScope) }
    override fun onStart() { super.onStart(); localAllowed.value = HubLocalPermission.granted(this); networks.start(); model.setForeground(true) }
    override fun onStop() { networks.stop(); model.setForeground(false); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubApp(model, localAllowed.value,
                requestLocal = { localPermission.launch(HubLocalPermission.NAME) },
                openLocalSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) })
        }
    }
}

@Composable
private fun HubApp(model: HubViewModel, localAllowed: Boolean, requestLocal: () -> Unit, openLocalSettings: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val preferences by model.display.collectAsStateWithLifecycle()
    val startup by model.startup.collectAsStateWithLifecycle()
    key(model.startupSessionId) {
        HubScreen(state,model,localAllowed,requestLocal,preferences,model::changeDisplay,openLocalSettings,startup)
    }

}

@Composable
fun HubScreen(state: HubState, model: HubUiActions, localAllowed: Boolean = true, requestLocal: (() -> Unit)? = null, preferences: DisplayPreferences = DisplayPreferences(), updateDisplay: ((DisplayChange) -> Unit)? = null, openLocalSettings: (() -> Unit)? = null, startup: HubStartup? = null) {
    val preview=remember {AppearancePreviewState()}
    val language=LocalDesktopBaseLanguage.current ?: preferences.language
    val inheritedDark=LocalDesktopBaseDark.current
    val systemDark=when(preferences.theme) {ThemeChoice.DARK->true;ThemeChoice.LIGHT->false;else->inheritedDark ?: isSystemInDarkTheme()}
    val effective=preview.rendered(preferences.appearance)
    val dark=!DesktopAppearance.light(DesktopAppearance.palette(effective.colors,systemDark).getValue("bg"))
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity=(LocalActivity.current as? ComponentActivity) ?: (lifecycleOwner as? ComponentActivity)
    val backgroundOwner=lifecycleOwner as? androidx.lifecycle.ViewModelStoreOwner
    val backgroundModel=LocalBackgroundImageModel.current ?: backgroundOwner?.let { owner -> remember(owner) { androidx.lifecycle.ViewModelProvider(owner)[BackgroundImageModel::class.java] } }
    val imageRegistryOwner=androidx.activity.compose.LocalActivityResultRegistryOwner.current ?: requireNotNull(lifecycleOwner as? androidx.activity.result.ActivityResultRegistryOwner)
    val backgroundState=backgroundModel?.state?.collectAsStateWithLifecycle()?.value ?: BackgroundImageState(busy=false)
    SideEffect {activity?.enableEdgeToEdge(
        statusBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT) {dark},
        navigationBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT) {dark})}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) preview.clear()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);preview.clear()}
    }
    TokenTheme(systemDark,language,effective) {
    CompositionLocalProvider(LocalAppearancePreview provides preview,LocalAppearancePreviewBase provides AppearancePreviewBase(systemDark,language,preferences.appearance), LocalBackgroundImageModel provides backgroundModel, LocalBackgroundImageState provides backgroundState, androidx.activity.compose.LocalActivityResultRegistryOwner provides imageRegistryOwner) {
    val currency = androidx.compose.runtime.remember(preferences.currency, preferences.currencyRates) { UsageCurrency(preferences.currency, preferences.currencyRates) }
    val strings = androidx.compose.ui.platform.LocalResources.current
    val costLanguage = UsageCostLanguage(strings.configuration.locales[0], strings.getString(R.string.usage_unpriced_label))
    androidx.compose.runtime.CompositionLocalProvider(LocalUsageCurrency provides currency, LocalUsageCostLanguage provides costLanguage,
        LocalDesktopVendorPalette provides DesktopVendorPalette.resolve(effective.vendorColors),
        LocalDesktopToolIcons provides preferences.appearance.showToolIcons, LocalLimitDisplay provides preferences.limits,LocalMainDisplay provides preferences.main) {
        if (startup == HubStartup.LOADING) HubStartupIcon()
        else DesktopHubScreen(state, model, localAllowed, requestLocal, preferences, updateDisplay, openLocalSettings,
            initialPage = startup?.let { if (it == HubStartup.SETTINGS) HubPage.SETTINGS else HubPage.HOME })
    }
    }
    }
}
