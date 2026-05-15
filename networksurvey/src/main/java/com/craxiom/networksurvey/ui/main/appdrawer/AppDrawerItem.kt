package com.craxiom.networksurvey.ui.main.appdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.main.NavDrawerOption
import com.craxiom.networksurvey.ui.theme.NsTheme

@Composable
fun <T> AppDrawerItem(item: AppDrawerItemInfo<T>, onClick: (options: T) -> Unit) =
    Surface(
        modifier = Modifier
            .width(200.dp)
            .padding(vertical = 4.dp),
        onClick = { onClick(item.drawerOption) },
        shape = RoundedCornerShape(50),
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Icon(
                painter = painterResource(id = item.drawableId),
                contentDescription = stringResource(id = item.descriptionId),
                modifier = Modifier
                    .size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(id = item.title),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                maxLines = 1
            )
        }
    }


class MainStateProvider : PreviewParameterProvider<AppDrawerItemInfo<NavDrawerOption>> {
    override val values = sequence {
        // Hardcoded list for preview purposes (includes all menu items)
        val previewButtons = arrayListOf(
            AppDrawerItemInfo(
                NavDrawerOption.NsAnalyticsConnection,
                R.string.ns_analytics,
                R.drawable.ic_ns_analytics,
                R.string.ns_analytics_description
            ),
            AppDrawerItemInfo(
                NavDrawerOption.ServerConnection,
                R.string.grpc_connection_title,
                R.drawable.connection_icon,
                R.string.grpc_connection_description
            ),
            AppDrawerItemInfo(
                NavDrawerOption.MqttBrokerConnection,
                R.string.mqtt_connection_title_full,
                R.drawable.ic_cloud_connection,
                R.string.device_status_stream_description
            ),
            AppDrawerItemInfo(
                NavDrawerOption.CellularCalculators,
                R.string.cellular_calculators,
                R.drawable.ic_calculator,
                R.string.device_status_stream_description
            ),
            AppDrawerItemInfo(
                NavDrawerOption.PlmnLookup,
                R.string.plmn_lookup_drawer_title,
                R.drawable.ic_plmn_lookup,
                R.string.plmn_lookup_drawer_description
            ),
            AppDrawerItemInfo(
                NavDrawerOption.OuiLookup,
                R.string.oui_lookup_drawer_title,
                R.drawable.ic_oui_lookup,
                R.string.oui_lookup_drawer_description
            ),
            AppDrawerItemInfo(
                NavDrawerOption.Settings,
                R.string.settings,
                R.drawable.ic_settings,
                R.string.device_status_stream_description
            )
        )
        previewButtons.forEach { element ->
            yield(element)
        }
    }
}

@Preview
@Composable
fun AppDrawerItemPreview(@PreviewParameter(MainStateProvider::class) state: AppDrawerItemInfo<NavDrawerOption>) {
    LocalContext.current
    NsTheme {
        AppDrawerItem(item = state, onClick = {})
    }
}