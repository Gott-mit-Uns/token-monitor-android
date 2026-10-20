package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Original 34x30 utility chrome inside an independent Android touch target. */
@Composable internal fun DesktopFooterAction(refresh:Boolean,label:String,tag:String,enabled:Boolean=true,feedback:RefreshFeedback=RefreshFeedback.NONE,click:()->Unit) {
    val shape=RoundedCornerShape(7.dp)
    Box(Modifier.size(48.dp).clickable(enabled=enabled,role=Role.Button,onClick=click).testTag(tag).semantics {contentDescription=label},contentAlignment=Alignment.Center) {
        Box(Modifier.size(34.dp,30.dp).testTag("$tag-visual").alpha(if(enabled) 1f else .4f)
            .border(1.dp,desktopEditLine(),shape).background(LocalDesktopOverlayColor.current.copy(alpha=.049f),shape),contentAlignment=Alignment.Center) {
            when {
                refresh && feedback==RefreshFeedback.SUCCESS -> Text("✓",style=quotaTextStyle(12,true),color=LocalDesktopSuccessColor.current)
                refresh && feedback==RefreshFeedback.ERROR -> Text("!",style=quotaTextStyle(12,true),color=MaterialTheme.colorScheme.error)
                else -> Icon(painterResource(if(refresh) R.drawable.desktop_refresh else R.drawable.desktop_settings),null,
                    if(refresh) Modifier.size(8.3.dp,8.dp) else Modifier.size(13.dp),tint=MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

internal enum class RefreshFeedback { NONE, SUCCESS, ERROR }
