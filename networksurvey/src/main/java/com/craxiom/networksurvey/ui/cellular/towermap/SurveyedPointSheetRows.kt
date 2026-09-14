package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity

/*
 * Row composables and lookups shared by the surveyed place sheet's detail views.
 */

/** The destinations this point's pipeline feeds, with whether each has accepted it. */
internal fun destinations(point: SurveyedPointEntity): List<Pair<Int, Boolean>> =
    if (point.source == SurveyedPointEntity.SOURCE_NS_ANALYTICS) {
        listOf(R.string.surveyed_place_destination_ns to (point.uploadedMask and SurveyedPointEntity.UPLOADED_NS_ANALYTICS != 0))
    } else buildList {
        if (point.observedMask == SurveyedPointEntity.OBSERVED_CELLULAR) {
            add(R.string.surveyed_place_destination_ocid to (point.uploadedMask and SurveyedPointEntity.UPLOADED_OCID != 0))
        }
        add(R.string.surveyed_place_destination_beacondb to (point.uploadedMask and SurveyedPointEntity.UPLOADED_BEACONDB != 0))
    }

@Composable
internal fun DetailRow(
    label: String,
    value: String,
    secondary: String? = null,
    swatch: Int? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                Spacer(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(swatch), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (secondary != null) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

internal fun metricName(protocol: Int): String = when (protocol) {
    SurveyedPointEntity.PROTOCOL_NR -> "SS-RSRP"
    SurveyedPointEntity.PROTOCOL_LTE -> "RSRP"
    SurveyedPointEntity.PROTOCOL_UMTS -> "RSCP"
    else -> "RSSI"
}

internal fun secondaryMetricName(protocol: Int): String = when (protocol) {
    SurveyedPointEntity.PROTOCOL_NR -> "SS-RSRQ"
    SurveyedPointEntity.PROTOCOL_LTE -> "RSRQ"
    SurveyedPointEntity.PROTOCOL_UMTS -> "RSSI"
    else -> ""
}
