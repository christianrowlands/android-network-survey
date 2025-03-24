package com.craxiom.networksurvey.fragments;

import static com.craxiom.networksurvey.ui.ASignalChartViewModelKt.UNKNOWN_RSSI;

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
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.databinding.FragmentNetworkDetailsBinding;
import com.craxiom.networksurvey.fragments.model.CellularViewModel;
import com.craxiom.networksurvey.fragments.model.GsmNeighbor;
import com.craxiom.networksurvey.fragments.model.LteNeighbor;
import com.craxiom.networksurvey.fragments.model.NrNeighbor;
import com.craxiom.networksurvey.fragments.model.UmtsNeighbor;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.model.NrRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.ui.cellular.CellularChartViewModel;
import com.craxiom.networksurvey.ui.cellular.ComposeFunctions;
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.util.CalculationUtils;
import com.craxiom.networksurvey.util.CellularUtils;
import com.craxiom.networksurvey.util.ColorUtils;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.ParserUtils;
import com.mackhartley.roundedprogressbar.RoundedProgressBar;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import app.futured.donut.DonutProgressView;
import app.futured.donut.DonutSection;
import timber.log.Timber;

/**
 * A fragment for displaying the latest cellular network details to the user.
 *
 * @since 1.6.0 (It really came earlier, but was minimal until the 1.6.0 rewrite.
 */
public class NetworkDetailsFragment extends AServiceDataFragment implements ICellularSurveyRecordListener, LocationListener
{
    public static final String SUBSCRIPTION_ID_KEY = "subscription_id";

    // The next two values have been added because certain devices don't follow the Interger#MAX_VALUE approach defined
    // in the Android API. The phone is supposed to report Interger#MAX_VALUE to indicate "Unknown/Unset" values, but
    // Pixel devices seem to report -120 all the time for UMTS RSCP, and Samsung devices seem to report -24 for UMTS RSCP.
    // These values are technically valid and filtering them out is an incorrect thing to do, but it is all I can think
    // of right now to prevent invalid values from being reported.
    private static final int RSCP_UNSET_VALUE_120 = -120;
    private static final int RSCP_UNSET_VALUE_24 = -24;

    private final DecimalFormat locationFormat = new DecimalFormat("###.#####");

    private int subscriptionId;

    private FragmentNetworkDetailsBinding binding;
    private CellularViewModel viewModel;
    private CellularChartViewModel chartViewModel;
    private SharedViewModel sharedViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        //noinspection DataFlowIssue
        subscriptionId = args.getInt(SUBSCRIPTION_ID_KEY, -1);
        Timber.d("Retrieving the subscriptionId from the arguments. subscriptionId=%d", subscriptionId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        binding = FragmentNetworkDetailsBinding.inflate(inflater);

        viewModel = new ViewModelProvider(requireActivity()).get(getClass().getName() + subscriptionId, CellularViewModel.class);
        chartViewModel = new ViewModelProvider(requireActivity()).get(getClass().getName() + "cellular_chart" + subscriptionId, CellularChartViewModel.class);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        initializeLocationTextView();

        initializeUiListeners();

        initializeObservers();

        chartViewModel.addInitialRssi(UNKNOWN_RSSI);
        ComposeFunctions.
                setContent(binding.composeView, chartViewModel);

        return binding.getRoot();
    }

    @Override
    public void onPause()
    {
        chartViewModel.pauseChartUpdates();

        super.onPause();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // In the edge case event where the user has just granted the location permission but has not restarted the app,
        // we need to update the UI to show the new location in this onResume method. There might be better approaches
        // instead of recalling the initialize view method each time the fragment is resumed.
        initializeLocationTextView();

        startAndBindToService();

        chartViewModel.resumeChartUpdates();
    }

    @Override
    public void onDestroyView()
    {
        removeObservers();

        super.onDestroyView();
    }

    @Override
    protected void onSurveyServiceConnected(NetworkSurveyService service)
    {
        service.registerCellularSurveyRecordListener(this);
        service.registerLocationListener(this);
        // Refresh the location views because we might have missed something between the
        // initial call and when we registered as a listener, but only if the location is not null
        // because the initializeLocationTextView method might have set the UI to indicate that the
        // location provider is disabled or that the location permission is missing and we don't
        // want to override that.
        Location latestLocation = service.getPrimaryLocationListener().getLatestLocation();
        if (latestLocation != null) updateLocationTextView(latestLocation);

        service.runSingleCellularScan();
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        service.unregisterLocationListener(this);
        service.unregisterCellularSurveyRecordListener(this);

        super.onSurveyServiceDisconnecting(service);
    }

    @Override
    public void onCellularBatch(List<CellularRecordWrapper> cellularGroup, int subscriptionId)
    {
        // The records are for a different SIM, so ignore them because another
        // NetworkDetailsFragment instance will handle them.
        if (this.subscriptionId != subscriptionId) return;

        processCellularGroup(cellularGroup);
    }

    @Override
    public void onNetworkType(String dataNetworkType, String voiceNetworkType, int subscriptionId, String overrideNetworkType)
    {
        // The records are for a different SIM, so ignore them because another
        // NetworkDetailsFragment instance will handle them.
        if (this.subscriptionId != subscriptionId) return;

        viewModel.setDataNetworkType(dataNetworkType);
        viewModel.setVoiceNetworkType(voiceNetworkType);
        viewModel.setOverrideNetworkType(overrideNetworkType);
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

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        // No-op
    }

    /**
     * Initialize the UI listeners for the various buttons and other UI elements.
     */
    private void initializeUiListeners()
    {
        binding.overrideNetworkGroup.setOnClickListener(c -> showOverrideNetworkInfoDialog());
        binding.cellularInfoIcon.setOnClickListener(c -> showCellularInfoDialog());
    }

    /**
     * Initialize the model view observers. These observers look for changes to the model view
     * values, and then update the UI based on any changes.
     */
    private void initializeObservers()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        viewModel.getDataNetworkType().observe(viewLifecycleOwner, networkType -> binding.currentDataNetwork.setText(networkType));
        viewModel.getCarrier().observe(viewLifecycleOwner, carrier -> binding.currentCarrier.setText(carrier));
        viewModel.getVoiceNetworkType().observe(viewLifecycleOwner, networkType -> binding.currentVoiceNetwork.setText(networkType));
        viewModel.getOverrideNetworkType().observe(viewLifecycleOwner, networkType -> binding.currentOverrideNetwork.setText(networkType));

        viewModel.getProviderEnabled().observe(viewLifecycleOwner, this::updateLocationProviderStatus);
        viewModel.getLocation().observe(viewLifecycleOwner, this::updateLocationTextView);

        viewModel.getServingCellProtocol().observe(viewLifecycleOwner, this::updateServingCellProtocol);

        viewModel.getMcc().observe(viewLifecycleOwner, s -> binding.plmn.setText(getString(R.string.mcc_mnc_value, s, viewModel.getMnc().getValue())));
        viewModel.getMnc().observe(viewLifecycleOwner, s -> binding.plmn.setText(getString(R.string.mcc_mnc_value, viewModel.getMcc().getValue(), s)));
        viewModel.getAreaCode().observe(viewLifecycleOwner, s -> binding.tac.setText(s));
        viewModel.getCellId().observe(viewLifecycleOwner, this::updateCellIdentity);
        viewModel.getChannelNumber().observe(viewLifecycleOwner, s -> binding.earfcn.setText(s));

        viewModel.getPci().observe(viewLifecycleOwner, s -> binding.pci.setText(s));
        viewModel.getBandwidth().observe(viewLifecycleOwner, s -> binding.bandwidth.setText(s));
        viewModel.getTa().observe(viewLifecycleOwner, s -> binding.ta.setText(s));
        viewModel.getCqi().observe(viewLifecycleOwner, s -> binding.cqi.setText(s));

        viewModel.getSignalOne().observe(viewLifecycleOwner, this::updateSignalStrengthOne);
        viewModel.getSignalTwo().observe(viewLifecycleOwner, this::updateSignalStrengthTwo);
        viewModel.getSignalThree().observe(viewLifecycleOwner, this::updateSignalStrengthThree);

        viewModel.getNrNeighbors().observe(viewLifecycleOwner, this::updateNrNeighborsView);
        viewModel.getLteNeighbors().observe(viewLifecycleOwner, this::updateLteNeighborsView);
        viewModel.getUmtsNeighbors().observe(viewLifecycleOwner, this::updateUmtsNeighborsView);
        viewModel.getGsmNeighbors().observe(viewLifecycleOwner, this::updateGsmNeighborsView);
    }

    /**
     * Cleans up by removing all the view model observers.
     */
    private void removeObservers()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        viewModel.getDataNetworkType().removeObservers(viewLifecycleOwner);
        viewModel.getCarrier().removeObservers(viewLifecycleOwner);
        viewModel.getVoiceNetworkType().removeObservers(viewLifecycleOwner);
        viewModel.getOverrideNetworkType().removeObservers(viewLifecycleOwner);

        viewModel.getProviderEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getLocation().removeObservers(viewLifecycleOwner);

        viewModel.getServingCellProtocol().removeObservers(viewLifecycleOwner);

        viewModel.getMcc().removeObservers(viewLifecycleOwner);
        viewModel.getMnc().removeObservers(viewLifecycleOwner);
        viewModel.getAreaCode().removeObservers(viewLifecycleOwner);
        viewModel.getCellId().removeObservers(viewLifecycleOwner);
        viewModel.getChannelNumber().removeObservers(viewLifecycleOwner);

        viewModel.getPci().removeObservers(viewLifecycleOwner);
        viewModel.getBandwidth().removeObservers(viewLifecycleOwner);
        viewModel.getTa().removeObservers(viewLifecycleOwner);
        viewModel.getCqi().removeObservers(viewLifecycleOwner);

        viewModel.getSignalOne().removeObservers(viewLifecycleOwner);
        viewModel.getSignalTwo().removeObservers(viewLifecycleOwner);
        viewModel.getSignalThree().removeObservers(viewLifecycleOwner);

        viewModel.getNrNeighbors().removeObservers(viewLifecycleOwner);
        viewModel.getLteNeighbors().removeObservers(viewLifecycleOwner);
        viewModel.getUmtsNeighbors().removeObservers(viewLifecycleOwner);
        viewModel.getGsmNeighbors().removeObservers(viewLifecycleOwner);
    }

    /**
     * Clears out the UI, which is needed if the phone stops seeing towers or something else happens (e.g. airplane mode).
     */
    private void clearCellularUi()
    {
        viewModel.setServingCellProtocol(CellularProtocol.NONE);

        viewModel.setMcc("");
        viewModel.setMnc("");
        viewModel.setAreaCode("");
        viewModel.setCellId(null);
        viewModel.setChannelNumber("");

        viewModel.setPci("");
        viewModel.setBandwidth("");
        viewModel.setTa("");
        viewModel.setCqi("");

        viewModel.setSignalOne(null);
        viewModel.setSignalTwo(null);
        viewModel.setSignalThree(null);

        viewModel.setNrNeighbors(Collections.emptySortedSet());
        viewModel.setLteNeighbors(Collections.emptySortedSet());
        viewModel.setUmtsNeighbors(Collections.emptySortedSet());
        viewModel.setGsmNeighbors(Collections.emptySortedSet());
    }

    /**
     * Initialize the location text view based on the phone's state.
     */
    private void initializeLocationTextView()
    {
        final TextView tvLocation = binding.locationCard.location;

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
        final TextView locationTextView = binding.locationCard.location;
        final TextView altitudeTextView = binding.locationCard.altitude;
        final TextView accuracyTextView = binding.locationCard.accuracy;
        if (latestLocation != null)
        {
            final String latLonString = locationFormat.format(latestLocation.getLatitude()) + ", " +
                    locationFormat.format(latestLocation.getLongitude());
            locationTextView.setText(latLonString);
            locationTextView.setTextColor(getResources().getColor(R.color.normalText, null));

            altitudeTextView.setText(getString(R.string.altitude_value, Long.toString(Math.round(latestLocation.getAltitude()))));

            accuracyTextView.setText(getString(R.string.accuracy_value, Integer.toString(MathUtils.roundAccuracy(latestLocation.getAccuracy()))));
        } else
        {
            locationTextView.setText(R.string.low_gps_confidence);
            locationTextView.setTextColor(Color.YELLOW);

            altitudeTextView.setText(getString(R.string.altitude_initial));

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
        final TextView locationTextView = binding.locationCard.location;

        locationTextView.setTextColor(getResources().getColor(R.color.connectionStatusConnecting, null));
        locationTextView.setText(enabled ? R.string.searching_for_location : R.string.turn_on_gps);
    }

    /**
     * Updates the serving cell title for the serving cell card to reflect the technology being
     * displayed in rest of the card.
     * <p>
     * This method also handles initializing the cellular details UI to handle this protocol.
     *
     * @param protocol The new protocol for the serving cell.
     */
    private void updateServingCellProtocol(CellularProtocol protocol)
    {
        final TextView titleTextView = binding.cellularDetailsTitle;
        titleTextView.setText(getString(R.string.card_title_cellular_details, protocol));

        switch (protocol)
        {
            case NONE:
                titleTextView.setText(R.string.card_title_cellular_details_initial);

                chartViewModel.setChartTitle("RSSI");
                chartViewModel.setCellularProtocol(protocol);
                break;

            case GSM:
                binding.tacLabel.setText(R.string.lac_label);
                binding.enbIdGroup.setVisibility(View.GONE);
                binding.sectorIdGroup.setVisibility(View.GONE);
                binding.earfcnLabel.setText(R.string.arfcn_label);
                binding.pciLabel.setText(R.string.bsic_label);
                binding.bandwidthGroup.setVisibility(View.GONE);
                binding.taGroup.setVisibility(View.GONE);
                binding.cqiGroup.setVisibility(View.GONE);
                binding.signalOneLabel.setText(R.string.rssi_label);
                binding.signalTwoGroup.setVisibility(View.GONE);
                binding.signalTwoLabel.setVisibility(View.GONE);
                binding.signalThreeGroup.setVisibility(View.GONE);
                binding.signalThreeLabel.setVisibility(View.GONE);

                chartViewModel.setChartTitle("RSSI");
                chartViewModel.setCellularProtocol(protocol);
                chartViewModel.setMinRssi(-110);
                chartViewModel.setMaxRssi(-46);
                break;

            case CDMA:
                binding.cqiGroup.setVisibility(View.GONE);
                binding.enbIdGroup.setVisibility(View.GONE);
                binding.sectorIdGroup.setVisibility(View.GONE);
                binding.signalTwoGroup.setVisibility(View.GONE);
                binding.signalTwoLabel.setVisibility(View.GONE);
                binding.signalThreeGroup.setVisibility(View.GONE);
                binding.signalThreeLabel.setVisibility(View.GONE);

                chartViewModel.setChartTitle("RSSI");
                chartViewModel.setCellularProtocol(protocol);
                break;

            case UMTS:
                binding.tacLabel.setText(R.string.lac_label);
                binding.enbIdLabel.setText(R.string.rnc_label);
                binding.enbIdGroup.setVisibility(View.VISIBLE);
                binding.sectorIdLabel.setText(R.string.short_cid_label);
                binding.sectorIdGroup.setVisibility(View.VISIBLE);
                binding.earfcnLabel.setText(R.string.uarfcn_label);
                binding.pciLabel.setText(R.string.psc_label);
                binding.bandwidthGroup.setVisibility(View.GONE);
                binding.taGroup.setVisibility(View.GONE);
                binding.cqiGroup.setVisibility(View.GONE);
                binding.signalOneLabel.setText(R.string.rssi_label);
                binding.signalTwoLabel.setText(R.string.rscp_label);
                binding.signalTwoGroup.setVisibility(View.VISIBLE);
                binding.signalTwoLabel.setVisibility(View.VISIBLE);
                binding.signalThreeGroup.setVisibility(View.GONE);
                binding.signalThreeLabel.setVisibility(View.GONE);

                chartViewModel.setChartTitle("RSCP");
                chartViewModel.setCellularProtocol(protocol);
                chartViewModel.setMinRssi(-110);
                chartViewModel.setMaxRssi(-62);
                break;

            case LTE:
                binding.tacLabel.setText(R.string.tac_label);
                binding.enbIdLabel.setText(R.string.enb_id_label);
                binding.enbIdGroup.setVisibility(View.VISIBLE);
                binding.sectorIdLabel.setText(R.string.sector_id_label);
                binding.sectorIdGroup.setVisibility(View.VISIBLE);
                binding.earfcnLabel.setText(R.string.earfcn_band_label);
                binding.pciLabel.setText(R.string.pci_label);
                binding.bandwidthGroup.setVisibility(View.VISIBLE);
                binding.taGroup.setVisibility(View.VISIBLE);
                binding.cqiGroup.setVisibility(View.VISIBLE);
                binding.signalOneLabel.setText(R.string.rsrp_label);
                binding.signalTwoLabel.setText(R.string.rsrq_label);
                binding.signalTwoGroup.setVisibility(View.VISIBLE);
                binding.signalTwoLabel.setVisibility(View.VISIBLE);
                binding.signalThreeLabel.setText(R.string.snr_label);
                binding.signalThreeGroup.setVisibility(View.VISIBLE);
                binding.signalThreeLabel.setVisibility(View.VISIBLE);

                chartViewModel.setChartTitle("RSRP");
                chartViewModel.setCellularProtocol(protocol);
                chartViewModel.setMinRssi(-125); // -140 dBm is the lowest reportable value for RSRP
                chartViewModel.setMaxRssi(-65); // -44 dBm is the highest reportable value for RSRP
                break;

            case NR:
                binding.tacLabel.setText(R.string.tac_label);
                binding.enbIdGroup.setVisibility(View.GONE);
                binding.sectorIdGroup.setVisibility(View.GONE);
                binding.earfcnLabel.setText(R.string.narfcn_band_label);
                binding.pciLabel.setText(R.string.pci_label);
                binding.bandwidthGroup.setVisibility(View.GONE);
                binding.taGroup.setVisibility(View.GONE);
                binding.cqiGroup.setVisibility(View.GONE);
                binding.signalOneLabel.setText(R.string.ss_rsrp_label);
                binding.signalTwoLabel.setText(R.string.ss_rsrq_label);
                binding.signalTwoGroup.setVisibility(View.VISIBLE);
                binding.signalTwoLabel.setVisibility(View.VISIBLE);
                binding.signalThreeLabel.setText(R.string.ss_sinr_label);
                binding.signalThreeGroup.setVisibility(View.VISIBLE);
                binding.signalThreeLabel.setVisibility(View.VISIBLE);

                chartViewModel.setChartTitle("SS RSRP");
                chartViewModel.setCellularProtocol(protocol);
                chartViewModel.setMinRssi(-125); // -156 dBm is the lowest reportable value for SS RSRP
                chartViewModel.setMaxRssi(-73); // -31 dBm is the highest reportable value for SS RSRP
                break;
        }
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

    /**
     * The method responsible for handling a new batch of cellular records.
     *
     * @param cellularGroup The new batch of cellular records.
     */
    private void processCellularGroup(List<CellularRecordWrapper> cellularGroup)
    {
        if (cellularGroup.isEmpty()) clearCellularUi();

        final List<GsmRecordData> gsmNeighbors = new ArrayList<>();
        final List<UmtsRecordData> umtsNeighbors = new ArrayList<>();
        final List<LteRecordData> lteNeighbors = new ArrayList<>();
        final List<NrRecordData> nrNeighbors = new ArrayList<>();
        for (CellularRecordWrapper cellularRecord : cellularGroup)
        {
            if (CellularUtils.isServingCell(cellularRecord.cellularRecord))
            {
                sharedViewModel.updateLatestServingCellInfo(new ServingCellInfo(cellularRecord, subscriptionId));
            }

            switch (cellularRecord.cellularProtocol)
            {
                case NONE:
                    return;

                case GSM:
                    final GsmRecordData gsmData = ((GsmRecord) cellularRecord.cellularRecord).getData();
                    if (gsmData.hasServingCell() && gsmData.getServingCell().getValue())
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processGsmServingCell(gsmData);
                    } else
                    {
                        gsmNeighbors.add(gsmData);
                    }
                    break;

                case CDMA:
                    // We don't support CDMA since it is pretty much gone
                    break;

                case UMTS:
                    final UmtsRecordData umtsData = ((UmtsRecord) cellularRecord.cellularRecord).getData();
                    if (umtsData.hasServingCell() && umtsData.getServingCell().getValue())
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processUmtsServingCell(umtsData);
                    } else
                    {
                        umtsNeighbors.add(umtsData);
                    }
                    break;

                case LTE:
                    final LteRecordData lteData = ((LteRecord) cellularRecord.cellularRecord).getData();
                    if (lteData.hasServingCell() && lteData.getServingCell().getValue())
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processLteServingCell(lteData);
                    } else
                    {
                        lteNeighbors.add(lteData);
                    }
                    break;

                case NR:
                    final NrRecordData nrData = ((NrRecord) cellularRecord.cellularRecord).getData();
                    if (nrData.hasServingCell() && nrData.getServingCell().getValue())
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processNrServingCell(nrData, ((NrRecordWrapper) cellularRecord).bands);
                    } else
                    {
                        nrNeighbors.add(nrData);
                    }
                    break;
            }
        }

        processGsmNeighbors(gsmNeighbors);
        processUmtsNeighbors(umtsNeighbors);
        processLteNeighbors(lteNeighbors);
        processNrNeighbors(nrNeighbors);
    }

    /**
     * Takes in the GSM serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the GSM serving cell record.
     */
    private void processGsmServingCell(GsmRecordData data)
    {
        // Adding the signal value first so that any cell change markers will be drawn on top of the
        // new signal value.
        if (data.hasSignalStrength())
        {
            chartViewModel.addNewRssi((int) data.getSignalStrength().getValue());
        }

        viewModel.setCarrier(data.getProvider());
        viewModel.setMcc(data.hasMcc() ? String.valueOf(data.getMcc().getValue()) : "");
        viewModel.setMnc(data.hasMnc() ? String.valueOf(data.getMnc().getValue()) : "");
        viewModel.setAreaCode(data.hasLac() ? String.valueOf(data.getLac().getValue()) : "");
        viewModel.setCellId(data.hasCi() ? (long) data.getCi().getValue() : null);
        viewModel.setChannelNumber(data.hasArfcn() ? String.valueOf(data.getArfcn().getValue()) : "");
        viewModel.setPci(data.hasBsic() ? ParserUtils.bsicToString(data.getBsic().getValue()) : "");

        viewModel.setSignalOne(data.hasSignalStrength() ? (int) data.getSignalStrength().getValue() : null);
    }

    /**
     * Takes in the UMTS serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the UMTS serving cell record.
     */
    private void processUmtsServingCell(UmtsRecordData data)
    {
        // Adding the signal value first so that any cell change markers will be drawn on top of the
        // new signal value.
        if (data.hasSignalStrength())
        {
            chartViewModel.addNewRssi((int) data.getSignalStrength().getValue());
        }

        viewModel.setCarrier(data.getProvider());
        viewModel.setMcc(data.hasMcc() ? String.valueOf(data.getMcc().getValue()) : "");
        viewModel.setMnc(data.hasMnc() ? String.valueOf(data.getMnc().getValue()) : "");
        viewModel.setAreaCode(data.hasLac() ? String.valueOf(data.getLac().getValue()) : "");
        viewModel.setCellId(data.hasCid() ? (long) data.getCid().getValue() : null);
        viewModel.setChannelNumber(data.hasUarfcn() ? String.valueOf(data.getUarfcn().getValue()) : "");
        viewModel.setPci(data.hasPsc() ? String.valueOf(data.getPsc().getValue()) : "");

        viewModel.setSignalOne(data.hasSignalStrength() ? (int) data.getSignalStrength().getValue() : null);
        viewModel.setSignalTwo(data.hasRscp() ? (int) data.getRscp().getValue() : null);
    }

    /**
     * Takes in the LTE serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the LTE serving cell record.
     */
    private void processLteServingCell(LteRecordData data)
    {
        // Adding the signal value first so that any cell change markers will be drawn on top of the
        // new signal value.
        if (data.hasRsrp()) chartViewModel.addNewRssi((int) data.getRsrp().getValue());

        viewModel.setCarrier(data.getProvider());
        viewModel.setMcc(data.hasMcc() ? String.valueOf(data.getMcc().getValue()) : "");
        viewModel.setMnc(data.hasMnc() ? String.valueOf(data.getMnc().getValue()) : "");
        viewModel.setAreaCode(data.hasTac() ? String.valueOf(data.getTac().getValue()) : "");
        viewModel.setCellId(data.hasEci() ? (long) data.getEci().getValue() : null);

        if (data.hasEarfcn())
        {
            int earfcn = data.getEarfcn().getValue();
            int band = CellularUtils.downlinkEarfcnToBand(earfcn);
            viewModel.setChannelNumber(earfcn + " / " + (band == -1 ? "?" : band));
        } else
        {
            viewModel.setChannelNumber("");
        }

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
        viewModel.setCqi(data.hasCqi() ? String.valueOf(data.getCqi().getValue()) : "");

        viewModel.setSignalOne(data.hasRsrp() ? (int) data.getRsrp().getValue() : null);
        viewModel.setSignalTwo(data.hasRsrq() ? (int) data.getRsrq().getValue() : null);
        viewModel.setSignalThree(data.hasSnr() ? (int) data.getSnr().getValue() : null);
    }

    /**
     * Takes in the NR serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the NR serving cell record.
     */
    private void processNrServingCell(NrRecordData data, int[] bands)
    {
        // Adding the signal value first so that any cell change markers will be drawn on top of the
        // new signal value.
        if (data.hasSsRsrp()) chartViewModel.addNewRssi((int) data.getSsRsrp().getValue());

        viewModel.setCarrier(data.getProvider());
        viewModel.setMcc(data.hasMcc() ? String.valueOf(data.getMcc().getValue()) : "");
        viewModel.setMnc(data.hasMnc() ? String.valueOf(data.getMnc().getValue()) : "");
        viewModel.setAreaCode(data.hasTac() ? String.valueOf(data.getTac().getValue()) : "");
        viewModel.setCellId(data.hasNci() ? data.getNci().getValue() : null);

        if (bands.length > 0)
        {
            StringBuilder bandsString = new StringBuilder();
            for (int i = 0; i < bands.length; i++)
            {
                bandsString.append(bands[i]);
                if (i < bands.length - 1)
                {
                    bandsString.append(", ");
                }
            }

            if (data.hasNarfcn())
            {
                int narfcn = data.getNarfcn().getValue();
                viewModel.setChannelNumber(narfcn + " / " + bandsString);
            } else
            {
                viewModel.setChannelNumber("? / " + bandsString);
            }
        } else
        {
            viewModel.setChannelNumber(data.hasNarfcn() ? String.valueOf(data.getNci().getValue()) : "");
        }

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
        viewModel.setTa(data.hasTa() ? String.valueOf(data.getTa().getValue()) : "");

        viewModel.setSignalOne(data.hasSsRsrp() ? (int) data.getSsRsrp().getValue() : null);
        viewModel.setSignalTwo(data.hasSsRsrq() ? (int) data.getSsRsrq().getValue() : null);
        viewModel.setSignalThree(data.hasSsSinr() ? (int) data.getSsSinr().getValue() : null);
    }

    /**
     * Takes in the current group of UMTS neighbors, converts them to a {@link UmtsNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processGsmNeighbors(List<GsmRecordData> neighbors)
    {
        final TreeSet<GsmNeighbor> gsmNeighbors = neighbors.stream().map(data -> {
            GsmNeighbor.Builder builder = new GsmNeighbor.Builder();
            if (data.hasArfcn()) builder.arfcn(data.getArfcn().getValue());
            if (data.hasBsic()) builder.bsic(data.getBsic().getValue());
            if (data.hasSignalStrength()) builder.rssi((int) data.getSignalStrength().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setGsmNeighbors(gsmNeighbors);
    }

    /**
     * Takes in the current group of UMTS neighbors, converts them to a {@link UmtsNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processUmtsNeighbors(List<UmtsRecordData> neighbors)
    {
        final TreeSet<UmtsNeighbor> umtsNeighbors = neighbors.stream().map(data -> {
            UmtsNeighbor.Builder builder = new UmtsNeighbor.Builder();
            if (data.hasUarfcn()) builder.uarfcn(data.getUarfcn().getValue());
            if (data.hasPsc()) builder.psc(data.getPsc().getValue());
            if (data.hasRscp()) builder.rscp((int) data.getRscp().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setUmtsNeighbors(umtsNeighbors);
    }

    /**
     * Takes in the current group of LTE neighbors, converts them to an {@link LteNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processLteNeighbors(List<LteRecordData> neighbors)
    {
        final TreeSet<LteNeighbor> lteNeighbors = neighbors.stream().map(data -> {
            LteNeighbor.Builder builder = new LteNeighbor.Builder();
            if (data.hasEarfcn()) builder.earfcn(data.getEarfcn().getValue());
            if (data.hasPci()) builder.pci(data.getPci().getValue());
            if (data.hasRsrp()) builder.rsrp((int) data.getRsrp().getValue());
            if (data.hasRsrq()) builder.rsrq((int) data.getRsrq().getValue());
            if (data.hasTa()) builder.ta(data.getTa().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setLteNeighbors(lteNeighbors);
    }

    /**
     * Takes in the current group of LTE neighbors, converts them to an {@link NrNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processNrNeighbors(List<NrRecordData> neighbors)
    {
        final TreeSet<NrNeighbor> nrNeighbors = neighbors.stream().map(data -> {
            NrNeighbor.Builder builder = new NrNeighbor.Builder();
            if (data.hasNarfcn()) builder.narfcn(data.getNarfcn().getValue());
            if (data.hasPci()) builder.pci(data.getPci().getValue());
            if (data.hasSsRsrp()) builder.ssRsrp((int) data.getSsRsrp().getValue());
            if (data.hasSsRsrq()) builder.ssRsrq((int) data.getSsRsrq().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setNrNeighbors(nrNeighbors);
    }

    /**
     * Sets the Cell Identity.
     * <p>
     * For LTE, it also calculates and sets the  related fields.
     *
     * @param cellIdentity The cell identity to set and calculate the other values from.
     */
    private void updateCellIdentity(Long cellIdentity)
    {
        if (cellIdentity != null)
        {
            final int ci = cellIdentity.intValue();
            chartViewModel.setServingCellId(ci);
            binding.cid.setText(String.valueOf(ci));

            CellularProtocol servingCellProtocol = viewModel.getServingCellProtocol().getValue();
            if (servingCellProtocol == CellularProtocol.LTE)
            {
                // The Cell Identity is 28 bits long. The first 20 bits represent the Macro eNodeB ID. The last 8 bits
                // represent the sector.  Strip off the last 8 bits to get the Macro eNodeB ID.
                int eNodebId = CalculationUtils.getEnodebIdFromCellId(ci);
                binding.enbId.setText(String.valueOf(eNodebId));

                int sectorId = CalculationUtils.getSectorIdFromCellId(ci);
                binding.sectorId.setText(String.valueOf(sectorId));
            } else if (servingCellProtocol == CellularProtocol.UMTS)
            {
                // The UMTS CID is 28 bits long. The first 12 bits represent the RNC ID. The last 16 bits represent the
                // Short Cell ID. Strip off the last 16 bits to get the RNC ID.
                int umtsRnc = CalculationUtils.getUmtsRncFromCid(ci);
                binding.enbId.setText(String.valueOf(umtsRnc));

                int umtsShortCellId = CalculationUtils.getUmtsShortCellIdFromCid(ci);
                binding.sectorId.setText(String.valueOf(umtsShortCellId));
            }
        } else
        {
            binding.cid.setText("");
            binding.enbId.setText("");
            binding.sectorId.setText("");
        }
    }

    /**
     * Sets the provided value on the first Signal Strength display, and handles configuring the display with the
     * appropriate min and max value.
     *
     * @param signalValue The new signal value to set.
     */
    private void updateSignalStrengthOne(Integer signalValue)
    {
        final CellularProtocol protocol = viewModel.getServingCellProtocol().getValue();
        if (protocol == null) return;

        binding.signalOneGroup.setVisibility(signalValue == null ? View.GONE : View.VISIBLE);
        binding.signalOneValue.setText(signalValue != null ? getString(R.string.dbm_value_label, String.valueOf(signalValue)) : "");
        setSignalStrengthBar(binding.progressBarSignalOne, signalValue, protocol.getMinSignalOne(), protocol.getMaxNormalizedSignalOne());
    }

    /**
     * Sets the provided value on the second Signal Strength display, and handles configuring the display with the
     * appropriate min and max value.
     *
     * @param signalValue The new signal value to set.
     */
    private void updateSignalStrengthTwo(Integer signalValue)
    {
        final CellularProtocol protocol = viewModel.getServingCellProtocol().getValue();
        if (protocol == null) return;

        if (protocol == CellularProtocol.UMTS &&
                (signalValue == null || signalValue == RSCP_UNSET_VALUE_120 || signalValue == RSCP_UNSET_VALUE_24))
        {
            // Special handling for UMTS RSCP because devices seem to report the wrong value for "Unset"
            signalValue = null;
        }

        int valueLabelResourceId = R.string.db_value_label;
        // For UMTS, the second signal strength is RSCP, and the units is dbm
        if (protocol == CellularProtocol.UMTS) valueLabelResourceId = R.string.dbm_value_label;

        binding.signalTwoGroup.setVisibility(signalValue == null ? View.GONE : View.VISIBLE);
        binding.signalTwoValue.setText(signalValue != null ? getString(valueLabelResourceId, String.valueOf(signalValue)) : "");
        setSignalStrengthBar(binding.progressBarSignalTwo, signalValue, protocol.getMinSignalTwo(), protocol.getMaxNormalizedSignalTwo());
    }

    /**
     * Sets the provided value on the third Signal Strength display, and handles configuring the display with the
     * appropriate min and max value.
     *
     * @param signalValue The new signal value to set.
     */
    private void updateSignalStrengthThree(Integer signalValue)
    {
        final CellularProtocol protocol = viewModel.getServingCellProtocol().getValue();
        if (protocol == null) return;

        if (protocol != CellularProtocol.LTE && protocol != CellularProtocol.NR)
        {
            Timber.e("Somehow the protocol is incorrect for the third signal strength. protocol=%s", protocol);
            return;
        }

        binding.signalThreeGroup.setVisibility(signalValue == null ? View.GONE : View.VISIBLE);
        binding.signalThreeValue.setText(signalValue != null ? getString(R.string.db_value_label, String.valueOf(signalValue)) : "");
        setSignalStrengthBar(binding.progressBarSignalThree, signalValue, protocol.getMinSignalThree(), protocol.getMaxNormalizedSignalThree());
    }

    /**
     * Updates the first signal strength indicator UI element with the provided value. If the value is null, then
     * the current value is cleared and a blank UI element is show.
     *
     * @param signalValue The new signal value to set, or null if the current value should be cleared.
     */
    private void setSignalStrengthBar(RoundedProgressBar signalStrengthBar, Integer signalValue, int minValue, int maxNormalizedValue)
    {
        if (signalValue == null || maxNormalizedValue < 0)
        {
            signalStrengthBar.setProgressPercentage(0, false);
            return;
        }

        int normalizedValue = signalValue <= minValue ? 0 : Math.abs(minValue - signalValue);

        double scaleFactor = 100.0 / maxNormalizedValue;
        int scaledNormalizedValue = (int) (normalizedValue * scaleFactor);

        final int color = ColorUtils.getSignalColorForValue(normalizedValue, maxNormalizedValue);

        signalStrengthBar.setProgressDrawableColor(color);
        signalStrengthBar.setBackgroundColor(ColorUtils.getFadedColor(color));
        // We want there to be at least a small amount of the bar visible, so we set the minimum to 5%.
        signalStrengthBar.setProgressPercentage(Math.max(5, scaledNormalizedValue), true);
    }

    /**
     * Updates the first signal strength indicator UI element with the provided value. If the value is null, then
     * the current value is cleared and a blank UI element is show.
     *
     * @param signalValue The new signal value to set, or null if the current value should be cleared.
     */
    private void setSignalStrengthDonut(DonutProgressView signalStrengthBar, Integer signalValue, int minValue, int maxNormalizedValue)
    {
        if (signalValue == null)
        {
            signalStrengthBar.clear();
            return;
        }

        int normalizedValue = signalValue <= minValue ? 0 : Math.abs(minValue - signalValue);

        final int color = ColorUtils.getSignalColorForValue(normalizedValue, maxNormalizedValue);

        final DonutSection fillSection = new DonutSection("fill", color, normalizedValue);
        signalStrengthBar.setCap(maxNormalizedValue);
        signalStrengthBar.submitData(Collections.singletonList(fillSection));
    }

    /**
     * Given the newest set of  r neighbors, update the neighbors table view.
     *
     * @param neighbors The latest batch of NR neighbors.
     */
    private void updateNrNeighborsView(SortedSet<NrNeighbor> neighbors)
    {
        final Context context = getContext();
        if (context == null) return;

        if (neighbors.isEmpty())
        {
            binding.nrNeighborsGroup.setVisibility(View.GONE);
            return;
        }

        binding.nrNeighborsGroup.setVisibility(View.VISIBLE);

        final TableLayout neighborsTable = binding.nrNeighborsTable;

        neighborsTable.removeAllViews();

        final TableRow headerRow = new TableRow(context);
        addHeaderToRow(context, headerRow, getString(R.string.narfcn_label));
        addHeaderToRow(context, headerRow, getString(R.string.pci_label));
        addHeaderToRow(context, headerRow, getString(R.string.ss_rsrp_label));
        addHeaderToRow(context, headerRow, getString(R.string.ss_rsrq_label));
        neighborsTable.addView(headerRow);

        for (NrNeighbor neighbor : neighbors)
        {
            final TableRow row = new TableRow(context);

            addValueToRow(context, row, neighbor.narfcn);
            addValueToRow(context, row, neighbor.pci);
            addValueToRow(context, row, neighbor.ssRsrp);
            addValueToRow(context, row, neighbor.ssRsrq);

            neighborsTable.addView(row);
        }
    }

    /**
     * Given the newest set of LTE neighbors, update the neighbors table view.
     *
     * @param neighbors The latest batch of LTE neighbors.
     */
    private void updateLteNeighborsView(SortedSet<LteNeighbor> neighbors)
    {
        final Context context = getContext();
        if (context == null) return;

        if (neighbors.isEmpty())
        {
            binding.lteNeighborsGroup.setVisibility(View.GONE);
            return;
        }

        binding.lteNeighborsGroup.setVisibility(View.VISIBLE);

        final TableLayout lteNeighborsTable = binding.lteNeighborsTable;

        lteNeighborsTable.removeAllViews();

        final TableRow headerRow = new TableRow(context);
        addHeaderToRow(context, headerRow, getString(R.string.earfcn_label));
        addHeaderToRow(context, headerRow, getString(R.string.pci_label));
        addHeaderToRow(context, headerRow, getString(R.string.rsrp_label));
        addHeaderToRow(context, headerRow, getString(R.string.rsrq_label));
        addHeaderToRow(context, headerRow, getString(R.string.ta_label));
        lteNeighborsTable.addView(headerRow);

        for (LteNeighbor neighbor : neighbors)
        {
            final TableRow row = new TableRow(context);

            addEarfcnValueToRow(context, row, neighbor.earfcn);
            addValueToRow(context, row, neighbor.pci);
            addValueToRow(context, row, neighbor.rsrp);
            addValueToRow(context, row, neighbor.rsrq);
            addValueToRow(context, row, neighbor.ta);

            lteNeighborsTable.addView(row);
        }
    }

    /**
     * Given the newest set of UMTS neighbors, update the neighbors table view.
     *
     * @param neighbors The latest batch of UMTS neighbors.
     */
    private void updateUmtsNeighborsView(SortedSet<UmtsNeighbor> neighbors)
    {
        final Context context = getContext();
        if (context == null) return;

        final TableLayout umtsNeighborsTable = binding.umtsNeighborsTable;

        if (neighbors.isEmpty())
        {
            binding.umtsNeighborsGroup.setVisibility(View.GONE);
            return;
        }

        binding.umtsNeighborsGroup.setVisibility(View.VISIBLE);

        umtsNeighborsTable.removeAllViews();

        final TableRow headerRow = new TableRow(context);
        addHeaderToRow(context, headerRow, getString(R.string.uarfcn_label));
        addHeaderToRow(context, headerRow, getString(R.string.psc_label));
        addHeaderToRow(context, headerRow, getString(R.string.rscp_label));
        umtsNeighborsTable.addView(headerRow);

        for (UmtsNeighbor neighbor : neighbors)
        {
            final TableRow row = new TableRow(context);

            addValueToRow(context, row, neighbor.uarfcn);
            addValueToRow(context, row, neighbor.psc);
            addValueToRow(context, row, neighbor.rscp);

            umtsNeighborsTable.addView(row);
        }
    }

    /**
     * Given the newest set of GSM neighbors, update the neighbors table view.
     *
     * @param neighbors The latest batch of GSM neighbors.
     */
    private void updateGsmNeighborsView(SortedSet<GsmNeighbor> neighbors)
    {
        final Context context = getContext();
        if (context == null) return;

        final TableLayout gsmNeighborsTable = binding.gsmNeighborsTable;

        if (neighbors.isEmpty())
        {
            binding.gsmNeighborsGroup.setVisibility(View.GONE);
            return;
        }

        binding.gsmNeighborsGroup.setVisibility(View.VISIBLE);

        gsmNeighborsTable.removeAllViews();

        final TableRow headerRow = new TableRow(context);
        addHeaderToRow(context, headerRow, getString(R.string.arfcn_label));
        addHeaderToRow(context, headerRow, getString(R.string.bsic_label));
        addHeaderToRow(context, headerRow, getString(R.string.rssi_label));
        gsmNeighborsTable.addView(headerRow);

        for (GsmNeighbor neighbor : neighbors)
        {
            final TableRow row = new TableRow(context);

            addValueToRow(context, row, neighbor.arfcn);
            addValueToRow(context, row, neighbor.bsic);
            addValueToRow(context, row, neighbor.rssi);

            gsmNeighborsTable.addView(row);
        }
    }

    /**
     * Set the provided EARFCN in a TextView and then adds it to the row. Also adds the band number.
     *
     * @param context The context to use for creating the TextView.
     * @param row     The row to add the cell to.
     * @param earfcn  The earfcn value to place in the cell. If the value is
     *                {@link com.craxiom.networksurvey.constants.NetworkSurveyConstants#UNSET_VALUE},
     *                then an empty string is placed in the cell.
     */
    private void addEarfcnValueToRow(Context context, TableRow row, int earfcn)
    {
        final String cellText;
        if (earfcn == NetworkSurveyConstants.UNSET_VALUE)
        {
            // We need to add an empty text view to make sure the columns align correctly
            cellText = "";
        } else
        {
            int band = CellularUtils.downlinkEarfcnToBand(earfcn);
            cellText = earfcn + " / " + (band == -1 ? "?" : band);
        }

        final TextView view = new TextView(context, null, 0, R.style.TableText);
        view.setText(cellText);
        row.addView(view);
    }

    /**
     * Set the provided column header in a TextView and then adds it to the row.
     *
     * @param context The context to use for creating the TextView.
     * @param row     The header row to add the cell to.
     * @param header  The value to place in the header column.
     */
    private void addHeaderToRow(Context context, TableRow row, String header)
    {
        final TextView view = new TextView(context, null, 0, R.style.ColumnTitleText);
        view.setText(header);
        row.addView(view);
    }

    /**
     * Set the provided value in a TextView and then adds it to the row.
     *
     * @param context The context to use for creating the TextView.
     * @param row     The row to add the cell to.
     * @param value   The value to place in the cell. If the value is
     *                {@link com.craxiom.networksurvey.constants.NetworkSurveyConstants#UNSET_VALUE},
     *                then an empty string is placed in the cell.
     */
    private void addValueToRow(Context context, TableRow row, int value)
    {
        final String cellText;
        if (value == NetworkSurveyConstants.UNSET_VALUE)
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

    /**
     * Displays a dialog with a description about the Override Network.
     */
    private void showOverrideNetworkInfoDialog()
    {
        final Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(getString(R.string.override_network_info_title));
        alertBuilder.setMessage(getString(R.string.override_network_explanation));
        alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
        });
        alertBuilder.create().show();
    }

    /**
     * Displays a dialog with some information about cellular terms.
     */
    private void showCellularInfoDialog()
    {
        final Context context = getContext();
        if (context == null) return;

        CellularProtocol protocol = viewModel.getServingCellProtocol().getValue();

        // Default to LTE as a fallback
        String cellularInfoTitle = getString(R.string.lte_info_description);
        CharSequence cellularInfoBody = getString(R.string.lte_cellular_terms_explanation);

        if (protocol == null) protocol = CellularProtocol.LTE;
        switch (protocol)
        {
            case NONE, LTE ->
            {
                cellularInfoTitle = getString(R.string.lte_info_description);
                cellularInfoBody = getText(R.string.lte_cellular_terms_explanation);
            }
            case GSM ->
            {
                cellularInfoTitle = getString(R.string.gsm_info_description);
                cellularInfoBody = getText(R.string.gsm_cellular_terms_explanation);
            }
            case CDMA ->
            {
                cellularInfoTitle = "How did you find CDMA?";
                cellularInfoBody = "CDMA is no longer supported. I am impressed you were able to find a CDMA network! Honestly, send me an email at craxiomdev@gmail.com and let me know where you found it.";
            }
            case UMTS ->
            {
                cellularInfoTitle = getString(R.string.umts_info_description);
                cellularInfoBody = getText(R.string.umts_cellular_terms_explanation);
            }
            case NR ->
            {
                cellularInfoTitle = getString(R.string.nr_info_description);
                cellularInfoBody = getText(R.string.nr_cellular_terms_explanation);
            }
        }

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(cellularInfoTitle);
        alertBuilder.setMessage(cellularInfoBody);
        alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
        });
        alertBuilder.create().show();
    }
}
