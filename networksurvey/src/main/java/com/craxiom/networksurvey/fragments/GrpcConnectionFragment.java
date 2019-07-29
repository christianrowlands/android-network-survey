package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import com.craxiom.networksurvey.IDeviceStatusListener;
import com.craxiom.networksurvey.ISurveyRecordListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.messaging.DeviceStatus;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.NetworkSurveyStatusGrpc;
import com.craxiom.networksurvey.messaging.StatusUpdateReply;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.lang.ref.WeakReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A fragment for allowing the user to connect to a remote gRPC based server.  This allows them to stream the survey results back to a server.
 *
 * @since 0.0.4
 */
public class GrpcConnectionFragment extends Fragment implements View.OnClickListener, IDeviceStatusListener, ISurveyRecordListener
{
    private static final String LOG_TAG = GrpcConnectionFragment.class.getSimpleName();

    private static final int ACCESS_PERMISSION_REQUEST_ID = 10;

    private View view;
    private ToggleButton grpcConnectionToggleButton;
    private EditText grpcHostAddressEdit;
    private EditText grpcPortNumberEdit;
    private ManagedChannel channel;

    private NetworkSurveyActivity networkSurveyActivity;  // TODO Need to unregister as listeners for Device Status and LTE Records so the queues don't get huge.

    private final BlockingQueue<DeviceStatus> deviceStatusBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<LteRecord> lteRecordBlockingQueue = new LinkedBlockingQueue<>();
    private GrpcDeviceStatusTask grpcDeviceStatusTask;

    public GrpcConnectionFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_grpc_connection, container, false);

        grpcConnectionToggleButton = view.findViewById(R.id.grpcConnectToggleButton);
        grpcHostAddressEdit = view.findViewById(R.id.grpcHostAddress);
        grpcPortNumberEdit = view.findViewById(R.id.grpcPortNumber);

        grpcConnectionToggleButton.setOnClickListener(this);

        ActivityCompat.requestPermissions(networkSurveyActivity, new String[]{
                        Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
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
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        deviceStatusBlockingQueue.add(deviceStatus);
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        lteRecordBlockingQueue.add(lteRecord);
    }

    public void setNetworkSurveyActivity(NetworkSurveyActivity networkSurveyActivity)
    {
        this.networkSurveyActivity = networkSurveyActivity;
        networkSurveyActivity.registerDeviceStatusListener(this);
        networkSurveyActivity.registerSurveyRecordListener(this);
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
                grpcConnectionToggleButton.setEnabled(true);
                grpcConnectionToggleButton.setText(getString(R.string.status_disconnected));
                break;

            case CONNECTING:
                grpcConnectionToggleButton.setEnabled(false);
                grpcConnectionToggleButton.setText(getString(R.string.status_connecting));
                break;

            case CONNECTED:
                grpcConnectionToggleButton.setEnabled(true);
                grpcConnectionToggleButton.setText(getString(R.string.status_connected));
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
        updateUiState(ConnectionState.CONNECTING);

        disconnectFromGrpcServer();

        try
        {
            final String host = grpcHostAddressEdit.getText().toString();
            final String portStr = grpcPortNumberEdit.getText().toString();
            int port = TextUtils.isEmpty(portStr) ? 0 : Integer.valueOf(portStr);

            hideSoftInputFromWindow();

            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

            grpcDeviceStatusTask = new GrpcDeviceStatusTask(channel, this);
            grpcDeviceStatusTask.execute();
            updateUiState(ConnectionState.CONNECTED);
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the remote gRPC server");
            updateUiState(ConnectionState.DISCONNECTED);
        }
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     */
    private void disconnectFromGrpcServer()
    {
        if (grpcDeviceStatusTask != null)
        {
            grpcDeviceStatusTask.cancel(true);
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

    private static class GrpcDeviceStatusTask extends AsyncTask<Void, Void, String>
    {
        private final ManagedChannel channel;
        private final WeakReference<GrpcConnectionFragment> fragmentReference;

        private Throwable failed;

        private GrpcDeviceStatusTask(ManagedChannel channel, GrpcConnectionFragment grpcConnectionFragment)
        {
            this.channel = channel;
            this.fragmentReference = new WeakReference<>(grpcConnectionFragment);
        }

        @Override
        protected String doInBackground(Void... nothing)
        {
            try
            {
                NetworkSurveyStatusGrpc.NetworkSurveyStatusStub asyncStub = NetworkSurveyStatusGrpc.newStub(channel);

                final CountDownLatch finishLatch = new CountDownLatch(1);
                final StreamObserver<StatusUpdateReply> responseObserver = new StreamObserver<StatusUpdateReply>()
                {
                    @Override
                    public void onNext(StatusUpdateReply value)
                    {

                    }

                    @Override
                    public void onError(Throwable t)
                    {
                        failed = t;
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted()
                    {
                        finishLatch.countDown();
                    }
                };

                final StreamObserver<DeviceStatus> deviceStatusStream = asyncStub.statusUpdate(responseObserver);

                try
                {
                    while (finishLatch.getCount() != 0)
                    {
                        final GrpcConnectionFragment grpcConnectionFragment = fragmentReference.get();
                        if (grpcConnectionFragment != null)
                        {
                            final DeviceStatus deviceStatus = grpcConnectionFragment.deviceStatusBlockingQueue.poll(60, TimeUnit.SECONDS);

                            if (Log.isLoggable(LOG_TAG, Log.VERBOSE))
                            {
                                Log.v(LOG_TAG, "Sending a DeviceStatus message to the remote gRPC server: " + deviceStatus.toString());
                            }

                            deviceStatusStream.onNext(deviceStatus);
                        }
                    }
                } catch (RuntimeException e)
                {
                    // Cancel RPC
                    deviceStatusStream.onError(e);
                    throw e;
                }

                // Mark the end of the stream
                deviceStatusStream.onCompleted();

                // Receiving happens asynchronously
                if (!finishLatch.await(1, TimeUnit.MINUTES))
                {
                    throw new RuntimeException("Could not finish rpc within 1 minute, the server is likely down");
                }

                if (failed != null)
                {
                    throw new RuntimeException(failed);
                }

                return ""; // TODO figure out something useful to return from the task
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to establish a connection to the remote gRPC server", e);
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result)
        {
            try
            {
                // TODO We don't want to close the channel once we start streaming LTE Records too
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            GrpcConnectionFragment grpcConnectionFragment = fragmentReference.get();
            if (grpcConnectionFragment != null)
            {
                grpcConnectionFragment.updateUiState(ConnectionState.DISCONNECTED);
            }
        }
    }

    private enum ConnectionState
    {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}
