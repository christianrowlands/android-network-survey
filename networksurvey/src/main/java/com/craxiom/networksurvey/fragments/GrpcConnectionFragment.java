package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.ConnectionState;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IGrpcConnectionStateListener;
import com.craxiom.networksurvey.services.GrpcConnectionService;

/**
 * A fragment for allowing the user to connect to a remote gRPC based server.  This fragment handles
 * the UI portion ofmthe connection and delegates the actual connection logic to {@link GrpcConnectionService}.
 *
 * @since 0.0.4
 */
public class GrpcConnectionFragment extends Fragment implements View.OnClickListener, IGrpcConnectionStateListener
{
    private static final String LOG_TAG = GrpcConnectionFragment.class.getSimpleName();

    private static final int ACCESS_PERMISSION_REQUEST_ID = 10;
    private static final String NETWORK_SURVEY_CONNECTION_HOST = "connectionHost";
    private static final String NETWORK_SURVEY_CONNECTION_PORT = "connectionPort";
    private static final String NETWORK_SURVEY_DEVICE_NAME = "deviceName";

    private final Handler uiThreadHandler;

    private Context applicationContext;

    private CardView connectionStatusCardView;
    private TextView connectionStatusText;
    private SwitchCompat grpcConnectionToggleSwitch;
    private EditText grpcHostAddressEdit;
    private EditText grpcPortNumberEdit;
    private EditText deviceNameEdit;

    private GrpcConnectionService grpcConnectionService;

    private String host = "";
    private Integer portNumber = NetworkSurveyConstants.DEFAULT_GRPC_PORT;
    private String deviceName = "";

    public GrpcConnectionFragment()
    {
        uiThreadHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        applicationContext = getActivity().getApplicationContext();
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.fragment_grpc_connection, container, false);

        connectionStatusCardView = view.findViewById(R.id.connection_status_card_view);
        connectionStatusText = view.findViewById(R.id.connection_status_text);
        grpcConnectionToggleSwitch = view.findViewById(R.id.grpcConnectToggleSwitch);
        grpcHostAddressEdit = view.findViewById(R.id.grpcHostAddress);
        grpcPortNumberEdit = view.findViewById(R.id.grpcPortNumber);
        deviceNameEdit = view.findViewById(R.id.deviceName);

        restoreConnectionParameters();
        grpcHostAddressEdit.setText(host);
        grpcPortNumberEdit.setText(String.valueOf(portNumber));
        deviceNameEdit.setText(deviceName);

        grpcConnectionToggleSwitch.setOnClickListener(this);

        initializeFragmentBasedOnConnectionState();

        ActivityCompat.requestPermissions(getActivity(), new String[]{
                        Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);

        return view;
    }

    @Override
    public void onDestroyView()
    {
        if (grpcConnectionService != null) grpcConnectionService.unregisterConnectionStateListener(this);
        super.onDestroyView();
    }

    @Override
    public void onClick(View view)
    {
        if (!hasInternetPermission()) return;

        if (grpcConnectionToggleSwitch.isChecked())
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
        uiThreadHandler.post(() -> updateUiState(newConnectionState));
    }

    /**
     * Reads the current connection state, and initializes the UI and other fragment items.
     */
    private void initializeFragmentBasedOnConnectionState()
    {
        final ConnectionState connectionState = GrpcConnectionService.getConnectedState();
        updateUiState(connectionState);

        if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING)
        {
            startService(false);
        }
    }

    /**
     * Checks to see if the Internet permission has been granted.  If it has not, false is returned, but a request is put in to get
     * access to the Internet permission.
     *
     * @return True if the Internet permission has already been granted, false otherwise.
     */
    private boolean hasInternetPermission()
    {
        final boolean hasPermission = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;

        Log.d(LOG_TAG, "Has Internet permission: " + hasPermission);

        if (hasPermission) return true;

        ActivityCompat.requestPermissions(getActivity(), new String[]{
                        Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);
        return false;
    }

    /**
     * Updates the UI based on the different states of the server connection.
     *
     * @param connectionState The new state of the server connection to update the UI for.
     */
    private synchronized void updateUiState(ConnectionState connectionState)
    {
        Log.d(LOG_TAG, "Updating the UI state for: " + connectionState);

        switch (connectionState)
        {
            case DISCONNECTED:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
                connectionStatusText.setText(getString(R.string.status_disconnected));
                grpcConnectionToggleSwitch.setEnabled(true);
                grpcConnectionToggleSwitch.setChecked(false);
                setFieldsEditable(true);
                break;

            case CONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnecting, null));
                connectionStatusText.setText(getString(R.string.status_connecting));
                grpcConnectionToggleSwitch.setEnabled(true);
                grpcConnectionToggleSwitch.setChecked(true);
                setFieldsEditable(false);
                break;

            case CONNECTED:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnected, null));
                connectionStatusText.setText(getString(R.string.status_connected));
                grpcConnectionToggleSwitch.setEnabled(true);
                grpcConnectionToggleSwitch.setChecked(true);
                setFieldsEditable(false);
                break;

            case DISCONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
                connectionStatusText.setText(getString(R.string.status_disconnecting));
                grpcConnectionToggleSwitch.setEnabled(false);
                grpcConnectionToggleSwitch.setChecked(true);
                setFieldsEditable(false);
                break;
        }
    }

    /**
     * Read the connection values from the UI, and then start the {@link GrpcConnectionService} and pass it the
     * parameters so it can establish the connection to the gRPC server.
     * <p>
     * If the connection values from the UI are invalid, then the connection is not started and a Toast is dislayed to
     * the user.
     */
    private void connectToGrpcServer()
    {
        try
        {
            if (!areConnectionParametersValid())
            {
                updateUiState(ConnectionState.DISCONNECTED);
                Log.w(LOG_TAG, "Can't connect because one ore more of the connection parameters are invalid");
                return;
            }

            updateUiState(ConnectionState.CONNECTING);

            host = grpcHostAddressEdit.getText().toString();
            final String portString = grpcPortNumberEdit.getText().toString();
            portNumber = Integer.valueOf(portString);
            deviceName = deviceNameEdit.getText().toString();

            storeConnectionParameters();

            hideSoftInputFromWindow();

            startService(true);
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the remote gRPC server");
            updateUiState(ConnectionState.DISCONNECTED);
        }
    }

    /**
     * Validates the user-specified server connection parameters.
     * <p>
     * For the host name, all non empty strings are 'valid'.
     * For the port number, all numeric numbers between 0 and 65535 are 'valid'.
     * For the device name, it must not be empty.
     *
     * @return True if all parameters are valid according to the above criteria.
     */
    private boolean areConnectionParametersValid()
    {
        if (grpcHostAddressEdit.getText().toString().isEmpty())
        {
            final String hostEmptyMessage = "Host address must be specified";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, hostEmptyMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        if (deviceNameEdit.getText().toString().isEmpty())
        {
            final String deviceNameEmptyMessage = "A device name must be specified";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, deviceNameEmptyMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        try
        {
            final int portNumber = Integer.valueOf(grpcPortNumberEdit.getText().toString());
            if (portNumber < 0 || portNumber > 65535)
            {
                final String invalidPortNumberMessage = "Port number must be between 0 and 65535";
                uiThreadHandler.post(() -> Toast.makeText(applicationContext, invalidPortNumberMessage, Toast.LENGTH_SHORT).show());
                return false;
            }
        } catch (Exception e)
        {
            final String portNotANumberMessage = "Port must be a number";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, portNotANumberMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        return true;
    }

    /**
     * Store the connection host address and port number so they can be used on app restart.
     */
    private void storeConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        final SharedPreferences.Editor edit = preferences.edit();
        if (host != null) edit.putString(NETWORK_SURVEY_CONNECTION_HOST, host);
        edit.putInt(NETWORK_SURVEY_CONNECTION_PORT, portNumber);
        if (deviceName != null) edit.putString(NETWORK_SURVEY_DEVICE_NAME, deviceName);
        edit.apply();
    }

    /**
     * Restore the connection host address and port number.
     */
    private void restoreConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        final String restoredHost = preferences.getString(NETWORK_SURVEY_CONNECTION_HOST, "");
        if (!restoredHost.isEmpty()) host = restoredHost;

        final int restoredPortNumber = preferences.getInt(NETWORK_SURVEY_CONNECTION_PORT, NetworkSurveyConstants.DEFAULT_GRPC_PORT);
        if (restoredPortNumber != -1) portNumber = restoredPortNumber;

        final String restoredDeviceName = preferences.getString(NETWORK_SURVEY_DEVICE_NAME, "");
        if (!restoredDeviceName.isEmpty()) deviceName = restoredDeviceName;
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     */
    private void disconnectFromGrpcServer()
    {
        GrpcConnectionService.disconnectFromGrpcServer(applicationContext);
    }

    /**
     * Sets all the appropriate fields as either editable or disable.
     *
     * @param editable True if the connection parameter fields should be editable, false otherwise.
     */
    private void setFieldsEditable(boolean editable)
    {
        grpcHostAddressEdit.setEnabled(editable);
        grpcPortNumberEdit.setEnabled(editable);
        deviceNameEdit.setEnabled(editable);
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
            final InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null)
            {
                inputMethodManager.hideSoftInputFromWindow(grpcHostAddressEdit.getWindowToken(), 0);
            }
        }
    }

    /**
     * Starts and then binds to the {@link GrpcConnectionService}.  If the connect parameter is true, then we also try
     * to start the connection to the gRPC server.
     *
     * @param connect If true, then after the service is started and bound, then a gRPC server connection is opened
     *                using the current instance host and port variables.
     */
    private synchronized void startService(boolean connect)
    {
        // Start the service
        Log.i(LOG_TAG, "Starting the gRPC Connection Service");
        final Intent serviceIntent = new Intent(applicationContext, GrpcConnectionService.class);
        applicationContext.startService(serviceIntent);

        // Bind to the service
        ServiceConnection serviceConnection = new GrpcServiceConnection(connect);
        final boolean bound = applicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_ABOVE_CLIENT);
        Log.i(LOG_TAG, "GrpcConnectionService bound: " + bound);
    }

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link GrpcConnectionService}.
     */
    private class GrpcServiceConnection implements ServiceConnection
    {
        private final boolean connect;

        /**
         * @param connect If true, then after the service is started and bound, then a gRPC server connection is opened
         *                using the current instance host and port variables.
         */
        GrpcServiceConnection(boolean connect)
        {
            this.connect = connect;
        }

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder iBinder)
        {
            Log.i(LOG_TAG, name + " service connected");
            final GrpcConnectionService.ConnectionServiceBinder binder = (GrpcConnectionService.ConnectionServiceBinder) iBinder;
            grpcConnectionService = binder.getService();
            grpcConnectionService.registerConnectionStateListener(GrpcConnectionFragment.this);

            if (connect)
            {
                GrpcConnectionService.connectToGrpcServer(applicationContext, host, portNumber, deviceName);
            } else
            {
                // Update the UI state just in case the static variable had become stale in the GrpcConnectionService
                // due to the service being stopped by the Android system without calling the onDestory() method.
                updateUiState(GrpcConnectionService.getConnectedState());
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Log.i(LOG_TAG, name + " service disconnected");
            grpcConnectionService = null;
        }
    }
}
