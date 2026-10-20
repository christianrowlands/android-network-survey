package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.survey.model.SentenceUi
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The warning above Start, shown only when part of the setup goes nowhere: "Call records won't be
 * recorded: no place that is on takes it." and "MQTT stream won't receive anything from this survey."
 */
@Composable
fun SurveySentence(sentence: SentenceUi, modifier: Modifier = Modifier) {
    val text = listOfNotNull(
        sentence.notSent.takeIf { it.isNotEmpty() }?.let {
            pluralStringResource(R.plurals.survey_sentence_not_sent, it.size, recordTypeNames(it))
        },
        sentence.receivesNothing.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.survey_sentence_receives_nothing, destinationNames(it))
        },
    ).joinToString(" ")
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = NsTheme.colors.status.warningText,
        modifier = modifier,
    )
}

/**
 * Fills a `%1$s`-style template with [arguments], setting each argument in bold. Placeholders may
 * appear in any order, as translations need.
 */
fun boldArguments(template: String, arguments: List<String>): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in PLACEHOLDER.findAll(template)) {
        append(template.substring(cursor, match.range.first))
        val index = match.groupValues[1].toInt() - 1
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(arguments.getOrElse(index) { "" }) }
        cursor = match.range.last + 1
    }
    append(template.substring(cursor))
}

private val PLACEHOLDER = Regex("%(\\d+)\\\$s")
