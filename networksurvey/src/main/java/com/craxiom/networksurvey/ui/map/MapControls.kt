package com.craxiom.networksurvey.ui.map

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

/** How far a map button shrinks while pressed. */
private const val PRESSED_SCALE = 0.90f

/** The opacity of a map control's fill, so the map shows faintly through it. */
private const val CONTROL_FILL_ALPHA = 0.95f

/**
 * A round button floating over a map, with a small press animation. Used by the Map tab's action
 * row and the watchlist sighting map.
 *
 * @param isLarge A 64dp button with a larger icon, for the primary action.
 */
@Composable
fun MapButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    icon: ImageVector? = null,
    isLarge: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    require(iconRes != null || icon != null) { "Either iconRes or icon must be provided" }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) PRESSED_SCALE else 1f, label = "button_scale")

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(if (isLarge) 64.dp else 48.dp)
            .scale(scale),
        containerColor = containerColor.copy(alpha = CONTROL_FILL_ALPHA),
        contentColor = contentColor,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (isLarge) 8.dp else 6.dp,
            pressedElevation = if (isLarge) 12.dp else 10.dp,
            hoveredElevation = if (isLarge) 10.dp else 8.dp
        ),
        interactionSource = interactionSource
    ) {
        val iconModifier = Modifier.size(if (isLarge) 36.dp else 28.dp)
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = iconModifier)
        } else {
            Icon(painter = painterResource(id = iconRes!!), contentDescription = contentDescription, modifier = iconModifier)
        }
    }
}

/** Follow my location: filled while the camera follows the user, outlined otherwise. */
@Composable
fun LocationButton(isFollowing: Boolean, toggleFollowMe: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) PRESSED_SCALE else 1f, label = "location_button_scale")

    FloatingActionButton(
        onClick = toggleFollowMe,
        modifier = modifier
            .size(48.dp)
            .scale(scale),
        containerColor = if (isFollowing) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = CONTROL_FILL_ALPHA)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = CONTROL_FILL_ALPHA)
        },
        contentColor = if (isFollowing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (isFollowing) 8.dp else 6.dp,
            pressedElevation = 10.dp,
            hoveredElevation = 9.dp
        ),
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(if (isFollowing) R.drawable.ic_my_location else R.drawable.ic_location_not_following),
            contentDescription = stringResource(if (isFollowing) R.string.map_stop_following else R.string.map_follow_location),
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Zoom in and zoom out, stacked. */
@Composable
fun ZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = CircleShape,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ZoomButton(R.drawable.ic_add, stringResource(R.string.map_zoom_in), onZoomIn)
            ZoomButton(R.drawable.ic_remove, stringResource(R.string.map_zoom_out), onZoomOut)
        }
    }
}

@Composable
private fun ZoomButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) PRESSED_SCALE else 1f, label = "zoom_scale")
    FloatingActionButton(
        onClick = onClick,
        // 40dp buttons 4dp apart inside the 4dp padding still give each a 48dp touch area
        modifier = Modifier
            .size(40.dp)
            .scale(scale),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CONTROL_FILL_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp,
            hoveredElevation = 1.dp
        ),
        interactionSource = interactionSource
    ) {
        Icon(painter = painterResource(icon), contentDescription = contentDescription)
    }
}

/**
 * A translucent band behind the status bar over a full-screen map. The status bar icons follow the
 * app theme, not the map tiles, so the band uses the theme's surface: dark icons get a light band
 * in the light scheme.
 */
@Composable
fun TopAppBarOverlay(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
    )
}
