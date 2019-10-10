package com.craxiom.networksurvey.fragments;

import android.content.Context;
import android.net.Uri;
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
    private final String LOG_TAG = CalculatorFragment.class.getSimpleName();

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;
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

                final int cellId = Integer.valueOf(enteredText);

                if (!CalculationUtils.isLteCellIdValid(cellId))
                {
                    Log.d(LOG_TAG, "The entered value for the LTE Cell ID is out of range.");
                    Toast.makeText(getActivity(), "Invalid Cell ID.  Valid Range is 0 - 268435455", Toast.LENGTH_LONG).show();
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

                final int pci = Integer.valueOf(enteredText);

                if (pci < 0 || pci > 503)
                {
                    Log.d(LOG_TAG, "The entered value for the LTE PCI is out of range.");
                    Toast.makeText(getActivity(), "Invalid PCI.  Valid Range is 0 - 503", Toast.LENGTH_LONG).show();
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

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment CalculatorFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static CalculatorFragment newInstance(String param1, String param2)
    {
        CalculatorFragment fragment = new CalculatorFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // TODO update these arguments
        if (getArguments() != null)
        {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
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

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri)
    {
        if (mListener != null)
        {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener)
        {
            mListener = (OnFragmentInteractionListener) context;
        } else
        {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener
    {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
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
