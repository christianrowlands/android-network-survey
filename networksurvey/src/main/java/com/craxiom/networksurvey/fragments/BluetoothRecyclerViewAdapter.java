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

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.BluetoothRecordData;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.util.ColorUtils;

import timber.log.Timber;

/**
 * The recycler view for the list of Bluetooth devices displayed in the UI.
 *
 * @since 1.0.0
 */
public class BluetoothRecyclerViewAdapter extends RecyclerView.Adapter<BluetoothRecyclerViewAdapter.ViewHolder>
{
    private final SortedList<BluetoothRecord> bluetoothRecords;
    private final Context context;
    private final BluetoothFragment bluetoothFragment;

    BluetoothRecyclerViewAdapter(SortedList<BluetoothRecord> items, Context context, BluetoothFragment bluetoothFragment)
    {
        bluetoothRecords = items;
        this.context = context;
        this.bluetoothFragment = bluetoothFragment;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_bluetooth_item, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position)
    {
        final BluetoothRecord bluetoothRecord = bluetoothRecords.get(position);
        final BluetoothRecordData data = bluetoothRecord.getData();
        holder.bluetoothData = data;
        final String sourceAddress = data.getSourceAddress();
        if (!sourceAddress.isEmpty())
        {
            holder.sourceAddress.setText(sourceAddress);
            holder.sourceAddress.setTextColor(context.getResources().getColor(R.color.colorAccent, null));
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

        final String otaDeviceName = data.getOtaDeviceName();
        if (otaDeviceName != null && !otaDeviceName.isEmpty())
        {
            holder.otaDeviceName.setText(context.getString(R.string.ota_device_name_value, otaDeviceName));
        } else
        {
            holder.otaDeviceName.setText("");
        }

        holder.supportedTechnologies.setText(BluetoothMessageConstants.getSupportedTechString(data.getSupportedTechnologies()));
    }

    @Override
    public int getItemCount()
    {
        return bluetoothRecords.size();
    }

    /**
     * Navigates to the Bluetooth details screen for the selected Bluetooth device.
     */
    private void navigateToDetails(BluetoothRecordData bluetoothData)
    {
        bluetoothFragment.navigateToBluetoothDetails(bluetoothData);
    }

    /**
     * The holder for the view components that go into the View.  These UI components will be updated with the content
     * in the onBindViewHolder method.
     */
    class ViewHolder extends RecyclerView.ViewHolder
    {
        final View mView;
        final TextView sourceAddress;
        final TextView signalStrength;
        final TextView otaDeviceName;
        final TextView supportedTechnologies;
        BluetoothRecordData bluetoothData;

        ViewHolder(View view)
        {
            super(view);
            mView = view;
            sourceAddress = view.findViewById(R.id.sourceAddress);
            signalStrength = view.findViewById(R.id.bluetooth_signal_strength);
            otaDeviceName = view.findViewById(R.id.otaDeviceName);
            supportedTechnologies = view.findViewById(R.id.supportedTechnologies);

            mView.setOnClickListener(v -> {
                if (bluetoothData.getSourceAddress().isEmpty())
                {
                    Timber.wtf("The source address is empty so we are unable to show the bluetooth details screen.");
                    return;
                }

                navigateToDetails(bluetoothData);
            });
        }
    }
}
