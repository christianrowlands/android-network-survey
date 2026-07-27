package com.craxiom.networksurvey.fragments;

import static com.craxiom.networksurvey.ui.ASignalChartViewModelKt.UNKNOWN_RSSI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
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
import com.craxiom.networksurvey.fragments.model.CarrierAggregationViewState;
import com.craxiom.networksurvey.fragments.model.CellularViewModel;
import com.craxiom.networksurvey.fragments.model.GsmNeighbor;
import com.craxiom.networksurvey.fragments.model.LteNeighbor;
import com.craxiom.networksurvey.fragments.model.NrNeighbor;
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellViewState;
import com.craxiom.networksurvey.fragments.model.UmtsNeighbor;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.model.NetworkTechnologyInfo;
import com.craxiom.networksurvey.model.NrRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.ui.cellular.CellularChartViewModel;
import com.craxiom.networksurvey.ui.cellular.ComposeFunctions;
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.util.CalculationUtils;
import com.craxiom.networksurvey.util.CellularBandwidthUtils;
import com.craxiom.networksurvey.util.CellularUtils;
import com.craxiom.networksurvey.util.ColorUtils;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.ParserUtils;
import com.craxiom.networksurvey.util.TelephonyStateUtils;
import com.mackhartley.roundedprogressbar.RoundedProgressBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import timber.log.Timber;

/**
 * A fragment for displaying the latest cellular network details to the user.
 *
 * @since 1.6.0 (It really came earlier, but was minimal until the 1.6.0 rewrite.
 */
public class NetworkDetailsFragment extends AServiceDataFragment implements ICellularSurveyRecordListener
{
    public static final String SUBSCRIPTION_ID_KEY = "subscription_id";

    // The next two values have been added because certain devices don't follow the Interger#MAX_VALUE approach defined
    // in the Android API. The phone is supposed to report Interger#MAX_VALUE to indicate "Unknown/Unset" values, but
    // Pixel devices seem to report -120 all the time for UMTS RSCP, and Samsung devices seem to report -24 for UMTS RSCP.
    // These values are technically valid and filtering them out is an incorrect thing to do, but it is all I can think
    // of right now to prevent invalid values from being reported.
    private static final int RSCP_UNSET_VALUE_120 = -120;
    private static final int RSCP_UNSET_VALUE_24 = -24;

    // Separator between a technology and its qualifier ("NR · Standalone") and between a pill's
    // label and value ("Voice · VoNR").
    private static final String TECH_SEPARATOR = " · ";

    private int subscriptionId;

    private FragmentNetworkDetailsBinding binding;
    private CellularViewModel viewModel;
    private CellularChartViewModel chartViewModel;
    private SharedViewModel sharedViewModel;
    private AirplaneModeReceiver airplaneModeReceiver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        //noinspection DataFlowIssue
        subscriptionId = args.getInt(SUBSCRIPTION_ID_KEY, -1);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        binding = FragmentNetworkDetailsBinding.inflate(inflater);

        viewModel = new ViewModelProvider(requireActivity()).get(getClass().getName() + subscriptionId, CellularViewModel.class);
        chartViewModel = new ViewModelProvider(requireActivity()).get(getClass().getName() + "cellular_chart" + subscriptionId, CellularChartViewModel.class);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

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

        // Unregister airplane mode receiver
        if (airplaneModeReceiver != null)
        {
            try
            {
                requireContext().unregisterReceiver(airplaneModeReceiver);
                Timber.d("Unregistered airplane mode receiver");
            } catch (IllegalArgumentException e)
            {
                Timber.w(e, "Airplane mode receiver was not registered");
            }
            airplaneModeReceiver = null;
        }

        super.onPause();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // Register airplane mode receiver
        airplaneModeReceiver = new AirplaneModeReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        requireContext().registerReceiver(airplaneModeReceiver, filter);

        // Check initial airplane mode state
        boolean isAirplaneModeOn = isAirplaneModeOn(requireContext());
        viewModel.setAirplaneModeActive(isAirplaneModeOn);
        if (isAirplaneModeOn)
        {
            clearCellularUi();
        }

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

        service.runSingleCellularScan();
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
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
    public void onNetworkType(NetworkTechnologyInfo technologyInfo, int subscriptionId)
    {
        // The records are for a different SIM, so ignore them because another
        // NetworkDetailsFragment instance will handle them.
        if (this.subscriptionId != subscriptionId) return;

        final TelephonyStateUtils.HeroResult hero = TelephonyStateUtils.deriveHero(
                technologyInfo.nrMode(), technologyInfo.baseDataRat(), technologyInfo.registrationRows());
        final String heroText = heroDisplay(hero);

        viewModel.setHeroText(heroText);
        viewModel.setHeroColorId(heroColorId(hero.state()));

        // The pill row supports a live technology hero. On the degraded No Service and Unknown
        // states the pills would all read "None"/"Unknown"/"N/A", which is noise the hero already
        // conveys, so hide the whole row (a null voice value hides it). On every live state show all
        // three pills with explicit values so nothing appears or disappears during normal use.
        if (hero.state() == TelephonyStateUtils.HeroState.NO_SERVICE
                || hero.state() == TelephonyStateUtils.HeroState.UNKNOWN)
        {
            viewModel.setVoicePillValue(null);
            viewModel.setDataPillValue(null);
            viewModel.setBrandingPillValue(null);
        } else
        {
            viewModel.setVoicePillValue(technologyInfo.voiceDisplay());
            viewModel.setDataPillValue(technologyInfo.dataDisplay());
            // Branding shows the carrier's marketing override label, or a uniform "None" when there
            // is no branding override (or it is not yet known). Gating on the raw override int keeps
            // the value consistent across API levels and scan paths; overrideDisplay itself is "N/A"
            // or "Unknown" on some paths, which would not match the info dialog copy.
            viewModel.setBrandingPillValue(
                    TelephonyStateUtils.isBrandingOverride(technologyInfo.overrideNetworkType())
                            ? technologyInfo.overrideDisplay()
                            : getString(R.string.branding_none));
        }

        viewModel.setCarrierAggregation(carrierAggregationViewState(technologyInfo.cellBandwidthsKhz()));
    }

    /**
     * @return The hero technology line for the top card (e.g. "NR · Standalone").
     */
    private String heroDisplay(TelephonyStateUtils.HeroResult hero)
    {
        return switch (hero.state())
        {
            case NR_STANDALONE -> NetworkSurveyConstants.NR + TECH_SEPARATOR
                    + getString(R.string.nr_mode_standalone);
            case NR_NON_STANDALONE -> NetworkSurveyConstants.LTE + " + " + NetworkSurveyConstants.NR
                    + TECH_SEPARATOR + NetworkSurveyConstants.NSA;
            case DATA_RAT -> CalculationUtils.getNetworkType(hero.rat());
            case NO_SERVICE -> getString(R.string.hero_no_service);
            case UNKNOWN -> getString(R.string.unknown);
        };
    }

    /**
     * @return The hero text color resource: accent for a live technology, orange for no service,
     * and faded for unknown, so a bad state never renders in the "all good" accent color.
     */
    private int heroColorId(TelephonyStateUtils.HeroState state)
    {
        return switch (state)
        {
            case NO_SERVICE -> R.color.rssi_orange;
            case UNKNOWN -> R.color.fadedText;
            default -> R.color.colorAccent;
        };
    }

    /**
     * @return The carrier aggregation view state (per-carrier chip labels plus the summary line),
     * or null when fewer than two valid component carriers are reported (so the section is hidden).
     */
    private CarrierAggregationViewState carrierAggregationViewState(int[] cellBandwidthsKhz)
    {
        final int[] validKhz = CellularBandwidthUtils.validBandwidthsKhz(cellBandwidthsKhz);
        if (validKhz.length <= 1) return null;

        final List<String> chipLabels = new ArrayList<>(validKhz.length);
        for (int khz : validKhz)
        {
            chipLabels.add(getString(R.string.mhz_value_label, CellularBandwidthUtils.formatBandwidthMhz(khz)));
        }
        final String summary = getString(R.string.carrier_aggregation_summary, validKhz.length,
                CellularBandwidthUtils.formatBandwidthMhz(CellularBandwidthUtils.aggregateBandwidthKhz(validKhz)));
        return new CarrierAggregationViewState(chipLabels, summary);
    }

    /**
     * Initialize the UI listeners for the various buttons and other UI elements.
     */
    private void initializeUiListeners()
    {
        binding.cellularInfoIcon.setOnClickListener(c -> showCellularInfoDialog());
        binding.networkTechnologyInfoIcon.setOnClickListener(c -> showNetworkTechnologyInfoDialog());
        binding.nrDetailsInfoIcon.setOnClickListener(c -> showNrDetailsInfoDialog());
        setupCopyOnLongPress();
    }

    /**
     * Wires up long-press-to-copy on each cellular detail value TextView. This replaces the
     * textIsSelectable behavior (which was removed to prevent the ViewPager2 + Compose reentrant
     * layout crash) and provides a cleaner single-value copy UX.
     */
    private void setupCopyOnLongPress()
    {
        NsUtils.setupCopyOnLongPress(
                binding.networkTechnologyHero,
                binding.plmn, binding.tac, binding.cid,
                binding.enbId, binding.sectorId, binding.earfcn,
                binding.pci, binding.band, binding.frequency,
                binding.lteBand, binding.bandwidth, binding.cqi, binding.ta,
                binding.nrBand, binding.nrFrequency, binding.nrPci, binding.nrNarfcn
        );
    }

    /**
     * Initialize the model view observers. These observers look for changes to the model view
     * values, and then update the UI based on any changes.
     */
    private void initializeObservers()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        viewModel.getCarrier().observe(viewLifecycleOwner, this::updateCarrier);
        viewModel.getHeroText().observe(viewLifecycleOwner, hero -> binding.networkTechnologyHero.setText(hero));
        viewModel.getHeroColorId().observe(viewLifecycleOwner, this::updateHeroColor);
        viewModel.getVoicePillValue().observe(viewLifecycleOwner, this::updateVoicePill);
        viewModel.getDataPillValue().observe(viewLifecycleOwner,
                value -> updatePill(binding.dataPill, R.string.pill_label_data, value));
        viewModel.getBrandingPillValue().observe(viewLifecycleOwner,
                value -> updatePill(binding.brandingPill, R.string.pill_label_branding, value));
        viewModel.getCarrierAggregation().observe(viewLifecycleOwner, this::updateCarrierAggregation);
        viewModel.getNrSecondaryCell().observe(viewLifecycleOwner, this::updateNrDetailsCard);

        viewModel.getAirplaneModeActive().observe(viewLifecycleOwner, this::updateAirplaneModeStatus);

        viewModel.getServingCellProtocol().observe(viewLifecycleOwner, this::updateServingCellProtocol);

        viewModel.getMcc().observe(viewLifecycleOwner, s -> binding.plmn.setText(getString(R.string.mcc_mnc_value, s, viewModel.getMnc().getValue())));
        viewModel.getMnc().observe(viewLifecycleOwner, s -> binding.plmn.setText(getString(R.string.mcc_mnc_value, viewModel.getMcc().getValue(), s)));
        viewModel.getAreaCode().observe(viewLifecycleOwner, s -> binding.tac.setText(s));
        viewModel.getCellId().observe(viewLifecycleOwner, this::updateCellIdentity);
        viewModel.getChannelNumber().observe(viewLifecycleOwner, s -> binding.earfcn.setText(s));
        viewModel.getFrequency().observe(viewLifecycleOwner, s -> binding.frequency.setText(s));
        viewModel.getBand().observe(viewLifecycleOwner, s -> binding.band.setText(s));
        viewModel.getLteBand().observe(viewLifecycleOwner, s -> binding.lteBand.setText(s));

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

        viewModel.getCarrier().removeObservers(viewLifecycleOwner);
        viewModel.getHeroText().removeObservers(viewLifecycleOwner);
        viewModel.getHeroColorId().removeObservers(viewLifecycleOwner);
        viewModel.getVoicePillValue().removeObservers(viewLifecycleOwner);
        viewModel.getDataPillValue().removeObservers(viewLifecycleOwner);
        viewModel.getBrandingPillValue().removeObservers(viewLifecycleOwner);
        viewModel.getCarrierAggregation().removeObservers(viewLifecycleOwner);
        viewModel.getNrSecondaryCell().removeObservers(viewLifecycleOwner);

        viewModel.getAirplaneModeActive().removeObservers(viewLifecycleOwner);

        viewModel.getServingCellProtocol().removeObservers(viewLifecycleOwner);

        viewModel.getMcc().removeObservers(viewLifecycleOwner);
        viewModel.getMnc().removeObservers(viewLifecycleOwner);
        viewModel.getAreaCode().removeObservers(viewLifecycleOwner);
        viewModel.getCellId().removeObservers(viewLifecycleOwner);
        viewModel.getChannelNumber().removeObservers(viewLifecycleOwner);
        viewModel.getFrequency().removeObservers(viewLifecycleOwner);
        viewModel.getBand().removeObservers(viewLifecycleOwner);
        viewModel.getLteBand().removeObservers(viewLifecycleOwner);

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
        viewModel.setFrequency("");
        viewModel.setBand("");
        viewModel.setLteBand("");

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

        // Carrier aggregation is a property of the serving cell, so clear it when the serving cell
        // goes away. The top-card fields (hero and pills) are intentionally left alone here:
        // onNetworkType refreshes them on every scan before this runs.
        viewModel.setCarrierAggregation(null);

        viewModel.setNrSecondaryCell(null);
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
                binding.frequencyRow.setVisibility(View.GONE);
                binding.lteBandRow.setVisibility(View.GONE);

                chartViewModel.setChartTitle("RSSI");
                chartViewModel.setCellularProtocol(protocol);
                break;

            case GSM:
                binding.tacLabel.setText(R.string.lac_label);
                binding.enbIdGroup.setVisibility(View.GONE);
                binding.sectorIdGroup.setVisibility(View.GONE);
                binding.earfcnLabel.setText(R.string.arfcn_label);
                binding.frequencyRow.setVisibility(View.GONE);
                binding.lteBandRow.setVisibility(View.GONE);
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
                binding.frequencyRow.setVisibility(View.GONE);
                binding.lteBandRow.setVisibility(View.GONE);
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
                binding.frequencyRow.setVisibility(View.GONE);
                binding.lteBandRow.setVisibility(View.GONE);
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
                binding.earfcnLabel.setText(R.string.earfcn_label);
                binding.frequencyRow.setVisibility(View.GONE);
                binding.lteBandRow.setVisibility(View.VISIBLE);
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
                binding.earfcnLabel.setText(R.string.narfcn_label);
                binding.frequencyRow.setVisibility(View.VISIBLE);
                binding.lteBandRow.setVisibility(View.GONE);
                binding.pciLabel.setText(R.string.pci_label);
                binding.bandwidthGroup.setVisibility(View.GONE);
                binding.taGroup.setVisibility(View.VISIBLE);
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
        final List<NrRecordWrapper> nrNonServing = new ArrayList<>();
        for (CellularRecordWrapper cellularRecord : cellularGroup)
        {
            if (CellularUtils.isServingCell(cellularRecord.cellularRecord))
            {
                sharedViewModel.updateLatestServingCellInfo(new ServingCellInfo(cellularRecord, subscriptionId, System.currentTimeMillis()));
            }

            switch (cellularRecord.cellularProtocol)
            {
                case NONE:
                    continue;

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
                        nrNonServing.add((NrRecordWrapper) cellularRecord);
                    }
                    break;
            }
        }

        // A cell the device reports as SECONDARY_SERVING is one the phone is actively using: on
        // 5G NSA it is the NR cell carrying the 5G data (the phone is registered on the LTE
        // anchor, so it shows up as a non-serving record), and on 5G SA with NR carrier
        // aggregation it is an NR SCell. Promote it to the NR details card and keep it out of the
        // neighbors table. Devices that report no connection status simply do not show the card.
        final NrRecordWrapper secondaryNrCell = CellularUtils.selectSecondaryServingNrCell(nrNonServing);
        if (secondaryNrCell != null) nrNonServing.remove(secondaryNrCell);
        viewModel.setNrSecondaryCell(secondaryNrCell != null ? buildNrSecondaryCellViewState(secondaryNrCell) : null);

        processGsmNeighbors(gsmNeighbors);
        processUmtsNeighbors(umtsNeighbors);
        processLteNeighbors(lteNeighbors);
        processNrNeighbors(nrNonServing.stream()
                .map(wrapper -> ((NrRecord) wrapper.cellularRecord).getData())
                .collect(Collectors.toList()));
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
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
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
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
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
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
        viewModel.setAreaCode(data.hasTac() ? String.valueOf(data.getTac().getValue()) : "");
        viewModel.setCellId(data.hasEci() ? (long) data.getEci().getValue() : null);

        // Set EARFCN without band information
        viewModel.setChannelNumber(data.hasEarfcn() ? String.valueOf(data.getEarfcn().getValue()) : "");

        // Set LTE band field with band number and name
        if (data.hasEarfcn())
        {
            int earfcn = data.getEarfcn().getValue();
            int bandNumber = CellularUtils.downlinkEarfcnToBand(earfcn);
            if (bandNumber != -1)
            {
                String bandName = CellularUtils.getLteBandName(bandNumber);
                if (bandName != null)
                {
                    viewModel.setLteBand(bandNumber + " (" + bandName + ")");
                } else
                {
                    viewModel.setLteBand(String.valueOf(bandNumber));
                }
            } else
            {
                viewModel.setLteBand("");
            }
        } else
        {
            viewModel.setLteBand("");
        }

        viewModel.setPci(data.hasPci() ? formatPci(data.getPci().getValue()) : "");
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
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
        viewModel.setAreaCode(data.hasTac() ? String.valueOf(data.getTac().getValue()) : "");
        viewModel.setCellId(data.hasNci() ? data.getNci().getValue() : null);

        // Set NARFCN without band information
        viewModel.setChannelNumber(data.hasNarfcn() ? String.valueOf(data.getNarfcn().getValue()) : "");

        viewModel.setBand(CellularUtils.formatNrBands(bands));

        viewModel.setPci(data.hasPci() ? formatPci(data.getPci().getValue()) : "");

        viewModel.setFrequency(data.hasNarfcn() ? formatNrFrequency(data.getNarfcn().getValue()) : "");

        viewModel.setTa(data.hasTa() ? String.valueOf(data.getTa().getValue()) : "");

        viewModel.setSignalOne(data.hasSsRsrp() ? (int) data.getSsRsrp().getValue() : null);
        viewModel.setSignalTwo(data.hasSsRsrq() ? (int) data.getSsRsrq().getValue() : null);
        viewModel.setSignalThree(data.hasSsSinr() ? (int) data.getSsSinr().getValue() : null);
    }

    /**
     * @return The PCI display value including the Primary and Secondary Sync Sequence breakdown,
     * e.g. "146 (2/48)".
     */
    private String formatPci(int pci)
    {
        final int primarySyncSequence = CalculationUtils.getPrimarySyncSequence(pci);
        final int secondarySyncSequence = CalculationUtils.getSecondarySyncSequence(pci);
        return pci + " (" + primarySyncSequence + "/" + secondarySyncSequence + ")";
    }

    /**
     * @return The NR frequency display value in MHz for the provided NARFCN (e.g. "3709.920 MHz"),
     * or an empty string when the NARFCN is not in a valid range.
     */
    private String formatNrFrequency(int narfcn)
    {
        final double frequencyMhz = CellularUtils.narfcnToFrequencyMhz(narfcn);
        if (frequencyMhz <= 0) return "";

        return getString(R.string.mhz_value_label, String.format(Locale.US, "%.3f", frequencyMhz));
    }

    /**
     * Builds the view state for the NR Secondary Cell details card from the NR record the device
     * reported as SECONDARY_SERVING (the 5G NSA data leg, or an NR CA SCell under SA).
     */
    private NrSecondaryCellViewState buildNrSecondaryCellViewState(NrRecordWrapper wrapper)
    {
        final NrRecordData data = ((NrRecord) wrapper.cellularRecord).getData();

        return new NrSecondaryCellViewState(
                CellularUtils.formatNrBands(wrapper.bands),
                data.hasNarfcn() ? formatNrFrequency(data.getNarfcn().getValue()) : "",
                data.hasPci() ? formatPci(data.getPci().getValue()) : "",
                data.hasNarfcn() ? String.valueOf(data.getNarfcn().getValue()) : "",
                data.hasSsRsrp() ? (int) data.getSsRsrp().getValue() : null,
                data.hasSsRsrq() ? (int) data.getSsRsrq().getValue() : null,
                data.hasSsSinr() ? (int) data.getSsSinr().getValue() : null);
    }

    /**
     * Shows or hides the NR Secondary Cell details card. The card is only visible while the
     * device reports an NR cell as SECONDARY_SERVING (a null view state hides it).
     */
    private void updateNrDetailsCard(NrSecondaryCellViewState state)
    {
        if (state == null)
        {
            binding.nrDetailsCardView.setVisibility(View.GONE);
            return;
        }

        binding.nrBand.setText(state.band());
        binding.nrFrequency.setText(state.frequency());
        binding.nrPci.setText(state.pci());
        binding.nrNarfcn.setText(state.narfcn());

        final CellularProtocol protocol = CellularProtocol.NR;

        // The label hides with its value group so a missing measurement cannot leave an orphaned
        // caption floating over blank space (matching the serving cell card's behavior).
        final Integer ssRsrp = state.ssRsrp();
        final int ssRsrpVisibility = ssRsrp == null ? View.INVISIBLE : View.VISIBLE;
        binding.nrSsRsrpGroup.setVisibility(ssRsrpVisibility);
        binding.nrSsRsrpLabel.setVisibility(ssRsrpVisibility);
        binding.nrSsRsrpValue.setText(ssRsrp != null ? getString(R.string.dbm_value_label, String.valueOf(ssRsrp)) : "");
        setSignalStrengthBar(binding.progressBarNrSsRsrp, ssRsrp, protocol.getMinSignalOne(), protocol.getMaxNormalizedSignalOne());

        final Integer ssRsrq = state.ssRsrq();
        final int ssRsrqVisibility = ssRsrq == null ? View.INVISIBLE : View.VISIBLE;
        binding.nrSsRsrqGroup.setVisibility(ssRsrqVisibility);
        binding.nrSsRsrqLabel.setVisibility(ssRsrqVisibility);
        binding.nrSsRsrqValue.setText(ssRsrq != null ? getString(R.string.db_value_label, String.valueOf(ssRsrq)) : "");
        setSignalStrengthBar(binding.progressBarNrSsRsrq, ssRsrq, protocol.getMinSignalTwo(), protocol.getMaxNormalizedSignalTwo());

        final Integer ssSinr = state.ssSinr();
        final int ssSinrVisibility = ssSinr == null ? View.INVISIBLE : View.VISIBLE;
        binding.nrSsSinrGroup.setVisibility(ssSinrVisibility);
        binding.nrSsSinrLabel.setVisibility(ssSinrVisibility);
        binding.nrSsSinrValue.setText(ssSinr != null ? getString(R.string.db_value_label, String.valueOf(ssSinr)) : "");
        setSignalStrengthBar(binding.progressBarNrSsSinr, ssSinr, protocol.getMinSignalThree(), protocol.getMaxNormalizedSignalThree());

        binding.nrDetailsCardView.setVisibility(View.VISIBLE);
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

        binding.signalOneGroup.setVisibility(signalValue == null ? View.INVISIBLE : View.VISIBLE);
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

        binding.signalTwoGroup.setVisibility(signalValue == null ? View.INVISIBLE : View.VISIBLE);
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

        if (signalValue != null && protocol != CellularProtocol.LTE && protocol != CellularProtocol.NR)
        {
            Timber.e("Somehow the protocol is incorrect for the third signal strength. protocol=%s", protocol);
            return;
        }

        binding.signalThreeGroup.setVisibility(signalValue == null ? View.INVISIBLE : View.VISIBLE);
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
            updateNeighborsCardVisibility();
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

        updateNeighborsCardVisibility();

        // Notify parent fragment to scroll if needed
        notifyParentOfNeighborUpdate();
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
            updateNeighborsCardVisibility();
            return;
        }

        binding.lteNeighborsGroup.setVisibility(View.VISIBLE);

        final TableLayout lteNeighborsTable = binding.lteNeighborsTable;

        lteNeighborsTable.removeAllViews();

        final TableRow headerRow = new TableRow(context);
        addHeaderToRow(context, headerRow, getString(R.string.earfcn_band_label));
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

        updateNeighborsCardVisibility();

        // Notify parent fragment to scroll if needed
        notifyParentOfNeighborUpdate();
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
            updateNeighborsCardVisibility();
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

        updateNeighborsCardVisibility();

        // Notify parent fragment to scroll if needed
        notifyParentOfNeighborUpdate();
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
            updateNeighborsCardVisibility();
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

        updateNeighborsCardVisibility();

        // Notify parent fragment to scroll if needed
        notifyParentOfNeighborUpdate();
    }

    /**
     * Hides or shows the neighbors card based on whether any neighbor sub-groups are visible.
     */
    private void updateNeighborsCardVisibility()
    {
        boolean anyVisible = binding.nrNeighborsGroup.getVisibility() == View.VISIBLE
                || binding.lteNeighborsGroup.getVisibility() == View.VISIBLE
                || binding.umtsNeighborsGroup.getVisibility() == View.VISIBLE
                || binding.gsmNeighborsGroup.getVisibility() == View.VISIBLE;
        binding.neighborsCardView.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
    }

    /**
     * Notifies the parent MainCellularFragment that neighbor data was updated,
     * so it can scroll to bottom if the user was already at bottom.
     */
    private void notifyParentOfNeighborUpdate()
    {
        androidx.fragment.app.Fragment parent = getParentFragment();
        if (parent instanceof MainCellularFragment)
        {
            ((MainCellularFragment) parent).onNeighborDataUpdated();
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
     * Sets the carrier line, falling back to a labeled "Unknown Carrier" when the provider name is
     * unavailable so the value is never shown as an unlabeled blank.
     *
     * @param carrier The provider/operator name, or null/blank when it could not be determined.
     */
    private void updateCarrier(String carrier)
    {
        // String.isBlank() is API 34+, but minSdk is 26, so trim().isEmpty() is used instead. It
        // also catches whitespace-only operator names, not just the empty string getProvider()
        // returns when the name is unavailable.
        binding.currentCarrier.setText(carrier == null || carrier.trim().isEmpty()
                ? getString(R.string.carrier_unknown) : carrier);
    }

    /**
     * Applies the hero text color for the current hero state.
     *
     * @param colorId The color resource to apply, or null to leave the color unchanged.
     */
    private void updateHeroColor(Integer colorId)
    {
        final Context context = getContext();
        if (binding == null || colorId == null || context == null) return;
        binding.networkTechnologyHero.setTextColor(ContextCompat.getColor(context, colorId));
    }

    /**
     * Updates the Voice pill, which doubles as the pill row visibility control: a null value means
     * the hero is a degraded No Service or Unknown state, so the whole row is hidden under it.
     *
     * @param value The voice bearer display value, or null to hide the pill row.
     */
    private void updateVoicePill(String value)
    {
        if (binding == null) return;
        if (value == null)
        {
            binding.networkTechnologyPills.setVisibility(View.GONE);
        } else
        {
            updatePill(binding.voicePill, R.string.pill_label_voice, value);
            binding.networkTechnologyPills.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Binds a pill TextView with a two-tone "Label · Value" text, or hides it when there is no
     * value to show.
     */
    private void updatePill(TextView pill, @StringRes int labelRes, String value)
    {
        if (binding == null) return;
        if (value == null)
        {
            pill.setVisibility(View.GONE);
        } else
        {
            pill.setText(buildPillText(labelRes, value));
            pill.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Builds the two-tone pill text: the label in the faded label color, the separator and value in
     * the pill's normal text color, matching the app's faded-label/bright-value convention.
     */
    private CharSequence buildPillText(@StringRes int labelRes, String value)
    {
        final String label = getString(labelRes);
        final SpannableStringBuilder builder = new SpannableStringBuilder(label);
        builder.append(TECH_SEPARATOR).append(value);

        final Context context = getContext();
        if (context != null)
        {
            builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.fadedText)),
                    0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    /**
     * Updates the carrier aggregation section in the serving cell card: one bandwidth chip per
     * component carrier plus a summary line. Hidden when fewer than two valid carriers are active.
     *
     * @param state The view state, or null to hide the section.
     */
    private void updateCarrierAggregation(CarrierAggregationViewState state)
    {
        if (binding == null) return;
        if (state == null)
        {
            binding.carrierAggregationGroup.setVisibility(View.GONE);
        } else
        {
            binding.carrierAggregationSummary.setText(state.summary());

            binding.carrierAggregationChips.removeAllViews();
            final LayoutInflater inflater = getLayoutInflater();
            for (String chipLabel : state.chipLabels())
            {
                final TextView chip = (TextView) inflater.inflate(R.layout.carrier_aggregation_chip,
                        binding.carrierAggregationChips, false);
                chip.setText(chipLabel);
                binding.carrierAggregationChips.addView(chip);
            }

            binding.carrierAggregationGroup.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Shows the Network Technology info dialog: an explanation of the hero line and the
     * Voice / Data / Branding pills.
     */
    private void showNetworkTechnologyInfoDialog()
    {
        if (getContext() == null) return;
        FragmentDialogs.showCellularInfo(getParentFragmentManager(),
                getString(R.string.network_technology_title),
                getString(R.string.network_technology_explanation));
    }

    /**
     * Updates the UI to reflect airplane mode status. The carrier, hero, and pills all live in the
     * network_technology_content container, so airplane mode toggles a single view; the next scan
     * refreshes the values when airplane mode ends.
     *
     * @param isAirplaneModeActive True if airplane mode is active, false otherwise.
     */
    private void updateAirplaneModeStatus(boolean isAirplaneModeActive)
    {
        Timber.d("updateAirplaneModeStatus called with isAirplaneModeActive=%s", isAirplaneModeActive);

        if (isAirplaneModeActive)
        {
            binding.networkTechnologyContent.setVisibility(View.GONE);
            binding.airplaneModeMessage.setVisibility(View.VISIBLE);

            clearCellularUi();
        } else
        {
            binding.networkTechnologyContent.setVisibility(View.VISIBLE);
            binding.airplaneModeMessage.setVisibility(View.GONE);

            // Restore visibility based on current protocol
            CellularProtocol protocol = viewModel.getServingCellProtocol().getValue();
            if (protocol != null)
            {
                updateServingCellProtocol(protocol);
            }
        }
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

        FragmentDialogs.showCellularInfo(getParentFragmentManager(), cellularInfoTitle, cellularInfoBody);
    }

    /**
     * Displays a dialog explaining what a 5G NR secondary cell is, plus the terms shown on the
     * card. Deliberately not the generic NR terms dialog: that one documents fields (MCC/MNC, TAC,
     * CID, TA) this card does not display.
     */
    private void showNrDetailsInfoDialog()
    {
        if (getContext() == null) return;
        FragmentDialogs.showCellularInfo(getParentFragmentManager(),
                getString(R.string.nr_secondary_info_description),
                getText(R.string.nr_secondary_cell_explanation));
    }

    /**
     * BroadcastReceiver to detect airplane mode changes.
     * This is independent of the PhoneStateListener and provides immediate UI updates.
     */
    private class AirplaneModeReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction()))
            {
                boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                Timber.d("Airplane mode broadcast received: %s, subscriptionId: %d",
                        isAirplaneModeOn ? "ON" : "OFF", subscriptionId);

                // Update view model
                viewModel.setAirplaneModeActive(isAirplaneModeOn);

                // Clear cellular UI if airplane mode is on
                if (isAirplaneModeOn)
                {
                    Timber.d("Clearing cellular UI due to airplane mode");
                    clearCellularUi();
                }
            }
        }
    }

    /**
     * Helper method to check if airplane mode is currently enabled.
     *
     * @param context The context to use for checking the setting.
     * @return True if airplane mode is on, false otherwise.
     */
    private static boolean isAirplaneModeOn(Context context)
    {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * Sets the MCC and MNC on the view model, preferring the PLMN string (which preserves
     * leading zeros) when available.
     */
    private void setMccMncOnViewModel(String plmn, Integer mcc, Integer mnc)
    {
        if (plmn != null)
        {
            viewModel.setMcc(NsUtils.extractMccFromPlmn(plmn));
            viewModel.setMnc(NsUtils.extractMncFromPlmn(plmn));
        } else
        {
            viewModel.setMcc(mcc != null ? String.valueOf(mcc) : "");
            viewModel.setMnc(mnc != null ? String.valueOf(mnc) : "");
        }
    }
}
