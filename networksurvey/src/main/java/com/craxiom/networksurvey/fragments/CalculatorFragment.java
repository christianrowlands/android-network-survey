package com.craxiom.networksurvey.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.craxiom.networksurvey.CalculationUtils;
import com.craxiom.networksurvey.R;

/**
 * A fragment to hold the LTE eNodeB ID calculator.
 *
 * @since 0.0.2
 */
public class CalculatorFragment extends Fragment
{
    static final String TITLE = "Calculators";
    private final String LOG_TAG = CalculatorFragment.class.getSimpleName();

    private View view;

    private TextWatcher lteCellIdTextWatcher = new TextWatcher()
    {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count)
        {
            final String enteredText = s.toString();
            try
            {
                if (enteredText.isEmpty())
                {
                    Log.v(LOG_TAG, "The entered text for the LTE Cell ID is empty.  Can't calculate the eNodeB ID.");
                    clearCellIdCalculatedValues();
                    return;
                }

                final int cellId = Integer.parseInt(enteredText);

                if (!CalculationUtils.isLteCellIdValid(cellId))
                {
                    Log.d(LOG_TAG, "The entered value for the LTE Cell ID is out of range.");
                    Toast.makeText(getActivity(), "Invalid Cell ID.  Valid Range is 0 - 268435455", Toast.LENGTH_SHORT).show();
                    clearCellIdCalculatedValues();
                    return;
                }

                // The Cell Identity is 28 bits long. The first 20 bits represent the Macro eNodeB ID. The last 8 bits
                // represent the sector.  Strip off the last 8 bits to get the Macro eNodeB ID.
                int eNodebId = CalculationUtils.getEnodebIdFromCellId(cellId);
                ((TextView) view.findViewById(R.id.calculatedEnbIdValue)).setText(String.valueOf(eNodebId));

                int sectorId = CalculationUtils.getSectorIdFromCellId(cellId);
                ((TextView) view.findViewById(R.id.calculatedSectorIdValue)).setText(String.valueOf(sectorId));
            } catch (Exception e)
            {
                Log.w(LOG_TAG, "Unable to parse the provide LTE Cell ID as an Integer:" + enteredText, e);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after)
        {

        }

        @Override
        public void afterTextChanged(Editable s)
        {

        }
    };

    private TextWatcher ltePciTextWatcher = new TextWatcher()
    {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count)
        {
            final String enteredText = s.toString();
            try
            {
                if (enteredText.isEmpty())
                {
                    Log.v(LOG_TAG, "The entered text for the LTE PCI is empty.  Can't calculate the PSS and SSS.");
                    clearPciCalculatedValues();
                    return;
                }

                final int pci = Integer.parseInt(enteredText);

                if (pci < 0 || pci > 503)
                {
                    Log.d(LOG_TAG, "The entered value for the LTE PCI is out of range.");
                    Toast.makeText(getActivity(), "Invalid PCI.  Valid Range is 0 - 503", Toast.LENGTH_SHORT).show();
                    clearPciCalculatedValues();
                    return;
                }

                int primarySyncSequence = CalculationUtils.getPrimarySyncSequence(pci);
                ((TextView) view.findViewById(R.id.calculatedPssValue)).setText(String.valueOf(primarySyncSequence));

                int secondarySyncSequence = CalculationUtils.getSecondarySyncSequence(pci);
                ((TextView) view.findViewById(R.id.calculatedSssValue)).setText(String.valueOf(secondarySyncSequence));
            } catch (Exception e)
            {
                Log.w(LOG_TAG, "Unable to parse the provide LTE PCI as an Integer:" + enteredText, e);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after)
        {

        }

        @Override
        public void afterTextChanged(Editable s)
        {

        }
    };

    public CalculatorFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_calculator, container, false);

        final EditText cellIdField = view.findViewById(R.id.lteCalculatorCellId);
        cellIdField.addTextChangedListener(lteCellIdTextWatcher);

        final EditText pciField = view.findViewById(R.id.lteCalculatorPci);
        pciField.addTextChangedListener(ltePciTextWatcher);

        return view;
    }

    /**
     * Sets the text in the Cell ID calculated TextView's to an empty string.
     */
    private void clearCellIdCalculatedValues()
    {
        ((TextView) view.findViewById(R.id.calculatedEnbIdValue)).setText("");
        ((TextView) view.findViewById(R.id.calculatedSectorIdValue)).setText("");
    }

    /**
     * Sets the text in the PCI calculated TextView's to an empty string.
     */
    private void clearPciCalculatedValues()
    {
        ((TextView) view.findViewById(R.id.calculatedPssValue)).setText("");
        ((TextView) view.findViewById(R.id.calculatedSssValue)).setText("");
    }
}
