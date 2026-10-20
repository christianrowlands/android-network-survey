package com.craxiom.networksurvey.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.PhoneState;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.WifiBeaconRecord;
import com.craxiom.messaging.grpc.BluetoothSurveyResponse;
import com.craxiom.messaging.grpc.CdmaSurveyResponse;
import com.craxiom.messaging.grpc.ConnectionHandshakeGrpc;
import com.craxiom.messaging.grpc.ConnectionReply;
import com.craxiom.messaging.grpc.ConnectionRequest;
import com.craxiom.messaging.grpc.DeviceStatusGrpc;
import com.craxiom.messaging.grpc.GnssSurveyResponse;
import com.craxiom.messaging.grpc.GsmSurveyResponse;
import com.craxiom.messaging.grpc.LteSurveyResponse;
import com.craxiom.messaging.grpc.NrSurveyResponse;
import com.craxiom.messaging.grpc.PhoneStateResponse;
import com.craxiom.messaging.grpc.StatusUpdateReply;
import com.craxiom.messaging.grpc.UmtsSurveyResponse;
import com.craxiom.messaging.grpc.WifiBeaconSurveyResponse;
import com.craxiom.messaging.grpc.WirelessSurveyGrpc;
import com.craxiom.mqttlibrary.connection.ConnectionState;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IPhoneStateListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.survey.model.RecordType;
import com.craxiom.networksurvey.util.DeviceNameStamper;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.android.AndroidChannelBuilder;
import io.grpc.stub.StreamObserver;
import timber.log.Timber;

/**
 * This connection service is used to create a connection to a remote gRPC server.
 *
 * @since 0.0.9
 */
public class GrpcConnectionService extends Service implements IDeviceStatusListener, IPhoneStateListener,
        ICellularSurveyRecordListener, IWifiSurveyRecordListener, IBluetoothSurveyRecordListener,
        IGnssSurveyRecordListener
{
    public static final long RECONNECTION_ATTEMPT_BACKOFF_TIME = 10_000L;
    // number of concurrent linked queues.
    private static final int NUMBER_OF_QUEUES_TO_PROCESS = 10;
    private static final int QUEUE_PROCESSING_SLEEP_TIME = 1_000;

    /**
     * Current connection state using AtomicReference for thread-safe access across multiple threads.
     */
    private static final AtomicReference<ConnectionState> connectionState =
            new AtomicReference<>(ConnectionState.DISCONNECTED);

    /** The last connection attempt that failed, or null since the last success or disconnect. */
    private static final AtomicReference<ConnectionFailure> lastFailure = new AtomicReference<>(null);

    /** What the record queues hold, and the connection's connecting and connected history. */
    private static final StreamQueueHealth QUEUE_HEALTH = new StreamQueueHealth();

    private static final String ACTION_CONNECT = "com.craxiom.networksurvey.services.action.connect";
    private static final String ACTION_SWITCH_SERVER = "com.craxiom.networksurvey.services.action.switch_server";
    private static final String ACTION_DISCONNECT = "com.craxiom.networksurvey.services.action.disconnect";

    private static final String HOST_PARAMETER = "com.craxiom.networksurvey.services.extra.host";
    private static final String PORT_PARAMETER = "com.craxiom.networksurvey.services.extra.port";
    private static final String DEVICE_NAME_PARAMETER = "com.craxiom.networksurvey.services.extra.devicename";
    private static final String RECORD_TYPES_PARAMETER = "com.craxiom.networksurvey.services.extra.record_types";
    private static final String ACTION_UPDATE_STREAM_TYPES = "com.craxiom.networksurvey.services.action.update_stream_types";

    private final Handler uiThreadHandler;
    private final SurveyServiceConnection surveyServiceConnection;
    private NetworkSurveyService networkSurveyService;
    private final ScheduledExecutorService executorService;

    private final StreamRecordQueue<DeviceStatus> deviceStatusQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<PhoneState> phoneStateQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<GsmRecord> gsmRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<CdmaRecord> cdmaRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<UmtsRecord> umtsRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<LteRecord> lteRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<NrRecord> nrRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<WifiBeaconRecord> wifiBeaconRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<BluetoothRecord> bluetoothRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);
    private final StreamRecordQueue<GnssRecord> gnssRecordQueue = new StreamRecordQueue<>(QUEUE_HEALTH, () -> this.streamingQueueLimit);

    // Cached streaming queue limit value
    private volatile int streamingQueueLimit = 0;

    // New connection approach
    private GrpcTask<DeviceStatus, StatusUpdateReply> deviceStatusGrpcTask;
    private GrpcTask<PhoneState, PhoneStateResponse> phoneStateGrpcTask;
    private GrpcTask<GsmRecord, GsmSurveyResponse> gsmRecordGrpcTask;
    private GrpcTask<CdmaRecord, CdmaSurveyResponse> cdmaRecordGrpcTask;
    private GrpcTask<UmtsRecord, UmtsSurveyResponse> umtsRecordGrpcTask;
    private GrpcTask<LteRecord, LteSurveyResponse> lteRecordGrpcTask;
    private GrpcTask<NrRecord, NrSurveyResponse> nrRecordGrpcTask;
    private GrpcTask<WifiBeaconRecord, WifiBeaconSurveyResponse> wifiBeaconRecordGrpcTask;
    private GrpcTask<BluetoothRecord, BluetoothSurveyResponse> bluetoothRecordGrpcTask;
    private GrpcTask<GnssRecord, GnssSurveyResponse> gnssRecordGrpcTask;
    private ManagedChannel channel;

    /**
     * It is sometimes hard to know if we should attempt a reconnect to the remote gRPC server based on the gRPC
     * connection failure messages.  Therefore, we always assume that we should keep attempting to reconnect unless
     * the user has specifically toggled the connection to off via the UI toggle switch.
     */
    private volatile boolean userCanceled = false;

    /** True once the "can't reach the server" Toast was shown for the current connect request. */
    private volatile boolean unreachableToastShown = false;

    private String host = null;
    private Integer portNumber = null;

    /** The name set on every message sent to the server; empty sends no name. */
    private volatile String deviceName = "";

    /** The record types requested for this connection. Device status is always streamed. */
    private volatile Set<RecordType> requestedStreamTypes = EnumSet.noneOf(RecordType.class);

    /** The record types that currently have an open gRPC stream. */
    private final Set<RecordType> activeStreamTypes = EnumSet.noneOf(RecordType.class);

    /** The record types whose listener is registered; kept while the connection reconnects. */
    private final EnumSet<RecordType> registeredListenerTypes = EnumSet.noneOf(RecordType.class);

    /**
     * Incremented on every connect and disconnect request. A connection attempt that finishes after
     * a newer request (for example the survey was stopped while the handshake was in flight) is
     * discarded instead of reporting CONNECTED, so it can never roll a new Mission ID after a stop.
     */
    private final AtomicInteger connectionGeneration = new AtomicInteger(0);

    private WirelessSurveyGrpc.WirelessSurveyStub wirelessSurveyStub;

    /** True once {@link #completeConnectionSetup()} has run for the current connection. */
    private boolean connectionSetupComplete = false;

    public GrpcConnectionService()
    {
        uiThreadHandler = new Handler(Looper.getMainLooper());

        surveyServiceConnection = new SurveyServiceConnection();

        executorService = Executors.newScheduledThreadPool(NUMBER_OF_QUEUES_TO_PROCESS);
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
    public static ConnectionState getConnectedState()
    {
        return connectionState.get();
    }

    /**
     * @return The last connection attempt that failed, or null if the connection has succeeded or
     * was turned off since.
     */
    public static ConnectionFailure getLastFailure()
    {
        return lastFailure.get();
    }

    /**
     * @return How many records are waiting to be sent, whether any are being thrown away, and when
     * the connection started connecting.
     */
    public static StreamQueueHealth getQueueHealth()
    {
        return QUEUE_HEALTH;
    }

    /**
     * Starts this service to connect to a remote gRPC server streaming the given record types.
     *
     * @param context            The context to use to create the service intent.
     * @param connectionSettings The connection settings to use to connect to the gRPC server.
     * @param recordTypes        The record types to stream.
     */
    public static void connectToGrpcServer(Context context, GrpcConnectionSettings connectionSettings,
                                           Collection<RecordType> recordTypes)
    {
        Timber.d("Creating the ACTION_CONNECT intent to kick off the gRPC connection");

        Intent intent = serverIntent(context, ACTION_CONNECT, connectionSettings);
        intent.putExtra(RECORD_TYPES_PARAMETER, toWireNames(recordTypes));

        // Report CONNECTING right away rather than when the service gets to the intent, so a Stop in
        // between sees the connection and stops it, and a second start does not connect twice.
        if (connectionState.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING))
        {
            QUEUE_HEALTH.onConnectRequested(System.currentTimeMillis());
        }
        context.startService(intent);
    }

    /**
     * Moves a running gRPC connection to the saved server without ending the survey: the stream
     * reconnects to the new host and port (retrying until it is reachable) and the Mission ID is
     * kept. A new device name alone applies without reconnecting. Does nothing when no connection
     * is running or one is being turned off; requests are handled in order, so a switch sent after a
     * disconnect never brings the connection back.
     *
     * @param context            The context to use to create the service intent.
     * @param connectionSettings The saved gRPC server settings.
     */
    public static void switchServer(Context context, GrpcConnectionSettings connectionSettings)
    {
        // Sending the intent with nothing running would start an idle service.
        if (connectionState.get() == ConnectionState.DISCONNECTED) return;

        context.startService(serverIntent(context, ACTION_SWITCH_SERVER, connectionSettings));
    }

    private static Intent serverIntent(Context context, String action, GrpcConnectionSettings connectionSettings)
    {
        final Intent intent = new Intent(context, GrpcConnectionService.class);
        intent.setAction(action);
        intent.putExtra(HOST_PARAMETER, connectionSettings.getHost());
        intent.putExtra(PORT_PARAMETER, connectionSettings.getPort());
        intent.putExtra(DEVICE_NAME_PARAMETER, connectionSettings.getDeviceName());
        return intent;
    }

    /**
     * Changes the record types streamed over the current gRPC connection without reconnecting.
     *
     * @param context     The context to use to create the service intent.
     * @param recordTypes The record types to stream from now on.
     */
    public static void updateStreamTypes(Context context, Collection<RecordType> recordTypes)
    {
        // Nothing to update without a connection, and sending the intent would start an idle service.
        if (connectionState.get() == ConnectionState.DISCONNECTED) return;

        Intent intent = new Intent(context, GrpcConnectionService.class);
        intent.setAction(ACTION_UPDATE_STREAM_TYPES);
        intent.putExtra(RECORD_TYPES_PARAMETER, toWireNames(recordTypes));
        context.startService(intent);
    }

    private static String[] toWireNames(Collection<RecordType> recordTypes)
    {
        return recordTypes.stream().map(RecordType::getWireName).toArray(String[]::new);
    }

    private static Set<RecordType> fromWireNames(String[] wireNames)
    {
        final Set<RecordType> types = EnumSet.noneOf(RecordType.class);
        if (wireNames == null) return types;
        for (String wireName : wireNames)
        {
            final RecordType type = RecordType.fromWireName(wireName);
            if (type != null) types.add(type);
        }
        return types;
    }

    /**
     * Starts this service to disconnect from the gRPC server. The service stops itself once the
     * connection is closed.
     *
     * @param context The context to use to create the service intent.
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

        // The gRPC connection is reported in the NetworkSurveyService's foreground notification, and
        // that service stays alive (and in the foreground) for as long as this connection is active.
        // Start it explicitly before binding, because the bind below does not auto-create it.
        final Context applicationContext = getApplicationContext();
        final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        try
        {
            applicationContext.startService(serviceIntent);
        } catch (Exception e)
        {
            Timber.e(e, "Could not start the NetworkSurveyService from the GrpcConnectionService");
        }

        final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, BIND_ABOVE_CLIENT);
        Timber.i("NetworkSurveyService bound in the GrpcConnectionService: %s", bound);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        // Load the streaming queue limit preference
        streamingQueueLimit = PreferenceUtils.getStreamingQueueLimit(getApplicationContext());
        Timber.d("gRPC streaming queue limit set to %d", streamingQueueLimit);

        if (intent != null)
        {
            final String action = intent.getAction();
            if (action == null || action.isEmpty())
            {
                Timber.i("No action to perform, so just starting the connection service");
            } else if (ACTION_CONNECT.equals(action))
            {
                final String host = intent.getStringExtra(HOST_PARAMETER);
                final int port = intent.getIntExtra(PORT_PARAMETER, -1);
                final String deviceName = intent.getStringExtra(DEVICE_NAME_PARAMETER);
                final String[] recordTypes = intent.getStringArrayExtra(RECORD_TYPES_PARAMETER);
                if (host == null || port == -1 || deviceName == null)
                {
                    Timber.e("A valid hostname (%s) and port (%d) is required to connect to a gRPC server", host, port);
                    notifyConnectionStateChange(ConnectionState.DISCONNECTED);
                } else
                {
                    userCanceled = false;
                    unreachableToastShown = false;
                    lastFailure.set(null);
                    QUEUE_HEALTH.onConnectRequested(System.currentTimeMillis());
                    requestedStreamTypes = fromWireNames(recordTypes);
                    this.deviceName = deviceName;
                    final int generation = connectionGeneration.incrementAndGet();
                    // A connection that is still open (or waiting to retry) is replaced, not leaked.
                    uiThreadHandler.removeCallbacksAndMessages(null);
                    if (channel != null || wirelessSurveyStub != null) disconnectFromGrpcServer(false);
                    // Keeps trying until the server answers, like MQTT, holding the records meanwhile
                    connectToGrpcServer(host, port, true, generation);
                    listenForRequestedRecords();
                }
            } else if (ACTION_SWITCH_SERVER.equals(action))
            {
                switchServer(intent.getStringExtra(HOST_PARAMETER), intent.getIntExtra(PORT_PARAMETER, -1),
                        intent.getStringExtra(DEVICE_NAME_PARAMETER));
            } else if (ACTION_UPDATE_STREAM_TYPES.equals(action))
            {
                final Set<RecordType> types = fromWireNames(intent.getStringArrayExtra(RECORD_TYPES_PARAMETER));
                requestedStreamTypes = types;
                if (isConnected())
                {
                    applyStreamTypes(types);
                } else
                {
                    // While connecting, hold records of the new types too, and drop the removed ones.
                    retainOnly(types);
                }
            } else if (ACTION_DISCONNECT.equals(action))
            {
                userCanceled = true;
                connectionGeneration.incrementAndGet();
                uiThreadHandler.removeCallbacksAndMessages(null);
                lastFailure.set(null);
                disconnectFromGrpcServer(true);
            }
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // Nothing binds to this service; it is driven by intents from the survey service.
        return null;
    }

    @Override
    public void onDestroy()
    {
        Timber.i("Destroying the Connection Service");

        if (surveyServiceConnection != null)
        {
            getApplicationContext().unbindService(surveyServiceConnection);
        }

        uiThreadHandler.removeCallbacksAndMessages(null);
        disconnectFromGrpcServer(true);

        super.onDestroy();
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        if (deviceStatus != null && accepts(null, deviceStatusGrpcTask))
        {
            deviceStatusQueue.offer(DeviceNameStamper.stamp(deviceStatus, deviceName));
        }
    }

    @Override
    public void onPhoneState(PhoneState phoneState)
    {
        if (phoneState != null && accepts(RecordType.PHONE_STATE, phoneStateGrpcTask))
        {
            phoneStateQueue.offer(DeviceNameStamper.stamp(phoneState, deviceName));
        }
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        if (gsmRecord != null && accepts(RecordType.CELLULAR, gsmRecordGrpcTask))
        {
            gsmRecordQueue.offer(DeviceNameStamper.stamp(gsmRecord, deviceName));
        }
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        if (cdmaRecord != null && accepts(RecordType.CELLULAR, cdmaRecordGrpcTask))
        {
            cdmaRecordQueue.offer(DeviceNameStamper.stamp(cdmaRecord, deviceName));
        }
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        if (umtsRecord != null && accepts(RecordType.CELLULAR, umtsRecordGrpcTask))
        {
            umtsRecordQueue.offer(DeviceNameStamper.stamp(umtsRecord, deviceName));
        }
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        if (lteRecord != null && accepts(RecordType.CELLULAR, lteRecordGrpcTask))
        {
            lteRecordQueue.offer(DeviceNameStamper.stamp(lteRecord, deviceName));
        }
    }

    @Override
    public void onNrSurveyRecord(NrRecord nrRecord)
    {
        if (nrRecord != null && accepts(RecordType.CELLULAR, nrRecordGrpcTask))
        {
            nrRecordQueue.offer(DeviceNameStamper.stamp(nrRecord, deviceName));
        }
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        if (wifiBeaconRecords != null && accepts(RecordType.WIFI, wifiBeaconRecordGrpcTask))
        {
            wifiBeaconRecordQueue.offerAll(wifiBeaconRecords.stream()
                    .map(wrapper -> DeviceNameStamper.stamp(wrapper.getWifiBeaconRecord(), deviceName))
                    .collect(Collectors.toList()));
        }
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        if (bluetoothRecord != null && accepts(RecordType.BLUETOOTH, bluetoothRecordGrpcTask))
        {
            bluetoothRecordQueue.offer(DeviceNameStamper.stamp(bluetoothRecord, deviceName));
        }
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        if (bluetoothRecords != null && accepts(RecordType.BLUETOOTH, bluetoothRecordGrpcTask))
        {
            bluetoothRecordQueue.offerAll(bluetoothRecords.stream()
                    .map(record -> DeviceNameStamper.stamp(record, deviceName))
                    .collect(Collectors.toList()));
        }
    }

    @Override
    public void onGnssSurveyRecord(GnssRecord gnssRecord)
    {
        if (gnssRecord != null && accepts(RecordType.GNSS, gnssRecordGrpcTask))
        {
            gnssRecordQueue.offer(DeviceNameStamper.stamp(gnssRecord, deviceName));
        }
    }

    /**
     * Whether a record is kept for the server. Records are kept while the connection is up and
     * while it is connecting or reconnecting, so they are sent once it connects. A stream the server
     * doesn't implement (its task finished while connected) takes no records.
     *
     * @param type The record type, or null for device status, which every connection carries.
     * @param task The stream task the record would be sent on, or null if it hasn't opened yet.
     */
    private boolean accepts(RecordType type, GrpcTask<?, ?> task)
    {
        if (type != null && !requestedStreamTypes.contains(type)) return false;

        final ConnectionState state = connectionState.get();
        if (state == ConnectionState.CONNECTING) return true;
        if (state != ConnectionState.CONNECTED) return false;
        return task == null || task.getStatus() != AsyncTask.Status.FINISHED;
    }

    /**
     * Check if the gRPC connection is currently active.
     * Thread-safe access provided by AtomicReference.
     *
     * @return True if the gRPC connection is active, false otherwise.
     */
    boolean isConnected()
    {
        return connectionState.get() == ConnectionState.CONNECTED;
    }

    /**
     * Connect to a gRPC server by establishing the {@link ManagedChannel}, and then kick off the appropriate tasks so
     * that streaming is started. The device name comes from {@link #deviceName}.
     *
     * @param host               The Host Name or IP Address of the remote gRPC server.
     * @param port               The Port Number of the gRPC server.
     * @param reconnectOnFailure True if the a reconnection should be performed if the connection fails, false if
     *                           only a single connection attempt should be made.
     * @param generation         The {@link #connectionGeneration} this attempt belongs to. If a newer connect or
     *                           disconnect request arrives before the handshake completes, the attempt is discarded.
     */
    private void connectToGrpcServer(String host, int port, boolean reconnectOnFailure, int generation)
    {
        try
        {
            Timber.d("Starting a connection to the gRPC server");

            this.host = host;
            portNumber = port;

            notifyConnectionStateChange(ConnectionState.CONNECTING);

            new Thread(() -> {
                try
                {
                    Thread.currentThread().setName("gRPC Connection Thread");
                    final Context applicationContext = getApplicationContext();
                    // The channel stays local to this attempt until it is accepted, so a stale attempt
                    // can never close the channel of a newer one.
                    final ManagedChannel attemptChannel = AndroidChannelBuilder.forAddress(host, port)
                            .usePlaintext()
                            .context(applicationContext)
                            .build();

                    final boolean handshakeAccepted = startConnection(attemptChannel);
                    if (isStaleAttempt(generation))
                    {
                        // The survey was stopped (or a newer connection requested) while the handshake was in
                        // flight. Neither reporting CONNECTED nor tearing down is this attempt's job anymore.
                        Timber.i("Discarding a gRPC connection attempt that finished after it was canceled");
                        attemptChannel.shutdownNow();
                        return;
                    }

                    if (!handshakeAccepted)
                    {
                        attemptChannel.shutdownNow();
                        Timber.w("Unable to connect to the gRPC server %s:%d", host, port);
                        onConnectionAttemptFailed(host, port, reconnectOnFailure, generation);
                        return;
                    }

                    synchronized (this)
                    {
                        if (isStaleAttempt(generation))
                        {
                            Timber.i("Discarding a gRPC connection that completed after it was canceled");
                            attemptChannel.shutdownNow();
                            return;
                        }
                        channel = attemptChannel;
                        wirelessSurveyStub = WirelessSurveyGrpc.newStub(attemptChannel);
                        lastFailure.set(null);

                        // Reported under the same lock as the stale check, so a stop cannot land in
                        // between and have this attempt roll a Mission ID after it.
                        notifyConnectionStateChange(ConnectionState.CONNECTED);
                    }

                    Timber.i("Connected to the gRPC server %s:%d", host, port);
                    uiThreadHandler.post(() -> Toast.makeText(applicationContext, R.string.grpc_connected_toast, Toast.LENGTH_SHORT).show());

                    // If the survey service is not bound yet, the bind callback finishes the setup.
                    completeConnectionSetup();
                } catch (Throwable t)
                {
                    Timber.e(t, "An exception occurred in the gRPC connection thread");
                    if (isStaleAttempt(generation)) return;
                    onConnectionAttemptFailed(host, port, reconnectOnFailure, generation);
                }
            }).start();
        } catch (Throwable e)
        {
            Timber.e(e, "An exception occurred when trying to connect to the remote gRPC server");
            onConnectionAttemptFailed(host, port, reconnectOnFailure, generation);
        }
    }

    /**
     * Records a failed connection attempt so the gRPC page can say which server could not be
     * reached, then either schedules another attempt or turns the connection off.
     */
    private void onConnectionAttemptFailed(String host, int port, boolean reconnectOnFailure, int generation)
    {
        lastFailure.set(new ConnectionFailure(host, port, System.currentTimeMillis()));
        if (!unreachableToastShown)
        {
            // Once per connect request; the Survey screen keeps showing the failure after that.
            unreachableToastShown = true;
            uiThreadHandler.post(() -> Toast.makeText(getApplicationContext(), R.string.grpc_unreachable_toast, Toast.LENGTH_SHORT).show());
        }

        final boolean attemptReconnect = !userCanceled && reconnectOnFailure;
        disconnectFromGrpcServer(!attemptReconnect);
        if (attemptReconnect)
        {
            // The failure is not a state change, so tell the survey service to republish it.
            if (networkSurveyService != null) networkSurveyService.updateServiceNotification();
            uiThreadHandler.postDelayed(() -> reconnectToGrpcServer(generation), RECONNECTION_ATTEMPT_BACKOFF_TIME);
        }
    }

    /**
     * Moves the running connection to a new server without reporting a disconnect, so the survey
     * session and the Mission ID continue. A new device name alone is applied in place.
     */
    private void switchServer(String newHost, int newPort, String newDeviceName)
    {
        final ConnectionState state = connectionState.get();
        if (userCanceled || newHost == null || newPort == -1
                || state == ConnectionState.DISCONNECTED || state == ConnectionState.DISCONNECTING)
        {
            Timber.i("Ignoring a gRPC server switch because no connection is running");
            return;
        }

        deviceName = newDeviceName == null ? "" : newDeviceName;
        if (newHost.equals(host) && Integer.valueOf(newPort).equals(portNumber)) return;

        Timber.i("Switching the gRPC connection to %s:%d", newHost, newPort);
        final int generation = connectionGeneration.incrementAndGet();
        // Retries scheduled for the old server would otherwise connect to it again.
        uiThreadHandler.removeCallbacksAndMessages(null);
        lastFailure.set(null);
        unreachableToastShown = false;
        disconnectFromGrpcServer(false);
        connectToGrpcServer(newHost, newPort, true, generation);
    }

    /**
     * @return True if a newer connect or disconnect request has arrived since the attempt with
     * {@code generation} started, or the user canceled the connection.
     */
    private boolean isStaleAttempt(int generation)
    {
        return userCanceled || generation != connectionGeneration.get();
    }

    /**
     * Finishes setting up a connection once both the handshake has succeeded and the survey service
     * is bound: reports the connection to the survey service (which starts the survey session),
     * opens the device status stream and the record streams. Safe to call twice; it runs from the
     * connection thread and again from the bind callback, whichever comes last does the work.
     */
    private synchronized void completeConnectionSetup()
    {
        if (!isConnected() || wirelessSurveyStub == null || networkSurveyService == null || connectionSetupComplete)
        {
            return;
        }
        connectionSetupComplete = true;

        // The CONNECTED notification may have run before the survey service was bound, so it is
        // reported here as well; a second report is harmless (the Mission ID only rolls once).
        networkSurveyService.onGrpcConnectionStateChanged(true);
        networkSurveyService.updateServiceNotification();

        // gRPC is now an active output, so MQTT backpressure may switch from pausing to dropping.
        networkSurveyService.reevaluateMqttBackpressureMode();

        startDeviceStatusStream();
        applyStreamTypes(requestedStreamTypes);
    }

    /**
     * Opens a gRPC stream for every record type in [types] that does not have one, closes the stream
     * of every type no longer in it, and registers the record listeners to match.
     *
     * @param types The record types to stream from now on.
     */
    private synchronized void applyStreamTypes(Set<RecordType> types)
    {
        if (wirelessSurveyStub == null || networkSurveyService == null)
        {
            Timber.w("Can't apply the gRPC stream types because the connection is not ready");
            return;
        }

        for (RecordType type : RecordType.values())
        {
            final boolean wanted = types.contains(type);
            final boolean active = activeStreamTypes.contains(type);
            if (wanted && !active)
            {
                if (startStream(type)) activeStreamTypes.add(type);
            } else if (!wanted && active)
            {
                stopStream(type);
                activeStreamTypes.remove(type);
            }
        }
        syncListeners(types);

        Timber.i("gRPC is streaming %s", activeStreamTypes);
    }

    /**
     * Registers this service as the record listener for every streamable type in [types] and
     * unregisters it from the others. The listeners stay registered while the connection
     * reconnects, so the survey keeps scanning in between.
     */
    private synchronized void syncListeners(Set<RecordType> types)
    {
        if (networkSurveyService == null) return;

        for (RecordType type : RecordType.values())
        {
            final boolean wanted = types.contains(type);
            final boolean registered = registeredListenerTypes.contains(type);
            if (wanted && !registered)
            {
                if (registerListener(type)) registeredListenerTypes.add(type);
            } else if (!wanted && registered)
            {
                unregisterListener(type);
                registeredListenerTypes.remove(type);
            }
        }
    }

    /**
     * While the connection is down, stops holding records of the types not in [types] (their
     * queued records are thrown away, as they would be if the stream closed) and starts listening
     * for the new ones. Streams open on the next connect.
     */
    private synchronized void retainOnly(Set<RecordType> types)
    {
        for (RecordType type : RecordType.values())
        {
            if (!types.contains(type)) stopStream(type);
        }
        syncListeners(types);
    }

    /**
     * Starts listening for the requested records as soon as a connection is requested, so records
     * produced while it connects are held and sent once it does. Without the survey service bound
     * yet, the bind callback does this.
     */
    private synchronized void listenForRequestedRecords()
    {
        final ConnectionState state = connectionState.get();
        if (networkSurveyService == null || state == ConnectionState.DISCONNECTED || state == ConnectionState.DISCONNECTING)
        {
            return;
        }
        networkSurveyService.registerDeviceStatusListener(this);
        syncListeners(requestedStreamTypes);
    }

    /**
     * Opens the gRPC streams for one record type.
     *
     * @return True if the record type is supported over gRPC and its stream was opened.
     */
    private boolean startStream(RecordType type)
    {
        final WirelessSurveyGrpc.WirelessSurveyStub stub = wirelessSurveyStub;
        switch (type)
        {
            case CELLULAR:
                gsmRecordGrpcTask = startTask(gsmRecordQueue, stub::streamGsmSurvey);
                cdmaRecordGrpcTask = startTask(cdmaRecordQueue, stub::streamCdmaSurvey);
                umtsRecordGrpcTask = startTask(umtsRecordQueue, stub::streamUmtsSurvey);
                lteRecordGrpcTask = startTask(lteRecordQueue, stub::streamLteSurvey);
                nrRecordGrpcTask = startTask(nrRecordQueue, stub::streamNrSurvey);
                return true;
            case PHONE_STATE:
                phoneStateGrpcTask = startTask(phoneStateQueue, stub::streamPhoneState);
                return true;
            case WIFI:
                wifiBeaconRecordGrpcTask = startTask(wifiBeaconRecordQueue, stub::streamWifiBeaconSurvey);
                return true;
            case BLUETOOTH:
                bluetoothRecordGrpcTask = startTask(bluetoothRecordQueue, stub::streamBluetoothSurvey);
                return true;
            case GNSS:
                gnssRecordGrpcTask = startTask(gnssRecordQueue, stub::streamGnssSurvey);
                return true;
            default:
                // CDR is files only.
                return false;
        }
    }

    /** Closes the gRPC streams for one record type. */
    private void stopStream(RecordType type)
    {
        switch (type)
        {
            case CELLULAR:
                gsmRecordGrpcTask = cancelTask(gsmRecordGrpcTask, gsmRecordQueue, true);
                cdmaRecordGrpcTask = cancelTask(cdmaRecordGrpcTask, cdmaRecordQueue, true);
                umtsRecordGrpcTask = cancelTask(umtsRecordGrpcTask, umtsRecordQueue, true);
                lteRecordGrpcTask = cancelTask(lteRecordGrpcTask, lteRecordQueue, true);
                nrRecordGrpcTask = cancelTask(nrRecordGrpcTask, nrRecordQueue, true);
                break;
            case PHONE_STATE:
                phoneStateGrpcTask = cancelTask(phoneStateGrpcTask, phoneStateQueue, true);
                break;
            case WIFI:
                wifiBeaconRecordGrpcTask = cancelTask(wifiBeaconRecordGrpcTask, wifiBeaconRecordQueue, true);
                break;
            case BLUETOOTH:
                bluetoothRecordGrpcTask = cancelTask(bluetoothRecordGrpcTask, bluetoothRecordQueue, true);
                break;
            case GNSS:
                gnssRecordGrpcTask = cancelTask(gnssRecordGrpcTask, gnssRecordQueue, true);
                break;
            default:
                break;
        }
    }

    /**
     * Registers this service as the listener for one record type.
     *
     * @return True if the record type is streamed over gRPC.
     */
    private boolean registerListener(RecordType type)
    {
        switch (type)
        {
            case CELLULAR:
                networkSurveyService.registerCellularSurveyRecordListener(this);
                return true;
            case PHONE_STATE:
                networkSurveyService.registerPhoneStateListener(this);
                return true;
            case WIFI:
                networkSurveyService.registerWifiSurveyRecordListener(this);
                return true;
            case BLUETOOTH:
                networkSurveyService.registerBluetoothSurveyRecordListener(this);
                return true;
            case GNSS:
                networkSurveyService.registerGnssSurveyRecordListener(this);
                return true;
            default:
                return false;
        }
    }

    /** Unregisters this service as the listener for one record type. */
    private void unregisterListener(RecordType type)
    {
        switch (type)
        {
            case CELLULAR:
                networkSurveyService.unregisterCellularSurveyRecordListener(this);
                break;
            case PHONE_STATE:
                networkSurveyService.unregisterPhoneStateListener(this);
                break;
            case WIFI:
                networkSurveyService.unregisterWifiSurveyRecordListener(this);
                break;
            case BLUETOOTH:
                networkSurveyService.unregisterBluetoothSurveyRecordListener(this);
                break;
            case GNSS:
                networkSurveyService.unregisterGnssSurveyRecordListener(this);
                break;
            default:
                break;
        }
    }

    /** Opens the device status stream, which every gRPC connection carries. */
    private void startDeviceStatusStream()
    {
        if (deviceStatusGrpcTask == null)
        {
            final ManagedChannel connectedChannel = channel;
            deviceStatusGrpcTask = startTask(deviceStatusQueue,
                    statusUpdateReplyStreamObserver -> DeviceStatusGrpc.newStub(connectedChannel).statusUpdate(statusUpdateReplyStreamObserver));
        }
        if (networkSurveyService != null) networkSurveyService.registerDeviceStatusListener(this);
    }

    /** Starts a stream task that belongs to the current connection generation. */
    private <MessageType, Reply> GrpcTask<MessageType, Reply> startTask(StreamRecordQueue<MessageType> queue,
                                                                        Function<StreamObserver<Reply>, StreamObserver<MessageType>> call)
    {
        final GrpcTask<MessageType, Reply> task = new GrpcTask<>(this, queue, call, connectionGeneration.get());
        task.executeOnExecutor(executorService);
        return task;
    }

    /**
     * Cancels a stream task, and clears its queue unless the connection is coming back.
     *
     * @param clearQueue False to keep the queued records for the reconnected stream.
     * @return Always null, so the caller can clear its task field in one statement.
     */
    private static <T> T cancelTask(GrpcTask<?, ?> task, StreamRecordQueue<?> queue, boolean clearQueue)
    {
        if (task != null) task.cancel(true);
        if (clearQueue) queue.clear();
        return null;
    }

    /**
     * Closes the connection to the gRPC server. A reconnect ({@code stopService} false) keeps the
     * record listeners registered and the queued records, so the survey keeps scanning while the
     * connection comes back and nothing recorded in between is lost.
     *
     * @param stopService True if the connection is turned off and the service stopped, false if
     *                    another connection attempt follows.
     */
    private void disconnectFromGrpcServer(boolean stopService)
    {
        if (stopService)
        {
            notifyConnectionStateChange(ConnectionState.DISCONNECTING);
            unregisterAllListeners();
        }

        deviceStatusGrpcTask = cancelTask(deviceStatusGrpcTask, deviceStatusQueue, stopService);
        phoneStateGrpcTask = cancelTask(phoneStateGrpcTask, phoneStateQueue, stopService);
        gsmRecordGrpcTask = cancelTask(gsmRecordGrpcTask, gsmRecordQueue, stopService);
        cdmaRecordGrpcTask = cancelTask(cdmaRecordGrpcTask, cdmaRecordQueue, stopService);
        umtsRecordGrpcTask = cancelTask(umtsRecordGrpcTask, umtsRecordQueue, stopService);
        lteRecordGrpcTask = cancelTask(lteRecordGrpcTask, lteRecordQueue, stopService);
        nrRecordGrpcTask = cancelTask(nrRecordGrpcTask, nrRecordQueue, stopService);
        wifiBeaconRecordGrpcTask = cancelTask(wifiBeaconRecordGrpcTask, wifiBeaconRecordQueue, stopService);
        bluetoothRecordGrpcTask = cancelTask(bluetoothRecordGrpcTask, bluetoothRecordQueue, stopService);
        gnssRecordGrpcTask = cancelTask(gnssRecordGrpcTask, gnssRecordQueue, stopService);

        synchronized (this)
        {
            activeStreamTypes.clear();
            wirelessSurveyStub = null;
            connectionSetupComplete = false;
        }

        shutdownChannel(!stopService);

        if (stopService) stopService();
    }

    /** Unregisters this service from every record and device status stream of the survey service. */
    private synchronized void unregisterAllListeners()
    {
        registeredListenerTypes.clear();
        if (networkSurveyService == null)
        {
            Timber.w("NetworkSurveyService is null when disconnecting from the gRPC server, cannot unregister listeners");
            return;
        }
        networkSurveyService.unregisterDeviceStatusListener(this);
        networkSurveyService.unregisterPhoneStateListener(this);
        networkSurveyService.unregisterCellularSurveyRecordListener(this);
        networkSurveyService.unregisterWifiSurveyRecordListener(this);
        networkSurveyService.unregisterBluetoothSurveyRecordListener(this);
        networkSurveyService.unregisterGnssSurveyRecordListener(this);
    }

    /**
     * Tries to perform a handshake with the gRPC Server. This should be done anytime we start a new connection with the
     * server.
     *
     * @return True if the handshake is successful, false otherwise.
     */
    private boolean startConnection(ManagedChannel attemptChannel)
    {
        try
        {
            final ConnectionHandshakeGrpc.ConnectionHandshakeBlockingStub blockingStub = ConnectionHandshakeGrpc
                    .newBlockingStub(attemptChannel).withDeadlineAfter(10, TimeUnit.SECONDS);

            final ConnectionReply connectionReply = blockingStub.startConnection(ConnectionRequest.newBuilder().build());

            return connectionReply != null && connectionReply.getConnectionAccept();
        } catch (Exception e)
        {
            if (e instanceof StatusRuntimeException)
            {
                if (((StatusRuntimeException) e).getStatus().getCode() == Status.Code.UNIMPLEMENTED)
                {
                    // Might be using the old server connection methods, so try those before failing the connection
                    Timber.w(e, "Could not connect to the remote gRPC Server, the server might be using the old connection approach: ");
                }
            } else if (e.getCause() instanceof ConnectException)
            {
                Timber.w(e, "Could not connect to the remote gRPC Server because: ");
            } else
            {
                Timber.e(e, "Could not connect to the remote gRPC Server due to an Exception");
            }
        }

        return false;
    }

    /**
     * Reconnects to the gRPC server using the last known connection settings, unless a newer
     * connect, switch or disconnect request replaced the connection that asked for it.
     *
     * @param generation The {@link #connectionGeneration} of the connection that failed.
     */
    private void reconnectToGrpcServer(int generation)
    {
        if (isStaleAttempt(generation))
        {
            Timber.i("Skipping a gRPC reconnect for a connection that was replaced or turned off");
            return;
        }
        if (host == null || portNumber == null)
        {
            Timber.e("Can't reconnect to the last gRPC server because the host or port is null");
            return;
        }

        Timber.i("Reconnecting to the gRPC server");

        // A new generation, so the other streams of the same failed connection do not reconnect too.
        connectToGrpcServer(host, portNumber, true, connectionGeneration.incrementAndGet());
    }

    /**
     * Closes the gRPC managed channel, and handles any channel cleanup.
     *
     * @param willReconnect True if an attempt is going to be made to reestablish the channel.  False if the connection
     *                      is going to remain disconnected.
     */
    private void shutdownChannel(boolean willReconnect)
    {
        if (channel != null)
        {
            try
            {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (Exception e)
            {
                Timber.w(e, "An exception occurred while trying to shutdown the gRPC Channel");
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
        unregisterAllListeners();

        Timber.i("About to call stopSelf for the GrpcConnectionService");

        stopSelf();
    }

    /**
     * Notify all the registered listeners of the new connection state.
     *
     * @param newConnectionState The new gRPC connection state.
     */
    private synchronized void notifyConnectionStateChange(ConnectionState newConnectionState)
    {
        Timber.i("gRPC Connection State Changed.  oldConnectionState=%s, newConnectionState=%s", connectionState.get(), newConnectionState);

        final ConnectionState oldConnectionState = connectionState.getAndSet(newConnectionState);
        if (newConnectionState == ConnectionState.CONNECTING && oldConnectionState != ConnectionState.CONNECTING)
        {
            QUEUE_HEALTH.onConnecting(System.currentTimeMillis());
        } else if (newConnectionState == ConnectionState.CONNECTED)
        {
            QUEUE_HEALTH.onConnected();
        }

        // gRPC streaming is a mission relevant survey. Signal the survey session so the Mission ID
        // rolls when the connection starts and the session ends when it really disconnects. The
        // connectionState is already updated above, so isGrpcConnectionActive() reflects the new
        // state when NetworkSurveyService evaluates its mission gate. Only CONNECTED and a real
        // DISCONNECTED are forwarded; transient reconnects do not report DISCONNECTED here.
        if (networkSurveyService != null)
        {
            if (newConnectionState == ConnectionState.CONNECTED)
            {
                networkSurveyService.onGrpcConnectionStateChanged(true);
            } else if (newConnectionState == ConnectionState.DISCONNECTED)
            {
                networkSurveyService.onGrpcConnectionStateChanged(false);
            }
        }

        // The connection state is shown in the survey notification owned by the NetworkSurveyService.
        if (networkSurveyService != null) networkSurveyService.updateServiceNotification();
    }

    /**
     * A connection attempt that failed: which server, and when.
     */
    public static final class ConnectionFailure
    {
        public final String host;
        public final int port;
        public final long timeMs;

        public ConnectionFailure(String host, int port, long timeMs)
        {
            this.host = host;
            this.port = port;
            this.timeMs = timeMs;
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
        private final StreamRecordQueue<MessageType> messageQueue;
        private final Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall;

        /** The connection this stream belongs to; a stream of a replaced connection never reconnects. */
        private final int generation;

        private Throwable failed;

        private GrpcTask(GrpcConnectionService serviceWeakReference, StreamRecordQueue<MessageType> queue,
                         Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall, int generation)
        {
            this.serviceWeakReference = new WeakReference<>(serviceWeakReference);
            messageQueue = queue;
            this.asyncStubCall = asyncStubCall;
            this.generation = generation;
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
                        Timber.e(t, "An error occurred in a gRPC stream");
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted()
                    {
                        Timber.i("Completed a gRPC stream");
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

                        final MessageType nextMessageToSend = messageQueue.poll();

                        // I know Thread.sleep() is bad, but after working through a couple different solutions using a
                        // scheduled executor service, I found that solution to be a bit more complicated than I wanted.
                        // Eventually, we could get away from using an error in the gRPC stream to indicate that the
                        // remote server is no longer reachable, but when I added a listener for state changes via
                        // channel.notifyWhenStateChanged(), I seemed to have run into a bug because that call was
                        // actually changing the channel connection state. Therefore, until we can reliable come up with
                        // a way to know when the connection drops, this sleep seems like the best approach.
                        if (nextMessageToSend == null)
                        {
                            Thread.sleep(QUEUE_PROCESSING_SLEEP_TIME);
                            continue;
                        }

                        //Timber.v("Sending a message to the remote gRPC server: %s", nextMessageToSend);

                        outgoingMessageStream.onNext(nextMessageToSend);
                    }
                } catch (InterruptedException ignore)
                {
                    Timber.i("The gRPC task was interrupted");
                } catch (RuntimeException e)
                {
                    // Cancel RPC
                    outgoingMessageStream.onError(e);
                    throw e;
                }

                // Mark the end of the stream
                outgoingMessageStream.onCompleted();

                // A canceled stream belongs to a connection being replaced or turned off; waiting up
                // to a minute for its server here would hold a pool thread the new streams need.
                if (isCancelled()) return false;

                // Receiving happens asynchronously
                if (!finishLatch.await(1, TimeUnit.MINUTES))
                {
                    throw new RuntimeException("Could not finish rpc within 1 minute, the server is likely down");
                }

                if (failed instanceof StatusRuntimeException)
                {
                    return ((StatusRuntimeException) failed).getStatus().getCode() == io.grpc.Status.Code.UNIMPLEMENTED;
                }

                return false;
            } catch (Throwable e)
            {
                // Not "unimplemented": reconnect, so this record type's stream doesn't end silently
                // while the connection still reports CONNECTED.
                Timber.e(e, "The connection to the remote gRPC server closed with an exception");
                return false;
            }
        }

        /**
         * @param unimplemented True if the remote procedure call (RPC) associated with this async task is unimplemented
         *                      on the remote server. In that event, we don't want to attempt a reconnect.
         */
        @Override
        protected void onPostExecute(Boolean unimplemented)
        {
            Timber.i("Completed a gRPC Task, userCanceled=%s, unimplemented=%s", userCanceled, unimplemented);

            if (unimplemented) return;

            GrpcConnectionService grpcConnectionService = serviceWeakReference.get();
            if (grpcConnectionService == null) return;

            // The connection was switched, reconnected or turned off after this stream started, so
            // tearing down now would close the newer connection's streams.
            if (generation != grpcConnectionService.connectionGeneration.get())
            {
                Timber.i("Ignoring the end of a gRPC stream that belonged to a replaced connection");
                return;
            }

            grpcConnectionService.disconnectFromGrpcServer(userCanceled);

            if (!userCanceled) grpcConnectionService.reconnectToGrpcServer(generation);
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
            Timber.i("%s service connected", name);
            final NetworkSurveyService.SurveyServiceBinder binder = (NetworkSurveyService.SurveyServiceBinder) iBinder;
            networkSurveyService = (NetworkSurveyService) binder.getService();

            listenForRequestedRecords();

            // The handshake can finish before this bind does (a cold start on a fast network), in
            // which case the connection setup was deferred until now.
            completeConnectionSetup();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Timber.i("%s service disconnected", name);
        }
    }
}
