package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.fragment.NavHostFragment;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.networksurvey.CalculationUtils;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.databinding.FragmentNetworkDetailsBinding;
import com.craxiom.networksurvey.fragments.model.CellularViewModel;
import com.craxiom.networksurvey.fragments.model.LteNeighbor;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.ColorUtils;
import com.craxiom.networksurvey.util.MathUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import app.futured.donut.DonutProgressView;
import app.futured.donut.DonutSection;
import timber.log.Timber;

/**
 * A fragment for displaying the latest cellular network details to the user.
 */
public class NetworkDetailsFragment extends AServiceDataFragment implements ICellularSurveyRecordListener, LocationListener
{
    public static final AtomicBoolean visible = new AtomicBoolean(false); // TODO Delete me
    static final String TITLE = "Details";

    private static final int GSM_MAX_NORMALIZED_RSSI = 50;
    private static final int GSM_MIN_RSSI = -110;
    private static final int UMTS_MAX_NORMALIZED_RSCP = 40;
    private static final int UMTS_MIN_RSCP = -115;
    private static final int LTE_MAX_NORMALIZED_RSRP = 35;
    private static final int LTE_MIN_RSRP = -125;
    private static final int LTE_MAX_NORMALIZED_RSRQ = 15;
    private static final int LTE_MIN_RSRQ = -23;
    private static final int NR_MAX_NORMALIZED_RSRP = 40;
    private static final int NR_MIN_RSRP = -115;
    private static final int NR_MAX_NORMALIZED_SS_RSRQ = 15;
    private static final int NR_MIN_SS_RSRQ = -23;

    private final DecimalFormat locationFormat = new DecimalFormat("###.#####");

    private FragmentNetworkDetailsBinding binding;
    private CellularViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        binding = FragmentNetworkDetailsBinding.inflate(inflater);

        final ViewModelStoreOwner viewModelStoreOwner = NavHostFragment.findNavController(this).getViewModelStoreOwner(R.id.nav_graph);
        final ViewModelProvider viewModelProvider = new ViewModelProvider(viewModelStoreOwner);
        viewModel = viewModelProvider.get(getClass().getName(), CellularViewModel.class);

        initializeLocationTextView();

        initializeObservers();

        return binding.getRoot();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        visible.set(true); // TODO Delete me

        // In the edge case event where the user has just granted the location permission but has not restarted the app,
        // we need to update the UI to show the new location in this onResume method. There might be better approaches
        // instead of recalling the initialize view method each time the fragment is resumed.
        initializeLocationTextView();

        final NetworkSurveyActivity activity = (NetworkSurveyActivity) getActivity();
        if (activity != null) activity.runSingleScan();

        startAndBindToService();
    }

    @Override
    public void onPause()
    {
        visible.set(false);

        super.onPause();
    }

    @Override
    public void onDestroyView()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();
        viewModel.getLocation().removeObservers(viewLifecycleOwner);

        super.onDestroyView();
    }

    @Override
    protected void registerDataListeners(NetworkSurveyService service)
    {
        service.registerLocationListener(this);
        service.registerCellularSurveyRecordListener(this);
    }

    @Override
    protected void unregisterDataListeners(NetworkSurveyService service)
    {
        service.unregisterLocationListener(this);
        service.unregisterCellularSurveyRecordListener(this);
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
    }

    @Override
    public void onNrSurveyRecord(NrRecord nrRecord)
    {
    }

    @Override
    public void onCellularBatch(List<CellularRecordWrapper> cellularGroup)
    {
        processCellularGroup(cellularGroup);
    }

    @Override
    public void onNetworkType(String dataNetworkType, String voiceNetworkType)
    {
        viewModel.setDataNetworkType(dataNetworkType);
        viewModel.setVoiceNetworkType(voiceNetworkType);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider)
    {
        if (LocationManager.GPS_PROVIDER.equals(provider)) viewModel.setProviderEnabled(true);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider)
    {
        if (LocationManager.GPS_PROVIDER.equals(provider)) viewModel.setProviderEnabled(false);
    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        viewModel.setLocation(location);
    }

    /**
     * Initialize the location text view based on the phone's state.
     *
     * @since 1.6.0
     */
    private void initializeLocationTextView()
    {
        final TextView tvLocation = binding.location;

        final String displayText;
        final int textColor;

        if (!hasLocationPermission())
        {
            tvLocation.setText(getString(R.string.missing_location_permission));
            tvLocation.setTextColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
            return;
        }

        final Location location = viewModel.getLocation().getValue();
        if (location != null)
        {
            updateLocationTextView(location);
            return;
        }

        final LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
        {
            Timber.wtf("Could not get the location manager.");
            displayText = getString(R.string.no_gps_device);
            textColor = R.color.connectionStatusDisconnected;
        } else
        {
            final LocationProvider locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
            if (locationProvider == null)
            {
                displayText = getString(R.string.no_gps_device);
            } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            {
                // gps exists, but isn't on
                displayText = getString(R.string.turn_on_gps);
            } else
            {
                displayText = getString(R.string.searching_for_location);
            }

            textColor = R.color.connectionStatusConnecting;
        }

        tvLocation.setText(displayText);
        tvLocation.setTextColor(getResources().getColor(textColor, null));
    }

    /**
     * Updates the location text view with the latest latitude and longitude, or if the latest location is below the
     * accuracy threshold then the text view is updated to notify the user of such.
     *
     * @param latestLocation The latest location if available, or null if the accuracy is not good enough.
     */
    private void updateLocationTextView(Location latestLocation)
    {
        final TextView locationTextView = binding.location;
        final TextView accuracyTextView = binding.accuracy;
        if (latestLocation != null)
        {
            final String latLonString = locationFormat.format(latestLocation.getLatitude()) + ", " +
                    locationFormat.format(latestLocation.getLongitude());
            locationTextView.setText(latLonString);
            locationTextView.setTextColor(getResources().getColor(R.color.normalText, null));

            accuracyTextView.setText(getString(R.string.accuracy_value, Integer.toString(MathUtils.roundAccuracy(latestLocation.getAccuracy()))));
        } else
        {
            locationTextView.setText(R.string.low_gps_confidence);
            locationTextView.setTextColor(Color.YELLOW);

            accuracyTextView.setText(getString(R.string.accuracy_initial));
        }
    }

    /**
     * Updates the location UI based on the provided location provider status. If this method is called, it always
     * results in the clearing of the lat/lon from the UI. Therefore, it should only be called when the location
     * provider is enabled or disabled.
     *
     * @param enabled The new status of the location provider; true for enabled, false for disabled.
     */
    private void updateLocationProviderStatus(boolean enabled)
    {
        final TextView locationTextView = binding.location;

        locationTextView.setTextColor(getResources().getColor(R.color.connectionStatusConnecting, null));
        locationTextView.setText(enabled ? R.string.searching_for_location : R.string.turn_on_gps);
    }

    /**
     * @return True if the {@link Manifest.permission#ACCESS_FINE_LOCATION} permission has been granted.  False otherwise.
     */
    private boolean hasLocationPermission()
    {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The ACCESS_FINE_LOCATION permission has not been granted");
            return false;
        }

        return true;
    }

    private void initializeObservers()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();
        viewModel.getDataNetworkType().observe(viewLifecycleOwner, networkType -> binding.currentDataNetwork.setText(networkType));
        viewModel.getCarrier().observe(viewLifecycleOwner, carrier -> binding.currentCarrier.setText(carrier));
        viewModel.getVoiceNetworkType().observe(viewLifecycleOwner, networkType -> binding.currentVoiceNetwork.setText(networkType));

        viewModel.getProviderEnabled().observe(viewLifecycleOwner, this::updateLocationProviderStatus);
        viewModel.getLocation().observe(viewLifecycleOwner, this::updateLocationTextView);

        viewModel.getMcc().observe(viewLifecycleOwner, s -> binding.mcc.setText(s));
        viewModel.getMnc().observe(viewLifecycleOwner, s -> binding.mnc.setText(s));
        viewModel.getAreaCode().observe(viewLifecycleOwner, s -> binding.tac.setText(s));
        viewModel.getCellId().observe(viewLifecycleOwner, l -> updateLteEci(l));
        viewModel.getChannelNumber().observe(viewLifecycleOwner, s -> binding.earfcn.setText(s));

        viewModel.getPci().observe(viewLifecycleOwner, s -> binding.pci.setText(s));
        viewModel.getBandwidth().observe(viewLifecycleOwner, s -> binding.bandwidth.setText(s));
        viewModel.getTa().observe(viewLifecycleOwner, s -> binding.ta.setText(s));

        viewModel.getRsrp().observe(viewLifecycleOwner, i -> {
            binding.rsrpValue.setText(i != null ? String.valueOf(i) : "");
            setSignalStrength(binding.progressBarRsrp, i, LTE_MIN_RSRP, LTE_MAX_NORMALIZED_RSRP);
        });
        viewModel.getRsrq().observe(viewLifecycleOwner, i -> {
            binding.rsrqValue.setText(i != null ? String.valueOf(i) : "");
            setSignalStrength(binding.progressBarRsrq, i, LTE_MIN_RSRQ, LTE_MAX_NORMALIZED_RSRQ);
        });

        viewModel.getLteNeighbors().observe(viewLifecycleOwner, this::updateLteNeighborsView);
    }

    /**
     * The method responsible for handling a new batch of cellular records.
     *
     * @param cellularGroup The new batch of cellular records.
     * @since 1.6.0
     */
    private void processCellularGroup(List<CellularRecordWrapper> cellularGroup)
    {
        CellularProtocol servingCellProtocol = null;
        List<LteRecordData> lteNeighbors = new ArrayList<>();
        for (CellularRecordWrapper cellularRecord : cellularGroup)
        {
            switch (cellularRecord.cellularProtocol)
            {
                // TODO Add the other protocols
                case LTE:
                    final LteRecordData lteData = ((LteRecord) cellularRecord.cellularRecord).getData();
                    if (lteData.hasServingCell() && lteData.getServingCell().getValue())
                    {
                        processLteServingCell(lteData);
                        servingCellProtocol = CellularProtocol.LTE;
                    } else
                    {
                        lteNeighbors.add(lteData);
                    }
                    break;
            }
        }

        processLteNeighbors(lteNeighbors);
    }

    /**
     * Takes in the LTE serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the LTE serving cell record.
     * @since 1.6.0
     */
    private void processLteServingCell(LteRecordData data)
    {
        viewModel.setCarrier(data.getProvider());
        viewModel.setMcc(data.hasMcc() ? String.valueOf(data.getMcc().getValue()) : "");
        viewModel.setMnc(data.hasMnc() ? String.valueOf(data.getMnc().getValue()) : "");
        viewModel.setAreaCode(data.hasTac() ? String.valueOf(data.getTac().getValue()) : "");
        viewModel.setCellId(data.hasEci() ? (long) data.getEci().getValue() : null);
        viewModel.setChannelNumber(data.hasEarfcn() ? String.valueOf(data.getEarfcn().getValue()) : "");

        if (data.hasPci())
        {
            final int pci = data.getPci().getValue();
            int primarySyncSequence = CalculationUtils.getPrimarySyncSequence(pci);
            int secondarySyncSequence = CalculationUtils.getSecondarySyncSequence(pci);
            viewModel.setPci(pci + " (" + primarySyncSequence + "/" + secondarySyncSequence + ")");
        } else
        {
            viewModel.setPci("");
        }
        viewModel.setBandwidth(LteMessageConstants.getLteBandwidth(data.getLteBandwidth()));
        viewModel.setTa(data.hasTa() ? String.valueOf(data.getTa().getValue()) : "");

        viewModel.setRsrp(data.hasRsrp() ? (int) data.getRsrp().getValue() : null);
        viewModel.setRsrq(data.hasRsrq() ? (int) data.getRsrq().getValue() : null);
    }

    /**
     * Takes in the current group of LTE neighbors, converts them to a {@link LteNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     * @since 1.6.0
     */
    private void processLteNeighbors(List<LteRecordData> neighbors)
    {
        final TreeSet<LteNeighbor> lteNeighbors = neighbors.stream().map(data -> {
            LteNeighbor.LteNeighborBuilder builder = LteNeighbor.builder();
            if (data.hasEarfcn()) builder.earfcn(data.getEarfcn().getValue());
            if (data.hasPci()) builder.pci(data.getPci().getValue());
            if (data.hasRsrp()) builder.rsrp((int) data.getRsrp().getValue());
            if (data.hasRsrq()) builder.rsrq((int) data.getRsrq().getValue());
            if (data.hasTa()) builder.ta(data.getTa().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setLteNeighbors(lteNeighbors);
    }

    private void updateLteEci(Long ci)
    {
        if (ci != null)
        {
            int eci = ci.intValue();
            binding.cid.setText(String.valueOf(eci));

            // The Cell Identity is 28 bits long. The first 20 bits represent the Macro eNodeB ID. The last 8 bits
            // represent the sector.  Strip off the last 8 bits to get the Macro eNodeB ID.
            int eNodebId = CalculationUtils.getEnodebIdFromCellId(eci);
            binding.enbId.setText(String.valueOf(eNodebId));

            int sectorId = CalculationUtils.getSectorIdFromCellId(eci);
            binding.sectorId.setText(String.valueOf(sectorId));
        } else
        {
            binding.cid.setText("");
            binding.enbId.setText("");
            binding.sectorId.setText("");
        }
    }

    /**
     * Updates the first signal strength indicator UI element with the provided value. If the value is null, then
     * the current value is cleared and a blank UI element is show.
     *
     * @param signalValue The new signal value to set, or null if the current value should be cleared.
     * @since 1.6.0
     */
    private void setSignalStrength(DonutProgressView signalStrengthBar, Integer signalValue, int minValue, int maxNormalizedValue)
    {
        if (signalValue == null)
        {
            binding.progressBarRsrq.clear();
            return;
        }

        int normalizedValue = signalValue <= minValue ? 0 : Math.abs(minValue - signalValue);

        final int color = ColorUtils.getSignalColorForValue(normalizedValue, maxNormalizedValue);

        final DonutSection fillSection = new DonutSection("fill", color, normalizedValue);
        signalStrengthBar.setCap(maxNormalizedValue);
        signalStrengthBar.submitData(Collections.singletonList(fillSection));
    }

    /**
     * Given the newest set of LTE neighbors, update the neighbors table view.
     *
     * @param neighbors The latest batch of LTE neighbors.
     * @since 1.6.0
     */
    private void updateLteNeighborsView(SortedSet<LteNeighbor> neighbors)
    {
        final Context context = getContext();
        if (context == null) return;

        final TableLayout lteNeighborsTable = binding.lteNeighborsTable;

        lteNeighborsTable.removeAllViews();

        for (LteNeighbor neighbor : neighbors)
        {
            final TableRow row = new TableRow(context);

            addValueToRow(context, row, neighbor.earfcn);
            addValueToRow(context, row, neighbor.pci);
            addValueToRow(context, row, neighbor.rsrp);
            addValueToRow(context, row, neighbor.rsrq);
            addValueToRow(context, row, neighbor.ta);

            lteNeighborsTable.addView(row);
        }
    }

    /**
     * Set the provided value in a TextView and then add it to the row.
     *
     * @param context The context to use for creating the TextView.
     * @param row     The row to add the cell to.
     * @param value   The value to place in the cell. If the value is {@link LteNeighbor#UNSET_VALUE},
     *                then an empty strinig is placed in the cell.
     * @since 1.6.0
     */
    private void addValueToRow(Context context, TableRow row, int value)
    {
        final String cellText;
        if (value == LteNeighbor.UNSET_VALUE)
        {
            // We need to add an empty text view to make sure the columns align correctly
            cellText = "";
        } else
        {
            cellText = String.valueOf(value);
        }

        final TextView view = new TextView(context, null, 0, R.style.TableText);
        view.setText(cellText);
        row.addView(view);
    }
}
