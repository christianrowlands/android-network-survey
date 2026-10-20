package com.craxiom.networksurvey.ui.cellular

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

/**
 * Helper function to robustly dismiss the keyboard in ModalBottomSheet.
 * Uses multiple approaches due to known issues with keyboard dismissal in bottom sheets.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun rememberKeyboardDismisser(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current

    return remember(focusManager, keyboardController, view) {
        {
            // 1. Clear focus first (removes cursor from TextFields)
            focusManager.clearFocus()

            // 2. Hide keyboard using Compose API
            keyboardController?.hide()

            // 3. Fallback: Use Android's InputMethodManager for extra reliability
            try {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    val imm =
                        activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                }
            } catch (_: Exception) {
                // Silently fail if we can't get the activity or IMM
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CellSearchBottomSheet(
    mccValue: String,
    mncValue: String,
    areaValue: String,
    cidValue: String,
    onMccChange: (String) -> Unit,
    onMncChange: (String) -> Unit,
    onAreaChange: (String) -> Unit,
    onCidChange: (String) -> Unit,
    onClearAll: () -> Unit,
    onSearch: (mcc: String, mnc: String, area: Int, cid: Long) -> Unit,
    onDismiss: () -> Unit,
    isSearching: Boolean = false,
    @StringRes searchError: Int? = null,
    hasSearchResult: Boolean = false,
    onClearSearchResult: () -> Unit = {}
) {
    val bottomSheetState = rememberModalBottomSheetState()
    val dismissKeyboard = rememberKeyboardDismisser()
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tower_search_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        dismissKeyboard()
                        onClearAll()
                    },
                    enabled = mccValue.isNotEmpty() || mncValue.isNotEmpty() ||
                            areaValue.isNotEmpty() || cidValue.isNotEmpty()
                ) {
                    Text(stringResource(R.string.clear_all))
                }
            }

            Text(
                text = stringResource(R.string.tower_search_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
            )

            // First row: MCC and MNC
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = mccValue,
                    onValueChange = onMccChange,
                    label = { Text(stringResource(R.string.plmn_lookup_mcc_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Right) }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = mccValue.isNotEmpty() && mccValue.toIntOrNull() == null
                )

                OutlinedTextField(
                    value = mncValue,
                    onValueChange = onMncChange,
                    label = { Text(stringResource(R.string.plmn_lookup_mnc_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = mncValue.isNotEmpty() && mncValue.toIntOrNull() == null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second row: LAC/TAC and CID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = areaValue,
                    onValueChange = onAreaChange,
                    label = { Text(stringResource(R.string.lac_tac_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Right) }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = areaValue.isNotEmpty() && areaValue.toIntOrNull() == null
                )

                OutlinedTextField(
                    value = cidValue,
                    onValueChange = onCidChange,
                    label = { Text(stringResource(R.string.cid_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            dismissKeyboard()
                            // Optionally trigger search if all fields are valid
                            val area = areaValue.toIntOrNull()
                            val cid = cidValue.toLongOrNull()
                            if (mccValue.toIntOrNull() != null && mncValue.toIntOrNull() != null && area != null && cid != null) {
                                onSearch(mccValue, mncValue, area, cid)
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = cidValue.isNotEmpty() && cidValue.toLongOrNull() == null
                )
            }

            searchError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clear Result button - shown only when there's a search result
                if (hasSearchResult) {
                    OutlinedButton(
                        onClick = onClearSearchResult,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.tower_search_clear_result))
                    }
                }

                // Search button
                Button(
                    onClick = {
                        // Dismiss keyboard first
                        dismissKeyboard()

                        val area = areaValue.toIntOrNull()
                        val cid = cidValue.toLongOrNull()

                        if (mccValue.toIntOrNull() != null && mncValue.toIntOrNull() != null && area != null && cid != null) {
                            onSearch(mccValue, mncValue, area, cid)
                        }
                    },
                    modifier = if (hasSearchResult) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    enabled = !isSearching &&
                            mccValue.toIntOrNull() != null &&
                            mncValue.toIntOrNull() != null &&
                            areaValue.toIntOrNull() != null &&
                            cidValue.toLongOrNull() != null
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.tower_search_button))
                    }
                }
            }
        }
    }
}
