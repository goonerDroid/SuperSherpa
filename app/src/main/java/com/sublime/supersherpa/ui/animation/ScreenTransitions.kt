package com.sublime.supersherpa.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.sublime.supersherpa.feature.transcription.AppScreen

private const val SCREEN_TWEEN_MS = 180
private const val SCREEN_STAGGER_RATIO = 6

fun animatedScreenTransition(
    initialState: AppScreen,
    targetState: AppScreen,
): ContentTransform {
    val direction = when {
        targetState.ordinal > initialState.ordinal -> 1
        targetState.ordinal < initialState.ordinal -> -1
        else -> 1
    }
    val enterTransition = slideInVertically(
        initialOffsetY = { fullHeight -> (fullHeight / SCREEN_STAGGER_RATIO) * direction },
        animationSpec = tween(SCREEN_TWEEN_MS),
    ) + fadeIn(animationSpec = tween(SCREEN_TWEEN_MS))

    val exitTransition = slideOutVertically(
        targetOffsetY = { fullHeight -> (-fullHeight / SCREEN_STAGGER_RATIO) * direction },
        animationSpec = tween(SCREEN_TWEEN_MS),
    ) + fadeOut(animationSpec = tween(SCREEN_TWEEN_MS))

    return enterTransition togetherWith exitTransition
}
