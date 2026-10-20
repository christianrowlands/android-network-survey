package com.craxiom.networksurvey.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.theme.NsTheme

/*
 * The anatomy shared by the destination pages (MQTT, NS Analytics, Community databases, Log files,
 * Organization settings): a titled page with Back, groups of rows in panels, short explanatory text,
 * and an optional full-width action pinned at the bottom.
 */

/**
 * A destination page: the title bar with Back, a scrolling column of panels, and an optional
 * [bottomBar] (usually [NsCtaBar]). Snackbars sit above the bottom bar, never over it.
 */
@Composable
fun NsPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<AppBarAction>? = null,
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TitleBar(title = title, appBarActions = actions, onBackClick = onBack) },
        // The main action clears the gesture bar, and stays above the keyboard while a form field
        // is focused.
        bottomBar = {
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .imePadding()
            ) { bottomBar() }
        },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .nsContentWidth()
                    .padding(horizontal = NsTheme.spacing.screenGutter)
                    .padding(bottom = NsTheme.spacing.l),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap),
                content = content,
            )
        }
    }
}

/** The pinned bar at the bottom of a page that holds its one main action. */
@Composable
fun NsCtaBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .nsContentWidth()
                .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
            content = content,
        )
    }
}

/** The full-width pill button used for a page's main action and for Start and Stop. */
@Composable
fun NsPrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    stateDescription: String? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    iconContent: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = NsTheme.shapes.pill,
        modifier = modifier
            .fillMaxWidth()
            .height(CTA_HEIGHT)
            .semantics { if (stateDescription != null) this.stateDescription = stateDescription },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                iconContent != null -> iconContent()
                icon != null -> Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(CTA_ICON))
            }
            if (iconContent != null || icon != null) Spacer(Modifier.width(NsTheme.spacing.s))
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * A panel of rows separated by thin rules. Pass [selectable] for a group of radio rows so
 * accessibility services announce it as one choice.
 */
@Composable
fun NsRowGroup(modifier: Modifier = Modifier, selectable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NsTheme.shapes.panel,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = PANEL_SHADOW,
    ) {
        Column(
            modifier = Modifier
                .then(if (selectable) Modifier.selectableGroup() else Modifier)
                .padding(horizontal = NsTheme.spacing.panelPaddingHorizontal, vertical = ROW_GROUP_INSET),
            content = content,
        )
    }
}

/** The rule between two rows of an [NsRowGroup]. */
@Composable
fun NsRowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * One row: an icon, a title, an optional subtitle that wraps, and a trailing control. The whole row
 * is the touch target when [onClick] is set.
 *
 * @param locked True when the organization sets this item. The row keeps its full look, adds the
 *               organization glyph and "Set by your organization", and stays tappable so [onClick]
 *               can explain why.
 * @param onLongClick A second action on a long press, only with [onClick].
 */
@Composable
fun NsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    locked: Boolean = false,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val action = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )

        onClick != null -> Modifier.clickable(onClickLabel = onClickLabel, role = role, onClick = onClick)
        else -> Modifier
    }
    NsRowLayout(modifier.then(action), title, subtitle, icon, titleColor, subtitleColor, iconTint, locked, trailing)
}

/** A row whose whole surface toggles a switch. */
@Composable
fun NsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    @DrawableRes icon: Int? = null,
    locked: Boolean = false,
    onLocked: () -> Unit = {},
) {
    val action = if (locked) {
        Modifier.clickable(onClickLabel = stringResource(R.string.survey_lock_learn_why), onClick = onLocked)
    } else {
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    }
    NsRowLayout(
        modifier.then(action), title, subtitle, icon,
        MaterialTheme.colorScheme.onSurface, subtitleColor, MaterialTheme.colorScheme.onSurfaceVariant, locked,
    ) {
        // The row carries the semantics, so the switch itself is not a second target.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A row in a single-choice group: the radio, a title and subtitle, and an optional trailing
 * control (an edit button), which is its own touch target. Put it in an [NsRowGroup] with
 * selectable = true.
 */
@Composable
fun NsRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    locked: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        RowText(title, subtitle, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, locked, Modifier.weight(1f))
        trailing()
    }
}

/** An icon button sized to the 48dp touch target, labelled for accessibility. */
@Composable
fun NsIconAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A paragraph of explanation between panels (the prototype's summary text). */
@Composable
fun NsSummaryText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier.padding(horizontal = NsTheme.spacing.xs),
    )
}

/** A centered empty state: an icon and one or two sentences. */
@Composable
fun NsEmptyState(text: String, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NsTheme.spacing.xxl, horizontal = NsTheme.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(EMPTY_ICON))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NsRowLayout(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    @DrawableRes icon: Int?,
    titleColor: Color,
    subtitleColor: Color,
    iconTint: Color,
    locked: Boolean,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .padding(vertical = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(ROW_ICON))
        RowText(title, subtitle, titleColor, subtitleColor, locked, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun RowText(title: String, subtitle: String?, titleColor: Color, subtitleColor: Color, locked: Boolean, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        if (locked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
                Icon(
                    painterResource(R.drawable.ic_domain),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(LOCK_GLYPH),
                )
                Text(
                    text = stringResource(R.string.survey_set_by_organization),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val CTA_HEIGHT = 56.dp
private val CTA_ICON = 20.dp
private val EMPTY_ICON = 36.dp
private val LOCK_GLYPH = 14.dp
private val ROW_GROUP_INSET = 2.dp
private val PANEL_SHADOW = 1.dp
