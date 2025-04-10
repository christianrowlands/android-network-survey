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
import com.craxiom.messaging.wifi.WifiBandwidth;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.model.WifiNetwork;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.util.ColorUtils;
import com.craxiom.networksurvey.util.WifiUtils;

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
                .inflate(R.layout.wifi_network_item, parent, false);
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
            final int signalStrength = (int) data.getSignalStrength().getValue();
            holder.signalStrength.setText(context.getString(R.string.dbm_value, String.valueOf(signalStrength)));
            holder.signalStrength.setTextColor(context.getResources().getColor(ColorUtils.getColorForSignalStrength(signalStrength), null));
        } else
        {
            holder.signalStrength.setText("");
        }

        holder.bssid.setText(context.getString(R.string.bssid_value, data.getBssid()));
        holder.encryptionType.setText(WifiBeaconMessageConstants.getEncryptionTypeString(data.getEncryptionType()));
        holder.frequency.setText(data.hasFrequencyMhz() ? context.getString(R.string.wifi_frequency_value, data.getFrequencyMhz().getValue()) : "");

        setChannelText(holder, data);

        holder.bandwidth.setText(data.getBandwidth() != WifiBandwidth.UNKNOWN ? context.getString(R.string.wifi_bandwidth_value, WifiUtils.formatBandwidth(data.getBandwidth())) : "");
        holder.standard.setText(WifiUtils.formatStandard(data.getStandard()));
        boolean passpoint = data.getPasspoint().getValue();
        holder.passpoint.setText((data.hasPasspoint() && passpoint) ? "Passpoint" : "");
        holder.capabilities.setText(wifiRecordWrapper.getCapabilitiesString());
    }

    /**
     * Sets the channel text view with the channel number and also the center channel number if
     * applicable.
     */
    private void setChannelText(ViewHolder holder, WifiBeaconRecordData data)
    {
        if (data.hasChannel())
        {
            int channel = data.getChannel().getValue();
            int centerChannel = channel;
            if (data.hasFrequencyMhz())
            {
                centerChannel = WifiUtils.getCenterChannel(channel, data.getBandwidth(), data.getFrequencyMhz().getValue());
            }
            if (centerChannel != channel)
            {
                holder.channel.setText(context.getString(R.string.wifi_channel_and_center_value, channel, centerChannel));
            } else
            {
                holder.channel.setText(context.getString(R.string.wifi_channel_value, channel));
            }
        } else
        {
            holder.channel.setText("");
        }
    }

    @Override
    public int getItemCount()
    {
        return wifiRecords.size();
    }

    /**
     * Navigates to the Wi-Fi details screen for the selected Wi-Fi network.
     */
    private void navigateToWifiDetails(WifiNetwork wifiNetwork)
    {
        wifiNetworksFragment.navigateToWifiDetails(wifiNetwork);
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
        final TextView bandwidth;
        final TextView standard;
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
            bandwidth = view.findViewById(R.id.wifi_bandwidth);
            standard = view.findViewById(R.id.wifi_standard);
            passpoint = view.findViewById(R.id.wifi_passpoint);
            capabilities = view.findViewById(R.id.wifi_capabilities);

            mView.setOnClickListener(v -> {
                Float signalStrength = null;
                WifiBeaconRecordData data = wifiRecord.getData();
                if (data.hasSignalStrength())
                {
                    signalStrength = data.getSignalStrength().getValue();
                }

                if (data.getBssid().isEmpty())
                {
                    Timber.wtf("The BSSID is empty so we are unable to show the Wi-Fi details screen.");
                    return;
                }

                WifiNetwork wifiNetwork = new WifiNetwork(
                        data.getBssid(),
                        signalStrength,
                        data.getSsid(),
                        data.hasFrequencyMhz() ? data.getFrequencyMhz().getValue() : null,
                        data.hasChannel() ? data.getChannel().getValue() : null,
                        data.getBandwidth(),
                        WifiBeaconMessageConstants.getEncryptionTypeString(data.getEncryptionType()),
                        data.hasPasspoint() ? data.getPasspoint().getValue() : null,
                        capabilities.getText().toString(),
                        data.getStandard());
                navigateToWifiDetails(wifiNetwork);
            });
        }
    }
}
