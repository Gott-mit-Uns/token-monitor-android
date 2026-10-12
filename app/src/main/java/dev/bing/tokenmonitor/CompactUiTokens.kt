package dev.bing.tokenmonitor

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared semantic density contract. Natural text may grow; touch bounds never shrink. */
internal object CompactUiTokens {
    val ChartHeightPrimary = 160.dp
    val ChartHeightSecondary = 120.dp
    val DetailRowMinHeight = 36.dp
    val InteractiveMinHeight = 48.dp
    val SectionSpacing = 8.dp
    val CardContentPadding = 8.dp
    val CardHorizontalPadding = 10.dp
    val ChartTitleSpacing = 4.dp
    val DetailRowPadding = 6.dp

    /** Extra axis room follows actual Android sp conversion, including non-linear scaling. */
    @Composable fun chartHeight(secondary: Boolean = false): Dp {
        val actualAxisText = with(LocalDensity.current) { (10f * LocalDesktopTextScale.current).sp.toDp() }
        val preferred = if (secondary) ChartHeightSecondary else ChartHeightPrimary
        return preferred + (actualAxisText - 10.dp).coerceAtLeast(0.dp) * 8
    }
}
