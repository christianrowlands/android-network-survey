package com.craxiom.networksurvey.ui.plmn.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.plmn.PlmnMode
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Pill-style mode tab selector for the PLMN lookup screen. Visually identical to
 * `com.craxiom.networksurvey.ui.wifi.components.WifiModeTabs` but typed against [PlmnMode].
 */
@Composable
fun PlmnModeTabs(
    mode: PlmnMode,
    onModeChanged: (PlmnMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeTab(
            label = stringResource(R.string.plmn_mode_search),
            selected = mode == PlmnMode.Search,
            onClick = { onModeChanged(PlmnMode.Search) },
        )
        ModeTab(
            label = stringResource(R.string.plmn_mode_resolve),
            selected = mode == PlmnMode.Resolve,
            onClick = { onModeChanged(PlmnMode.Resolve) },
        )
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) WifiTokens.SsidSoft else Color.Transparent
    val border = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    val textColor =
        if (selected) WifiTokens.SsidAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
    }
}

/**
 * Outlined search field used in PLMN Search mode. Auto-focuses on first composition via the
 * caller-provided [focusRequester]. Trailing clear icon appears only when [value] is non-empty.
 */
@Composable
fun PlmnSearchField(
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val fieldDescription = stringResource(R.string.plmn_lookup_search_field_a11y)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(12.dp)
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = WifiTokens.InkFaint,
            modifier = Modifier.height(20.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = fieldDescription },
            textStyle = LocalTextStyle.current.merge(
                TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(WifiTokens.SsidAccent),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.plmn_lookup_search_placeholder),
                            style = TextStyle(fontSize = 15.sp),
                            color = WifiTokens.InkFaint,
                        )
                    }
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) {
            // Use IconButton's default 48 dp touch target rather than constraining it to 28 dp.
            IconButton(onClick = { onChange("") }) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.plmn_clear_search_a11y),
                    tint = WifiTokens.InkFaint,
                )
            }
        }
    }
}

/**
 * Two side-by-side numeric cards for Resolve mode. Each card highlights its border in the primary
 * tint when populated; both inputs are digit-only and capped at 3 chars. Focus management is
 * driven by the caller-supplied [mccFocusRequester] / [mncFocusRequester].
 */
@Composable
fun PlmnResolveFields(
    mcc: String,
    mnc: String,
    onMcc: (String) -> Unit,
    onMnc: (String) -> Unit,
    mccFocusRequester: FocusRequester,
    mncFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NumericCard(
            label = stringResource(R.string.plmn_lookup_mcc_label),
            a11yLabel = stringResource(R.string.plmn_lookup_mcc_field_a11y),
            value = mcc,
            onChange = { onMcc(it) },
            focusRequester = mccFocusRequester,
            imeAction = ImeAction.Next,
            onImeAction = { mncFocusRequester.requestFocus() },
            modifier = Modifier.weight(1f),
        )
        NumericCard(
            label = stringResource(R.string.plmn_lookup_mnc_label),
            a11yLabel = stringResource(R.string.plmn_lookup_mnc_field_a11y),
            value = mnc,
            onChange = { onMnc(it) },
            focusRequester = mncFocusRequester,
            imeAction = ImeAction.Done,
            onImeAction = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumericCard(
    label: String,
    a11yLabel: String,
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (value.isNotEmpty()) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08f.em,
            ),
            color = WifiTokens.InkFaint,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(top = 12.dp)
                .focusRequester(focusRequester)
                .semantics { contentDescription = a11yLabel },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum",
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction?.invoke() },
                onDone = { onImeAction?.invoke() }
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}
