package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * A single-line form field for the destination pages: a label, an optional placeholder, and an
 * error or help line underneath. Hosts, ports and names are typed as is, so autocorrect and
 * capitalization stay off.
 *
 * @param imeAction  Next moves to the following field; Done runs [onDone] (usually Save).
 * @param digitsOnly Drops anything that is not a digit, for port numbers.
 */
@Composable
fun NsFormField(
    label: Int,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    digitsOnly: Boolean = false,
    onDone: () -> Unit = {},
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (digitsOnly) it.filter(Char::isDigit) else it) },
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}
