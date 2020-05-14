package com.craxiom.networksurvey.services;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.navigation.NavDeepLinkBuilder;

import com.craxiom.networksurvey.ConnectionState;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IConnectionStateListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.messaging.CdmaRecord;
import com.craxiom.networksurvey.messaging.CdmaSurveyResponse;
import com.craxiom.networksurvey.messaging.ConnectionReply;
import com.craxiom.networksurvey.messaging.ConnectionRequest;
import com.craxiom.networksurvey.messaging.DeviceStatus;
import com.craxiom.networksurvey.messaging.GsmRecord;
import com.craxiom.networksurvey.messaging.GsmSurveyResponse;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.LteSurveyResponse;
import com.craxiom.networksurvey.messaging.NetworkSurveyStatusGrpc;
import com.craxiom.networksurvey.messaging.StatusUpdateReply;
import com.craxiom.networksurvey.messaging.UmtsRecord;
import com.craxiom.networksurvey.messaging.UmtsSurveyResponse;
import com.craxiom.networksurvey.messaging.WifiBeaconRecord;
import com.craxiom.networksurvey.messaging.WifiBeaconSurveyResponse;
import com.craxiom.networksurvey.messaging.WirelessSurveyGrpc;

import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.grpc.ManagedChannel;
import io.grpc.android.AndroidChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * This connection service is used to create a connection to a remote gRPC server.
 *
 * @since 0.0.9
 */
public class GrpcConnectionService extends Service implements IDeviceStatusListener, ISurveyRecordListener
{
    public static final long RECONNECTION_ATTEMPT_BACKOFF_TIME = 10_000L;
    private static final String LOG_TAG = GrpcConnectionService.class.getSimpleName();
    private static final int DEVICE_STATUS_REFRESH_RATE_MS = 15_000;

    private static ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private static final String ACTION_CONNECT = "com.craxiom.networksurvey.services.action.connect";
    private static final String ACTION_DISCONNECT = "com.craxiom.networksurvey.services.action.disconnect";

    private static final String HOST_PARAMETER = "com.craxiom.networksurvey.services.extra.host";
    private static final String PORT_PARAMETER = "com.craxiom.networksurvey.services.extra.port";
    private static final String DEVICE_NAME_PARAMETER = "com.craxiom.networksurvey.services.extra.devicename";

    private final ConnectionServiceBinder connectionServiceBinder;
    private final Handler uiThreadHandler;
    private final SurveyServiceConnection surveyServiceConnection;
    private NetworkSurveyService networkSurveyService;
    private GpsListener gpsListener;

    private final BlockingQueue<DeviceStatus> deviceStatusBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<GsmRecord> gsmRecordBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<CdmaRecord> cdmaRecordBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<UmtsRecord> umtsRecordBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<LteRecord> lteRecordBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<WifiBeaconRecord> wifiBeaconRecordBlockingQueue = new LinkedBlockingQueue<>();

    private final List<IConnectionStateListener> grpcConnectionListeners = new CopyOnWriteArrayList<>();

    private GrpcTask<DeviceStatus, StatusUpdateReply> deviceStatusGrpcTask;
    private GrpcTask<GsmRecord, GsmSurveyResponse> gsmRecordGrpcTask;
    private GrpcTask<CdmaRecord, CdmaSurveyResponse> cdmaRecordGrpcTask;
    private GrpcTask<UmtsRecord, UmtsSurveyResponse> umtsRecordGrpcTask;
    private GrpcTask<LteRecord, LteSurveyResponse> lteRecordGrpcTask;
    private GrpcTask<WifiBeaconRecord, WifiBeaconSurveyResponse> wifiBeaconRecordGrpcTask;
    private ManagedChannel channel;
    private final AtomicInteger deviceStatusGeneratorTaskId = new AtomicInteger();

    /**
     * It is sometimes hard to know if we should attempt a reconnect to the remote gRPC server based on the gRPC
     * connection failure messages.  Therefore, we always assume that we should keep attempting to reconnect unless
     * the user has specifically toggled the connection to off via the UI toggle switch.
     */
    private volatile boolean userCanceled = false;

    private String host = null;
    private Integer portNumber = null;
    private String deviceName = "";
    private String deviceId = "";
    private Handler deviceStatusReportHandler;

    public GrpcConnectionService()
    {
        connectionState = ConnectionState.DISCONNECTED;

        connectionServiceBinder = new ConnectionServiceBinder();
        uiThreadHandler = new Handler(Looper.getMainLooper());

        surveyServiceConnection = new SurveyServiceConnection();
    }

    /**
     * The current connection state.
     * <p>
     * It is possible that this connection state is stale.  If the Android System needed to stop this service due to
     * low memory or for other reasons, then it is possible that the onDestroy method was never called, which means the
     * connection state could not be updated.
     *
     * @return The current connection state of this service.
     */
    public static synchronized ConnectionState getConnectedState()
    {
        return connectionState;
    }

    /**
     * Starts this service to connect to a remote gRPC server with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @param context    The context to use to create the service intent.
     * @param host       The Host Name or IP Address of the remote gRPC server.
     * @param port       The Port Number of the gRPC server.
     * @param deviceName The name that represents this device to the gRPC server.
     * @see IntentService
     */
    public static void connectToGrpcServer(Context context, String host, int port, String deviceName)
    {
        Intent intent = new Intent(context, GrpcConnectionService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(HOST_PARAMETER, host);
        intent.putExtra(PORT_PARAMETER, port);
        intent.putExtra(DEVICE_NAME_PARAMETER, deviceName);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void disconnectFromGrpcServer(Context context)
    {
        Intent intent = new Intent(context, GrpcConnectionService.class);
        intent.setAction(ACTION_DISCONNECT);
        context.startService(intent);
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        HandlerThread deviceStatusReportHandlerThread = new HandlerThread("DeviceStatusThread");
        deviceStatusReportHandlerThread.start();

        deviceStatusReportHandler = new Handler(deviceStatusReportHandlerThread.getLooper());

        // Bind to the survey service
        final Context applicationContext = getApplicationContext();
        final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, Context.BIND_ABOVE_CLIENT);
        Log.i(LOG_TAG, "NetworkSurveyService bound in the GrpcConnectionService: " + bound);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent != null)
        {
            final String action = intent.getAction();
            if (action == null || action.isEmpty())
            {
                Log.i(LOG_TAG, "No action to perform, so just starting the connection service");
            } else if (ACTION_CONNECT.equals(action))
            {
                final String host = intent.getStringExtra(HOST_PARAMETER);
                final int port = intent.getIntExtra(PORT_PARAMETER, -1);
                final String deviceName = intent.getStringExtra(DEVICE_NAME_PARAMETER);
                if (host == null || port == -1 || deviceName == null)
                {
                    Log.e(LOG_TAG, "A valid hostname {} and port {} is required to connect to a gRPC server: " + host + ":" + port);
                } else
                {
                    userCanceled = false;
                    connectToGrpcServer(host, port, deviceName, false);
                }
            } else if (ACTION_DISCONNECT.equals(action))
            {
                userCanceled = true;
                disconnectFromGrpcServer(true);
            }
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return connectionServiceBinder;
    }

    @Override
    public void onDestroy()
    {
        Log.i(LOG_TAG, "Destroying the Connection Service");

        if (surveyServiceConnection != null) getApplicationContext().unbindService(surveyServiceConnection);

        if (deviceStatusReportHandler != null)
        {
            deviceStatusReportHandler.getLooper().quitSafely();
            deviceStatusReportHandler = null;
        }

        disconnectFromGrpcServer(true);

        super.onDestroy();
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        if (deviceStatus != null) deviceStatusBlockingQueue.add(deviceStatus);
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        if (isConnected() && gsmRecord != null) gsmRecordBlockingQueue.add(gsmRecord);
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        if (isConnected() && cdmaRecord != null) cdmaRecordBlockingQueue.add(cdmaRecord);
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        if (isConnected() && umtsRecord != null) umtsRecordBlockingQueue.add(umtsRecord);
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        if (isConnected() && lteRecord != null) lteRecordBlockingQueue.add(lteRecord);
    }

    @Override
    public void onWifiBeaconSurveyRecord(WifiBeaconRecord wifiBeaconRecord)
    {
        if (isConnected() && wifiBeaconRecord != null) wifiBeaconRecordBlockingQueue.add(wifiBeaconRecord);
    }

    /**
     * Adds an {@link IConnectionStateListener} so that it will be notified of all future connection state changes.
     *
     * @param connectionStateListener The listener to add.
     */
    public void registerConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        grpcConnectionListeners.add(connectionStateListener);
    }

    /**
     * Removes an {@link IConnectionStateListener} so that it will no longer be notified of connection state changes.
     *
     * @param connectionStateListener The listener to remove.
     */
    public void unregisterConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        grpcConnectionListeners.remove(connectionStateListener);
    }

    /**
     * Synchronized because the connection state can be updated from multiple threads.
     *
     * @return True if the gRPC connection is active, false otherwise.
     */
    synchronized boolean isConnected()
    {
        return connectionState == ConnectionState.CONNECTED;
    }

    /**
     * Connect to a gRPC server by establishing the {@link ManagedChannel}, and then kick off the appropriate tasks so
     * that streaming is started.
     *
     * @param host               The Host Name or IP Address of the remote gRPC server.
     * @param port               The Port Number of the gRPC server.
     * @param deviceName         The name that represents this device to the gRPC server.
     * @param reconnectOnFailure True if the a reconnection should be performed if the connection fails, false if
     *                           only a single connection attempt should be made.
     */
    private void connectToGrpcServer(String host, int port, String deviceName, boolean reconnectOnFailure)
    {
        try
        {
            Log.d(LOG_TAG, "Starting a connection to the gRPC server");

            this.host = host;
            portNumber = port;
            this.deviceName = deviceName;

            notifyConnectionStateChange(ConnectionState.CONNECTING);
            initializeDeviceStatusReport(deviceStatusGeneratorTaskId.incrementAndGet());

            new Thread(() -> {

                Thread.currentThread().setName("gRPC Connection Thread");
                final Context applicationContext = getApplicationContext();
                channel = AndroidChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .context(applicationContext)
                        .build();

                if (!startConnection())
                {
                    final String errorMessage = "Unable to connect to the Network Survey Server";
                    Log.w(LOG_TAG, errorMessage);
                    uiThreadHandler.post(() -> Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show());
                    final boolean attemptReconnection = !userCanceled && reconnectOnFailure;
                    disconnectFromGrpcServer(!attemptReconnection);
                    if (attemptReconnection)
                    {
                        uiThreadHandler.postDelayed(this::reconnectToGrpcServer, RECONNECTION_ATTEMPT_BACKOFF_TIME);
                    }
                    return;
                }

                notifyConnectionStateChange(ConnectionState.CONNECTED);
                final String message = "Connected to the Network Survey Server!";
                Log.i(LOG_TAG, message);
                uiThreadHandler.post(() -> Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show());

                deviceStatusGrpcTask = new GrpcTask<>(this, deviceStatusBlockingQueue,
                        statusUpdateReplyStreamObserver -> NetworkSurveyStatusGrpc.newStub(channel).statusUpdate(statusUpdateReplyStreamObserver));
                deviceStatusGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                final WirelessSurveyGrpc.WirelessSurveyStub wirelessSurveyStub = WirelessSurveyGrpc.newStub(channel);

                gsmRecordGrpcTask = new GrpcTask<>(this, gsmRecordBlockingQueue,
                        wirelessSurveyStub::streamGsmSurvey);
                gsmRecordGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                cdmaRecordGrpcTask = new GrpcTask<>(this, cdmaRecordBlockingQueue,
                        wirelessSurveyStub::streamCdmaSurvey);
                cdmaRecordGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                umtsRecordGrpcTask = new GrpcTask<>(this, umtsRecordBlockingQueue,
                        wirelessSurveyStub::streamUmtsSurvey);
                umtsRecordGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                lteRecordGrpcTask = new GrpcTask<>(this, lteRecordBlockingQueue,
                        wirelessSurveyStub::streamLteSurvey);
                lteRecordGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                wifiBeaconRecordGrpcTask = new GrpcTask<>(this, wifiBeaconRecordBlockingQueue,
                        wirelessSurveyStub::streamWifiBeaconSurvey);
                wifiBeaconRecordGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }).start();
        } catch (Throwable e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the remote gRPC server", e);
            disconnectFromGrpcServer(!reconnectOnFailure);

            if (reconnectOnFailure)
            {
                uiThreadHandler.postDelayed(this::reconnectToGrpcServer, RECONNECTION_ATTEMPT_BACKOFF_TIME);
            }
        }
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     *
     * @param stopService True if the service should be stopped after disconnecting from the gRCP server, false otherwise.
     */
    private void disconnectFromGrpcServer(boolean stopService)
    {
        if (stopService) notifyConnectionStateChange(ConnectionState.DISCONNECTING);

        if (deviceStatusGrpcTask != null) deviceStatusGrpcTask.cancel(true);
        if (gsmRecordGrpcTask != null) gsmRecordGrpcTask.cancel(true);
        if (cdmaRecordGrpcTask != null) cdmaRecordGrpcTask.cancel(true);
        if (umtsRecordGrpcTask != null) umtsRecordGrpcTask.cancel(true);
        if (lteRecordGrpcTask != null) lteRecordGrpcTask.cancel(true);
        if (wifiBeaconRecordGrpcTask != null) wifiBeaconRecordGrpcTask.cancel(true);

        shutdownChannel(!stopService);

        if (stopService) stopService();
    }

    /**
     * Tries to perform a handshake with the gRPC Server.  This should be done anytime we start a new connection with the server.
     *
     * @return True if the handshake is successful, false otherwise.
     */
    private boolean startConnection()
    {
        try
        {
            final NetworkSurveyStatusGrpc.NetworkSurveyStatusBlockingStub blockingStub = NetworkSurveyStatusGrpc
                    .newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS); // TODO make the timeout a user preference

            final ConnectionReply connectionReply = blockingStub.startConnection(ConnectionRequest.newBuilder().build());

            return connectionReply != null && connectionReply.getConnectionAccept();
        } catch (Exception e)
        {
            if (e.getCause() instanceof ConnectException)
            {
                Log.w(LOG_TAG, "Could not connect to the remote gRPC Server because: " + e.getCause().getMessage(), e);
            } else
            {
                Log.e(LOG_TAG, "Could not connect to the remote gRPC Server due to an Exception", e);
            }
        }

        return false;
    }

    /**
     * Initialize the handler that generates a periodic Device Status Message.
     *
     * @param taskId The ID of the task that initialized this device report.  It is used to ensure that only one
     *               device status report handler is running.  If the ID provided here does not match the current ID in
     *               the service then this handler is stopped.
     */
    private void initializeDeviceStatusReport(int taskId)
    {
        final int handlerTaskId = taskId;

        deviceStatusReportHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    if (handlerTaskId != deviceStatusGeneratorTaskId.get())
                    {
                        Log.d(LOG_TAG, "Stopping the device status report because the task ID has changed");
                        return;
                    }

                    onDeviceStatus(generateDeviceStatus());

                    deviceStatusReportHandler.postDelayed(this, DEVICE_STATUS_REFRESH_RATE_MS);
                } catch (SecurityException e)
                {
                    Log.e(LOG_TAG, "Could not get the required permissions to generate a device status message", e);
                }
            }
        }, 200L);
    }

    /**
     * Generate a device status message that can be sent to any remote servers.
     *
     * @return A Device Status message that can be sent to a remote server.
     */
    private DeviceStatus generateDeviceStatus()
    {
        final DeviceStatus.Builder builder = DeviceStatus.newBuilder();
        builder.setDeviceSerialNumber(deviceId)
                .setDeviceName(deviceName)
                .setDeviceTime(System.currentTimeMillis());

        if (gpsListener != null)
        {
            final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                builder.setLatitude(lastKnownLocation.getLatitude());
                builder.setLongitude(lastKnownLocation.getLongitude());
                builder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent batteryStatus = registerReceiver(null, intentFilter);
        if (batteryStatus != null)
        {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final float batteryPercent = (level / (float) scale) * 100;
            builder.setBatteryLevelPercent((int) batteryPercent);
        }

        return builder.build();
    }

    /**
     * Create and add the persistent notification indicating the current connection state to the remote gRPC server.
     * <p>
     * If the connection is disconnected, then the notification is removed.
     * <p>
     * This method is synchronized since we get notified of connection state changes from multiple threads.
     */
    private synchronized void updateConnectionNotification()
    {
        // Do nothing if the connection is in a disconnecting state.  We will update the notification once the full disconnection happens.
        if (connectionState == ConnectionState.DISCONNECTING) return;

        if (connectionState == ConnectionState.DISCONNECTED)
        {
            Log.i(LOG_TAG, "Removing the connection notification");

            final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            //noinspection ConstantConditions
            notificationManager.cancel(NetworkSurveyConstants.GRPC_CONNECTION_NOTIFICATION_ID);

            return;
        }

        PendingIntent pendingIntent = new NavDeepLinkBuilder(this)
                .setGraph(R.navigation.nav_graph)
                .setDestination(R.id.connection_fragment)
                .createPendingIntent();

        final CharSequence notificationText = getNotificationText();

        Notification notification = new NotificationCompat.Builder(this, NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.connection_notification_title))
                .setContentText(notificationText)
                .setOngoing(true)
                .setSmallIcon(R.drawable.connection_icon)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.connection_notification_title))
                .build();

        startForeground(NetworkSurveyConstants.GRPC_CONNECTION_NOTIFICATION_ID, notification);
    }

    /**
     * Synchronized because the connection state variable can be updated from multiple threads.
     *
     * @return A String that can be used in the Android notification to represent the current connection state.
     */
    private synchronized CharSequence getNotificationText()
    {
        final CharSequence notificationText;
        switch (connectionState)
        {
            case CONNECTING:
                notificationText = getText(R.string.connection_notification_connecting_text);
                break;

            case CONNECTED:
                notificationText = getText(R.string.connection_notification_active_text);
                break;

            default:
                notificationText = "";
        }

        return notificationText;
    }

    /**
     * Used to reconnect to the gRPC server using the last known connection settings.
     */
    private void reconnectToGrpcServer()
    {
        if (host == null || portNumber == null)
        {
            Log.e(LOG_TAG, "Can't reconnect to the last gRPC server because the host or port is null");
        }

        connectToGrpcServer(host, portNumber, deviceName, true);
    }

    /**
     * Closes the gRPC managed channel, and handles any channel cleanup.
     *
     * @param willReconnect True if an attempt is going to be made to reestablish the channel.  False if the connection
     *                      is going to remain disconnected.
     */
    private void shutdownChannel(boolean willReconnect)
    {
        // Increment the device status task ID so that the handler will stop on the next running
        deviceStatusGeneratorTaskId.getAndIncrement();

        if (channel != null)
        {
            try
            {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (Exception e)
            {
                Log.w(LOG_TAG, "An exception occurred while trying to shutdown the gRPC Channel", e);
            } finally
            {
                channel = null;
            }
        }

        if (!willReconnect) notifyConnectionStateChange(ConnectionState.DISCONNECTED);
    }

    /**
     * Closes the gRPC managed channel, unregisters the location listener, and stops this service.  After calling this
     * method this service should no longer be used.
     */
    private void stopService()
    {
        if (networkSurveyService != null)
        {
            networkSurveyService.unregisterSurveyRecordListener(this);
        }

        Log.i(LOG_TAG, "About to call stopSelf for the GrpcConnectionService");

        stopSelf();
    }

    /**
     * Notify all the registered listeners of the new connection state.
     *
     * @param newConnectionState The new gRPC connection state.
     */
    private synchronized void notifyConnectionStateChange(ConnectionState newConnectionState)
    {
        if (Log.isLoggable(LOG_TAG, Log.INFO))
        {
            Log.i(LOG_TAG, "gRPC Connection State Changed.  oldConnectionState=" + connectionState + ", newConnectionState=" + newConnectionState);
        }

        connectionState = newConnectionState;

        updateConnectionNotification();

        for (IConnectionStateListener listener : grpcConnectionListeners)
        {
            try
            {
                listener.onConnectionStateChange(newConnectionState);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to notify a gRPC Connection State Listener because of an exception", e);
            }
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always runs in the same process as its clients,
     * we don't need to deal with IPC.
     */
    public class ConnectionServiceBinder extends Binder
    {
        public GrpcConnectionService getService()
        {
            return GrpcConnectionService.this;
        }
    }

    /**
     * A task that can be run for each RPC stream that needs to be opened.
     *
     * @param <MessageType> The type of message that will be streamed to the remote gRPC server.
     * @param <Reply>       The reply type that will come back from gRPC server once the stream is complete.
     */
    @SuppressLint("StaticFieldLeak")
    private class GrpcTask<MessageType, Reply> extends AsyncTask<Void, Void, Boolean>
    {
        private final WeakReference<GrpcConnectionService> serviceWeakReference;
        private final BlockingQueue<MessageType> messageBlockingQueue;
        private final Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall;

        private Throwable failed;

        private GrpcTask(GrpcConnectionService serviceWeakReference, BlockingQueue<MessageType> messageBlockingQueue,
                         Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall)
        {
            this.serviceWeakReference = new WeakReference<>(serviceWeakReference);
            this.messageBlockingQueue = messageBlockingQueue;
            this.asyncStubCall = asyncStubCall;
        }

        @Override
        protected Boolean doInBackground(Void... nothing)
        {
            try
            {
                final CountDownLatch finishLatch = new CountDownLatch(1);
                final StreamObserver<Reply> responseObserver = new StreamObserver<Reply>()
                {
                    @Override
                    public void onNext(Reply value)
                    {

                    }

                    @Override
                    public void onError(Throwable t)
                    {
                        failed = t;
                        Log.e(LOG_TAG, "An error occurred in a gRPC stream", t);
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted()
                    {
                        Log.i(LOG_TAG, "Completed a gRPC stream");
                        finishLatch.countDown();
                    }
                };

                final StreamObserver<MessageType> outgoingMessageStream = asyncStubCall.apply(responseObserver);

                try
                {
                    while (finishLatch.getCount() != 0 && !isCancelled())
                    {
                        final GrpcConnectionService grpcConnectionService = serviceWeakReference.get();
                        if (grpcConnectionService == null)
                        {
                            finishLatch.countDown();
                            break;
                        }

                        final MessageType nextMessageToSend = messageBlockingQueue.poll(60, TimeUnit.SECONDS);

                        if (nextMessageToSend == null) continue;

                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE))
                        {
                            Log.v(LOG_TAG, "Sending a message to the remote gRPC server: " + nextMessageToSend);
                        }

                        outgoingMessageStream.onNext(nextMessageToSend);
                    }
                } catch (InterruptedException ignore)
                {
                    Log.i(LOG_TAG, "The gRPC task was interrupted");
                } catch (RuntimeException e)
                {
                    // Cancel RPC
                    outgoingMessageStream.onError(e);
                    throw e;
                }

                // Mark the end of the stream
                outgoingMessageStream.onCompleted();

                // Receiving happens asynchronously
                if (!finishLatch.await(1, TimeUnit.MINUTES))
                {
                    throw new RuntimeException("Could not finish rpc within 1 minute, the server is likely down");
                }

                return failed != null;
            } catch (Throwable e)
            {
                Log.e(LOG_TAG, "The connection to the remote gRPC server closed with an exception", e);
                return true;
            }
        }

        @Override
        protected void onPostExecute(Boolean reconnect)
        {
            Log.i(LOG_TAG, "Completed a gRPC Task, should reconnect= " + !userCanceled);
            GrpcConnectionService grpcConnectionService = serviceWeakReference.get();
            if (grpcConnectionService != null)
            {
                grpcConnectionService.disconnectFromGrpcServer(userCanceled);

                if (!userCanceled) grpcConnectionService.reconnectToGrpcServer();
            }
        }
    }

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link GrpcConnectionService}.
     */
    private class SurveyServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder iBinder)
        {
            Log.i(LOG_TAG, name + " service connected");
            final NetworkSurveyService.SurveyServiceBinder binder = (NetworkSurveyService.SurveyServiceBinder) iBinder;
            networkSurveyService = binder.getService();
            deviceId = networkSurveyService.getDeviceId();
            gpsListener = networkSurveyService.getGpsListener();
            networkSurveyService.registerSurveyRecordListener(GrpcConnectionService.this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Log.i(LOG_TAG, name + " service disconnected");
        }
    }
}
