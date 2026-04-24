package com.craxiom.networksurvey.ui.manufacturer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.ManufacturerResolver
import com.craxiom.networksurvey.data.ManufacturerSources
import com.craxiom.networksurvey.data.oui.OuiStatus
import androidx.compose.ui.res.stringResource

/**
 * Authoritative manufacturer identity card for a Bluetooth device. Always renders the three
 * underlying sources inline (Device brand, Service vendor, Chipset (OUI)) beneath a summary
 * primary line, plus an info icon that opens the explanatory bottom sheet.
 *
 * Disagreement between sources is highlighted on each differing row using the tertiary
 * container background, not error red, and only after every source has reached a terminal
 * state (no flicker during partial load).
 */
@Composable
fun BluetoothManufacturerCard(
    mac: String?,
    serviceUuids: List<String>?,
    companyId: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolver = remember(context) { ManufacturerResolver.getInstance(context) }

    // Synchronous YAML-only paint first. Oui status stays LOADING until the async call returns.
    val initial = remember(serviceUuids, companyId) {
        resolver.resolveSynchronousSources(serviceUuids, companyId)
    }

    val sources by produceState(
        initialValue = initial,
        key1 = mac,
        key2 = serviceUuids,
        key3 = companyId
    ) {
        value = runCatching {
            resolver.resolve(mac, serviceUuids, companyId)
        }.getOrElse { initial.copy(ouiStatus = OuiStatus.TRANSIENT_FAILURE) }
    }

    var showSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(Modifier.padding(padding)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.manufacturer_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showSheet = true },
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.manufacturer_info_content_description)
                    )
                }
            }

            // Primary line
            val primary = sources.primary()
            val primaryText = primary ?: context.getString(R.string.manufacturer_all_unknown)
            val primaryItalic = primary == null

            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontStyle = if (primaryItalic) FontStyle.Italic else FontStyle.Normal
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            SourcesSection(sources)

            // Disagreement caption; only when all sources are terminal and at least two resolved disagree.
            val terminal = sources.ouiStatus != OuiStatus.LOADING
            val hasDisagreement = terminal && sources.resolvedCount() >= 2 && !sources.allAgree()
            if (hasDisagreement) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.manufacturer_disagreement_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Card-level caption when OUI lookup is disabled. Explains why the Chipset row is
            // blank and points to the actionable path. Rendered once below the whole card, never
            // crammed into a per-row value cell.
            if (sources.ouiStatus == OuiStatus.LOOKUP_DISABLED) {
                Spacer(modifier = Modifier.height(4.dp))
                val lockedByMdm = remember(context) { isOuiLookupLockedByMdm(context) }
                Text(
                    text = if (lockedByMdm)
                        stringResource(R.string.manufacturer_disabled_mdm)
                    else
                        stringResource(R.string.manufacturer_disabled_user),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showSheet) {
        ManufacturerSourcesSheet(onDismiss = { showSheet = false })
    }
}

@Composable
private fun SourcesSection(sources: ManufacturerSources) {
    val context = LocalContext.current
    val terminal = sources.ouiStatus != OuiStatus.LOADING
    val disagreementEnabled = terminal && sources.resolvedCount() >= 2 && !sources.allAgree()
    val primaryVendor = sources.primary()

    Column(Modifier.padding(top = 4.dp)) {
        SourceRow(
            label = stringResource(R.string.manufacturer_brand),
            value = sources.companyIdVendor
                ?: stringResource(R.string.manufacturer_not_advertised),
            italic = sources.companyIdVendor.isNullOrBlank(),
            highlight = disagreementEnabled && vendorDiffers(sources.companyIdVendor, primaryVendor)
        )
        SourceRow(
            label = stringResource(R.string.manufacturer_service),
            value = sources.uuidVendor ?: context.getString(R.string.manufacturer_none),
            italic = sources.uuidVendor.isNullOrBlank(),
            highlight = disagreementEnabled && vendorDiffers(sources.uuidVendor, primaryVendor)
        )
        SourceRow(
            label = stringResource(R.string.manufacturer_chipset),
            value = chipsetValue(sources),
            italic = sources.ouiVendor.isNullOrBlank(),
            highlight = disagreementEnabled && vendorDiffers(sources.ouiVendor, primaryVendor)
        )
    }
}

private fun vendorDiffers(candidate: String?, primary: String?): Boolean {
    if (candidate.isNullOrBlank() || primary.isNullOrBlank()) return false
    return !candidate.equals(primary, ignoreCase = true)
}

@Composable
private fun chipsetValue(sources: ManufacturerSources): String {
    if (!sources.ouiVendor.isNullOrBlank()) return sources.ouiVendor
    return ouiStatusCopy(sources.ouiStatus)
}

@Composable
private fun SourceRow(
    label: String,
    value: String,
    italic: Boolean,
    highlight: Boolean
) {
    val bg =
        if (highlight) MaterialTheme.colorScheme.tertiaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val fg =
        if (highlight) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.small)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
            ),
            color = fg,
            modifier = Modifier.weight(2f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManufacturerSourcesSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padding, vertical = padding)
        ) {
            Text(
                text = stringResource(R.string.manufacturer_sheet_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.manufacturer_sheet_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SheetParagraph(
                title = stringResource(R.string.manufacturer_brand),
                body = stringResource(R.string.manufacturer_sheet_brand)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SheetParagraph(
                title = stringResource(R.string.manufacturer_service),
                body = stringResource(R.string.manufacturer_sheet_service)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SheetParagraph(
                title = stringResource(R.string.manufacturer_chipset),
                body = stringResource(R.string.manufacturer_sheet_chipset)
            )
            Spacer(modifier = Modifier.height(padding))
        }
    }
}

@Composable
private fun SheetParagraph(title: String, body: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val padding = 16.dp
