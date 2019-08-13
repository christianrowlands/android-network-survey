package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ToggleButton;
import com.craxiom.networksurvey.ConnectionState;
import com.craxiom.networksurvey.GrpcConnectionController;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.NetworkSurveyConstants;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.listeners.IGrpcConnectionStateListener;
import io.grpc.ManagedChannel;

/**
 * A fragment for allowing the user to connect to a remote gRPC based server.  This fragment handles the UI portion of the connection and delegates the actual
 * connection logic to {@link com.craxiom.networksurvey.GrpcConnectionController}.
 *
 * @since 0.0.4
 */
public class GrpcConnectionFragment extends Fragment implements View.OnClickListener, IGrpcConnectionStateListener
{
    private static final String LOG_TAG = GrpcConnectionFragment.class.getSimpleName();

    private static final int ACCESS_PERMISSION_REQUEST_ID = 10;
    private static final String NETWORK_SURVEY_CONNECTION_HOST = "NetworkSurvey:connectionHost";
    private static final String NETWORK_SURVEY_CONNECTION_PORT = "NetworkSurvey:connectionPort";

    private View view;
    private ToggleButton grpcConnectionToggleButton;
    private EditText grpcHostAddressEdit;
    private EditText grpcPortNumberEdit;

    private NetworkSurveyActivity networkSurveyActivity;  // TODO Need to unregister as listeners for Device Status and LTE Records so the queues don't get huge.
    private GrpcConnectionController grpcConnectionController;
    private String host = "";
    private Integer portNumber = NetworkSurveyConstants.DEFAULT_GRPC_PORT;

    public GrpcConnectionFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_grpc_connection, container, false);

        grpcConnectionToggleButton = view.findViewById(R.id.grpcConnectToggleButton);
        grpcHostAddressEdit = view.findViewById(R.id.grpcHostAddress);
        grpcPortNumberEdit = view.findViewById(R.id.grpcPortNumber);

        restoreConnectionParameters();
        grpcHostAddressEdit.setText(host);
        grpcPortNumberEdit.setText(String.valueOf(portNumber));

        grpcConnectionToggleButton.setOnClickListener(this);

        ActivityCompat.requestPermissions(networkSurveyActivity, new String[]{
                        Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);

        return view;
    }

    @Override
    public void onDestroyView()
    {
        grpcConnectionController.unregisterConnectionListener(this);
        super.onDestroyView();
    }

    @Override
    public void onClick(View view)
    {
        if (!hasInternetPermission()) return;

        if (grpcConnectionToggleButton.isChecked())
        {
            connectToGrpcServer();
        } else
        {
            disconnectFromGrpcServer();
        }
    }

    @Override
    public void onGrpcConnectionStateChange(ConnectionState newConnectionState)
    {
        updateUiState(newConnectionState);
    }

    public void setGrpcConnectionController(GrpcConnectionController grpcConnectionController)
    {
        this.grpcConnectionController = grpcConnectionController;
        grpcConnectionController.registerConnectionListener(this);
    }

    public void setNetworkSurveyActivity(NetworkSurveyActivity networkSurveyActivity)
    {
        this.networkSurveyActivity = networkSurveyActivity;
    }

    /**
     * Checks to see if the Internet permission has been granted.  If it has not, false is returned, but a request is put in to get
     * access to the Internet permission.
     *
     * @return True if the Internet permission has already been granted, false otherwise.
     */
    private boolean hasInternetPermission()
    {
        final boolean hasPermission = ContextCompat.checkSelfPermission(networkSurveyActivity, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;

        Log.d(LOG_TAG, "Has Internet permission: " + hasPermission);

        if (hasPermission) return true;

        ActivityCompat.requestPermissions(networkSurveyActivity, new String[]{
                        Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);
        return false;
    }

    /**
     * Updates the UI based on the different states of the server connection.
     *
     * @param connectionState The new state of the server connection to update the UI for.
     */
    private void updateUiState(ConnectionState connectionState)
    {
        switch (connectionState)
        {
            case DISCONNECTED:
                grpcConnectionToggleButton.setText(getString(R.string.status_disconnected));
                grpcConnectionToggleButton.setEnabled(true);
                grpcConnectionToggleButton.setChecked(false);
                break;

            case CONNECTING:
                grpcConnectionToggleButton.setText(getString(R.string.status_connecting));
                grpcConnectionToggleButton.setEnabled(false);
                grpcConnectionToggleButton.setChecked(false);
                break;

            case CONNECTED:
                grpcConnectionToggleButton.setText(getString(R.string.status_connected));
                grpcConnectionToggleButton.setEnabled(true);
                grpcConnectionToggleButton.setChecked(true);
                break;
        }
    }

    /**
     * Connect to a gRPC server by establishing the {@link ManagedChannel}, and then kick off the appropriate tasks so that
     * streaming is started.
     * <p>
     * If the server is already connected, then first disconnect and then start a new connection.
     */
    private void connectToGrpcServer()
    {
        grpcConnectionController.disconnectFromGrpcServer();

        try
        {
            host = grpcHostAddressEdit.getText().toString();
            final String portString = grpcPortNumberEdit.getText().toString();
            portNumber = Integer.valueOf(portString);
            int port = TextUtils.isEmpty(portString) ? 0 : portNumber;

            storeConnectionParameters();

            hideSoftInputFromWindow();

            grpcConnectionController.connectToGrpcServer(host, port);
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the remote gRPC server");
            updateUiState(ConnectionState.DISCONNECTED);
        }
    }

    /**
     * Store the connection host address and port number so they can be used on app restart.
     */
    private void storeConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(networkSurveyActivity.getApplicationContext());
        final SharedPreferences.Editor edit = preferences.edit();
        if (host != null) edit.putString(NETWORK_SURVEY_CONNECTION_HOST, host);
        edit.putInt(NETWORK_SURVEY_CONNECTION_PORT, portNumber);
        edit.apply();
    }

    /**
     * Restore the connection host address and port number.
     */
    private void restoreConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(networkSurveyActivity.getApplicationContext());

        final String restoredHost = preferences.getString(NETWORK_SURVEY_CONNECTION_HOST, "");
        if ((restoredHost != null) && (!restoredHost.isEmpty())) host = restoredHost;

        final int restoredPortNumber = preferences.getInt(NETWORK_SURVEY_CONNECTION_PORT, -1);
        if ((restoredPortNumber == -1)) portNumber = restoredPortNumber;
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     */
    private void disconnectFromGrpcServer()
    {
        if (grpcConnectionController != null)
        {
            grpcConnectionController.disconnectFromGrpcServer();
        }
    }

    /**
     * Hides the input keyboard on the current view.
     */
    private void hideSoftInputFromWindow()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null)
        {
            Log.e(LOG_TAG, "Unable to get the activity from the gRPC Connection Fragment");
        } else
        {
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(grpcHostAddressEdit.getWindowToken(), 0);
        }
    }
}
