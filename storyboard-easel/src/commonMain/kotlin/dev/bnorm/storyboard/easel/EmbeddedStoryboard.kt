package dev.bnorm.storyboard.easel

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.bnorm.storyboard.core.StoryboardState
import dev.bnorm.storyboard.ui.StoryboardScene

@Composable
fun EmbeddedStoryboard(storyboard: StoryboardState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val overlayState = remember(coroutineScope) { OverlayState(coroutineScope) }
    Box(modifier = modifier.onPointerMovePress(overlayState)) {
        StoryboardScene(storyboard)
        StoryboardOverlay(storyboard, overlayState)
    }
}
