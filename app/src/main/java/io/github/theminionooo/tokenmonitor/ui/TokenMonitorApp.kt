package io.github.theminionooo.tokenmonitor.ui

import android.app.Activity
import android.provider.Settings
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.theminionooo.tokenmonitor.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import io.github.theminionooo.tokenmonitor.data.storage.TextScale
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.delay

internal fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun TokenMonitorApp(viewModel: DashboardViewModel) {
    val repositoryState by viewModel.hubState.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val form by viewModel.connectionForm.collectAsState()
    val displayOptions by viewModel.displayOptions.collectAsState()
    val serviceStatus by viewModel.serviceStatus.collectAsState()
    val discovery by viewModel.discovery.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val systemAnimationsEnabled = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f

    BackHandler(enabled = repositoryState.hasConnection && destination != DashboardDestination.Home) {
        viewModel.navigateBack()
    }

    val typography = remember(displayOptions.textScale) { tokenMonitorTypography(displayOptions.textScale.step) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = remember(displayOptions.themeCode, displayOptions.followSystemTheme, systemDark) {
        Palette.from(resolveInterfaceTheme(displayOptions.themeCode, displayOptions.followSystemTheme, systemDark))
    }
    // System bars and the window ground follow the theme, so a light theme gets dark status icons and no dark flash on rotation.
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            window.decorView.setBackgroundColor(palette.shell.toArgb())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }
    MaterialTheme(colorScheme = remember(palette) { tokenMonitorColors(palette) }, typography = typography) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalColorfulToolMarks provides displayOptions.colorfulToolMarks,
            LocalToolIcons provides displayOptions.showToolIcons,
            LocalInteractionMotion provides interactionMotionEnabled(displayOptions.reduceMotion, systemAnimationsEnabled),
            LocalNow provides rememberNow(),
            LocalSessionTitles provides displayOptions.showSessionTitles,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to palette.gradientTop,
                                0.38f to palette.shell,
                                1f to palette.gradientBottom,
                            ),
                        ),
                    ),
            ) {
                DashboardScaffold(
                    state = repositoryState,
                    destination = destination,
                    form = form,
                    displayOptions = displayOptions,
                    serviceStatus = serviceStatus,
                    onChoose = viewModel::choose,
                    onRefresh = viewModel::refresh,
                    onSaveConnection = viewModel::saveConnection,
                    onColorfulToolMarksChange = viewModel::setColorfulToolMarks,
                    onCompactTokenTotalChange = viewModel::setCompactTokenTotal,
                    onReduceMotionChange = viewModel::setReduceMotion,
                    onTextScaleChange = viewModel::setTextScale,
                    onThemeCodeChange = viewModel::setThemeCode,
                    onFollowSystemThemeChange = viewModel::setFollowSystemTheme,
                    onShowLiveIndicatorChange = viewModel::setShowLiveIndicator,
                    onShowToolIconsChange = viewModel::setShowToolIcons,
                    onRankingMetricChange = viewModel::setRankingMetric,
                    onShowLimitSourceChange = viewModel::setShowLimitSource,
                    onShowAccountEmailsChange = viewModel::setShowAccountEmails,
                    onShowSessionTitlesChange = viewModel::setShowSessionTitles,
                    onLimitBarMetricChange = viewModel::setLimitBarMetric,
                    onDefaultPeriodChange = viewModel::setDefaultPeriod,
                    onViewVisibleChange = viewModel::setViewVisible,
                    onHomeModuleVisibleChange = viewModel::setHomeModuleVisible,
                    onMoveView = viewModel::moveView,
                    onMoveHomeModule = viewModel::moveHomeModule,
                    onDisconnect = viewModel::disconnect,
                    onOpenServicePage = { url -> runCatching { uriHandler.openUri(url) } },
                    onOpenReleasePage = { runCatching { uriHandler.openUri(androidReleasesUrl) } },
                    discovery = discovery,
                    onFindHomeHub = viewModel::findHomeHub,
                )
            }
        }
    }
}

@Composable
internal fun DashboardScaffold(
    state: HubRepositoryState,
    destination: DashboardDestination,
    form: ConnectionFormState,
    displayOptions: DisplayOptions,
    serviceStatus: ServiceStatusState,
    onChoose: (DashboardDestination) -> Unit,
    onRefresh: () -> Unit,
    onSaveConnection: (String, String, String, Boolean) -> Unit,
    onColorfulToolMarksChange: (Boolean) -> Unit,
    onCompactTokenTotalChange: (Boolean) -> Unit,
    onReduceMotionChange: (ReduceMotionMode) -> Unit,
    onTextScaleChange: (TextScale) -> Unit,
    onThemeCodeChange: (String?) -> Unit,
    onFollowSystemThemeChange: (Boolean) -> Unit,
    onShowLiveIndicatorChange: (Boolean) -> Unit,
    onShowToolIconsChange: (Boolean) -> Unit,
    onRankingMetricChange: (RankingMetric) -> Unit,
    onShowLimitSourceChange: (Boolean) -> Unit,
    onShowAccountEmailsChange: (Boolean) -> Unit,
    onLimitBarMetricChange: (LimitBarMetric) -> Unit,
    onDefaultPeriodChange: (String) -> Unit,
    onViewVisibleChange: (String, Boolean) -> Unit,
    onHomeModuleVisibleChange: (String, Boolean) -> Unit,
    onMoveView: (String, Int) -> Unit,
    onMoveHomeModule: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onOpenServicePage: (String) -> Unit,
    onOpenReleasePage: () -> Unit,
    discovery: HubDiscoveryState,
    onFindHomeHub: () -> Unit,
    onShowSessionTitlesChange: (Boolean) -> Unit = {},
) {
    var periodName by rememberSaveable { mutableStateOf(displayOptions.defaultPeriod) }
    var homeReturnVisible by rememberSaveable { mutableStateOf(false) }
    // Destinations have separate compositions during AnimatedContent transitions.
    var modelFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val motionEnabled = LocalInteractionMotion.current
    val period = DashboardPeriod.entries.firstOrNull { it.name == periodName } ?: DashboardPeriod.Today
    val settingsOpen = destination == DashboardDestination.Settings
    LaunchedEffect(displayOptions.defaultPeriod) {
        periodName = displayOptions.defaultPeriod
    }
    val chooseDestination: (DashboardDestination) -> Unit = { next ->
        modelFilter = null
        homeReturnVisible = destination == DashboardDestination.Home && next != DashboardDestination.Home
        if (next == DashboardDestination.Home || next == DashboardDestination.Settings) homeReturnVisible = false
        onChoose(next)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // The first-run welcome page owns the whole screen; there is no Home to go back to yet.
            if (!(settingsOpen && !state.hasConnection)) {
                DesktopHeader(
                    state = state,
                    settingsOpen = settingsOpen,
                    showLiveIndicator = displayOptions.showLiveIndicator,
                    period = period,
                    onPeriodChange = { periodName = it.name },
                    onGoHome = { chooseDestination(DashboardDestination.Home) },
                )
            }
        },
        bottomBar = {
            if (!settingsOpen) {
                DesktopFooter(
                    destination = destination,
                    state = state,
                    displayOptions = displayOptions,
                    onChoose = chooseDestination,
                    onRefresh = onRefresh,
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = { (fadeIn(tween(if (motionEnabled) 200 else 0, easing = DesktopEaseOut)) + slideInVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)) { it / 28 }) togetherWith fadeOut(tween(if (motionEnabled) 110 else 0)) },
            label = "dashboard view",
        ) { visibleDestination ->
        if (visibleDestination == DashboardDestination.Settings) {
            ConnectionScreen(
                modifier = Modifier.padding(padding),
                state = state,
                form = form,
                displayOptions = displayOptions,
                onSaveConnection = onSaveConnection,
                onColorfulToolMarksChange = onColorfulToolMarksChange,
                onCompactTokenTotalChange = onCompactTokenTotalChange,
                onReduceMotionChange = onReduceMotionChange,
                onTextScaleChange = onTextScaleChange,
                onThemeCodeChange = onThemeCodeChange,
                onFollowSystemThemeChange = onFollowSystemThemeChange,
                onShowLiveIndicatorChange = onShowLiveIndicatorChange,
                onShowToolIconsChange = onShowToolIconsChange,
                onRankingMetricChange = onRankingMetricChange,
                onShowLimitSourceChange = onShowLimitSourceChange,
                onShowAccountEmailsChange = onShowAccountEmailsChange,
                onShowSessionTitlesChange = onShowSessionTitlesChange,
                onLimitBarMetricChange = onLimitBarMetricChange,
                onDefaultPeriodChange = onDefaultPeriodChange,
                onViewVisibleChange = onViewVisibleChange,
                onHomeModuleVisibleChange = onHomeModuleVisibleChange,
                onMoveView = onMoveView,
                onMoveHomeModule = onMoveHomeModule,
                onDisconnect = onDisconnect,
                onOpenReleasePage = onOpenReleasePage,
                discovery = discovery,
                onFindHomeHub = onFindHomeHub,
            )
        } else {
            DashboardContent(
                modifier = Modifier.padding(padding),
                state = state,
                destination = visibleDestination,
                period = period,
                onChoose = chooseDestination,
                onRefresh = onRefresh,
                homeReturnVisible = homeReturnVisible,
                modelFilter = modelFilter.takeIf { visibleDestination == DashboardDestination.Models },
                onClearModelFilter = { modelFilter = null },
                onOpenToolModels = { tool ->
                    chooseDestination(DashboardDestination.Models)
                    modelFilter = tool
                },
                serviceStatus = serviceStatus,
                displayOptions = displayOptions,
                onOpenServicePage = onOpenServicePage,
            )
        }
        }
    }
}

internal const val androidReleasesUrl = "https://github.com/The-Minion-oOo/token-monitor-android/releases"
