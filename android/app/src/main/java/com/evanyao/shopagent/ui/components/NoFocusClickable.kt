package com.evanyao.shopagent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalFocusManager

/**
 * 不请求焦点的 clickable，避免触发 verticalScroll 自动滚动。
 * 用于解决点击列表项时页面往上跳一下的问题。
 */
fun Modifier.noFocusClickable(
    onClick: () -> Unit
): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            focusManager.clearFocus()
            onClick()
        }
    )
}
