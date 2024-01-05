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

import com.craxiom.messaging.WifiBeaconRecord;
import com.craxiom.messaging.WifiBeaconRecordData;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.model.WifiRecordWrapper;

import timber.log.Timber;

/**
 * The recycler view for the list of Wi-Fi networks displayed in the UI.
 *
 * @since 0.1.2
 */
public class MyWifiNetworkRecyclerViewAdapter extends RecyclerView.Adapter<MyWifiNetworkRecyclerViewAdapter.ViewHolder>
{
    private final SortedList<WifiRecordWrapper> wifiRecords;
    private final Context context;
    private final WifiNetworksFragment wifiNetworksFragment;

    MyWifiNetworkRecyclerViewAdapter(SortedList<WifiRecordWrapper> items, Context context, WifiNetworksFragment wifiNetworksFragment)
    {
        wifiRecords = items;
        this.context = context;
        this.wifiNetworksFragment = wifiNetworksFragment;
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
        final WifiBeaconRecordData data = wifiBeaconRecord.getData();
        final String ssid = data.getSsid();
        if (ssid.isEmpty())
        {
            holder.ssid.setText(WifiBeaconMessageConstants.HIDDEN_SSID_PLACEHOLDER);
            holder.ssid.setTextColor(context.getResources().getColor(R.color.red, null));
        } else
        {
            holder.ssid.setText(ssid);
            holder.ssid.setTextColor(context.getResources().getColor(R.color.colorAccent, null));
        }

        if (data.hasSignalStrength())
        {
            final float signalStrength = data.getSignalStrength().getValue();
            holder.signalStrength.setText(context.getString(R.string.dbm_value, String.valueOf(signalStrength)));
            holder.signalStrength.setTextColor(context.getResources().getColor(getColorForSignalStrength(signalStrength), null));
        } else
        {
            holder.signalStrength.setText("");
        }

        holder.bssid.setText(context.getString(R.string.bssid_value, data.getBssid()));
        holder.encryptionType.setText(WifiBeaconMessageConstants.getEncryptionTypeString(data.getEncryptionType()));
        holder.frequency.setText(data.hasFrequencyMhz() ? context.getString(R.string.wifi_frequency_value, data.getFrequencyMhz().getValue()) : "");
        holder.channel.setText(data.hasChannel() ? context.getString(R.string.wifi_channel_value, data.getChannel().getValue()) : "");
        boolean passpoint = data.getPasspoint().getValue();
        holder.passpoint.setText((data.hasPasspoint() && passpoint) ? "Passpoint" : "");
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
            colorResourceId = R.color.rssi_green;
        } else if (signalStrength > -70)
        {
            colorResourceId = R.color.rssi_yellow;
        } else if (signalStrength > -80)
        {
            colorResourceId = R.color.rssi_orange;
        } else if (signalStrength > -90)
        {
            colorResourceId = R.color.rssi_red;
        } else
        {
            colorResourceId = R.color.rssi_deep_red;
        }

        return colorResourceId;
    }

    /**
     * Navigates to the Wi-Fi details screen for the selected Wi-Fi network.
     */
    private void navigateToWifiDetails(String bssid, Float signalStrength)
    {
        if (bssid == null || bssid.isEmpty())
        {
            Timber.wtf("The BSSID is null or empty so we are unable to show the Wi-Fi details screen.");
            return;
        }

        wifiNetworksFragment.navigateToWifiDetails(bssid, signalStrength);
    }

    /**
     * The holder for the view components that go into the View.  These UI components will be updated with the content
     * in the onBindViewHolder method.
     */
    class ViewHolder extends RecyclerView.ViewHolder
    {
        final View mView;
        final TextView ssid;
        final TextView signalStrength;
        final TextView bssid;
        final TextView encryptionType;
        final TextView frequency;
        final TextView channel;
        final TextView passpoint;
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
            passpoint = view.findViewById(R.id.wifi_passpoint);
            capabilities = view.findViewById(R.id.wifi_capabilities);

            mView.setOnClickListener(v -> {
                Float signalStrength = null;
                if (wifiRecord.getData().hasSignalStrength())
                {
                    signalStrength = wifiRecord.getData().getSignalStrength().getValue();
                }
                navigateToWifiDetails(wifiRecord.getData().getBssid(), signalStrength);
            });
        }
    }
}
