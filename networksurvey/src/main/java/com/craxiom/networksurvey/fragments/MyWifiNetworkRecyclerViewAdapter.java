package com.craxiom.networksurvey.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.messaging.WifiBeaconRecord;
import com.craxiom.networksurvey.model.WifiRecordWrapper;

/**
 * The recycler view for the list of Wi-Fi networks displayed in the UI.
 *
 * @since 0.1.2
 */
public class MyWifiNetworkRecyclerViewAdapter extends RecyclerView.Adapter<MyWifiNetworkRecyclerViewAdapter.ViewHolder>
{
    private final SortedList<WifiRecordWrapper> wifiRecords;
    private final Context context;

    MyWifiNetworkRecyclerViewAdapter(SortedList<WifiRecordWrapper> items, Context context)
    {
        wifiRecords = items;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_wifi_network_item, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position)
    {
        final WifiRecordWrapper wifiRecordWrapper = wifiRecords.get(position);

        final WifiBeaconRecord wifiBeaconRecord = wifiRecordWrapper.getWifiBeaconRecord();
        holder.wifiRecord = wifiBeaconRecord;
        final String ssid = wifiBeaconRecord.getSsid();
        if (ssid.isEmpty())
        {
            holder.ssid.setText(WifiBeaconMessageConstants.HIDDEN_SSID_PLACEHOLDER);
            holder.ssid.setTextColor(context.getResources().getColor(R.color.red, null));
        } else
        {
            holder.ssid.setText(ssid);
            holder.ssid.setTextColor(context.getResources().getColor(R.color.colorAccent, null));
        }

        if (wifiBeaconRecord.hasSignalStrength())
        {
            final float signalStrength = wifiBeaconRecord.getSignalStrength().getValue();
            holder.signalStrength.setText(context.getString(R.string.wifi_dbm_value, String.valueOf(signalStrength)));
            holder.signalStrength.setTextColor(context.getResources().getColor(getColorForSignalStrength(signalStrength), null));
        } else
        {
            holder.signalStrength.setText("");
        }

        holder.bssid.setText(context.getString(R.string.bssid_value, wifiBeaconRecord.getBssid()));
        holder.encryptionType.setText(WifiBeaconMessageConstants.getEncryptionTypeString(wifiBeaconRecord.getEncryptionType()));
        holder.frequency.setText(wifiBeaconRecord.hasFrequency() ? context.getString(R.string.wifi_frequency_value, wifiBeaconRecord.getFrequency().getValue()) : "");
        holder.channel.setText(wifiBeaconRecord.hasChannel() ? context.getString(R.string.wifi_channel_value, wifiBeaconRecord.getChannel().getValue()) : "");
        holder.capabilities.setText(wifiRecordWrapper.getCapabilitiesString());
    }

    @Override
    public int getItemCount()
    {
        return wifiRecords.size();
    }

    /**
     * @param signalStrength The signal strength value in dBm.
     * @return The resource ID for the color that should be used for the signal strength text.
     */
    private int getColorForSignalStrength(float signalStrength)
    {
        final int colorResourceId;
        if (signalStrength > -60)
        {
            colorResourceId = R.color.wifi_rssi_green;
        } else if (signalStrength > -70)
        {
            colorResourceId = R.color.wifi_rssi_yellow;
        } else if (signalStrength > -80)
        {
            colorResourceId = R.color.wifi_rssi_orange;
        } else if (signalStrength > -90)
        {
            colorResourceId = R.color.wifi_rssi_red;
        } else
        {
            colorResourceId = R.color.wifi_rssi_deep_red;
        }

        return colorResourceId;
    }

    /**
     * The holder for the view components that go into the View.  These UI components will be updated with the content
     * in the onBindViewHolder method.
     */
    static class ViewHolder extends RecyclerView.ViewHolder
    {
        final View mView;
        final TextView ssid;
        final TextView signalStrength;
        final TextView bssid;
        final TextView encryptionType;
        final TextView frequency;
        final TextView channel;
        final TextView capabilities;
        WifiBeaconRecord wifiRecord;

        ViewHolder(View view)
        {
            super(view);
            mView = view;
            ssid = view.findViewById(R.id.ssid);
            signalStrength = view.findViewById(R.id.wifi_signal_strength);
            bssid = view.findViewById(R.id.bssid);
            encryptionType = view.findViewById(R.id.encryption_type);
            frequency = view.findViewById(R.id.wifi_frequency);
            channel = view.findViewById(R.id.wifi_channel);
            capabilities = view.findViewById(R.id.wifi_capabilities);
        }
    }
}
