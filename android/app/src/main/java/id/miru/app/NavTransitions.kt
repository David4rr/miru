package id.miru.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

private val detailScaleSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
private val fadeSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)

fun AnimatedContentTransitionScope<Int>.screenModeTransition(): ContentTransform {
    return when {
        targetState == 2 -> {
            (scaleIn(initialScale = 0.90f, animationSpec = detailScaleSpring) + fadeIn(fadeSpring)) togetherWith
                (scaleOut(targetScale = 1.08f, animationSpec = detailScaleSpring) + fadeOut(fadeSpring))
        }
        initialState == 2 -> {
            (scaleIn(initialScale = 1.08f, animationSpec = detailScaleSpring) + fadeIn(fadeSpring)) togetherWith
                (scaleOut(targetScale = 0.90f, animationSpec = detailScaleSpring) + fadeOut(fadeSpring))
        }
        else -> {
            fadeIn(fadeSpring) togetherWith fadeOut(fadeSpring)
        }
    }
}
