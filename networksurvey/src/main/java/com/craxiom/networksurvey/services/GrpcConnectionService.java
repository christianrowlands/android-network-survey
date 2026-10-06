package com.craxiom.networksurvey.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Binder;
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
import com.craxiom.mqttlibrary.IConnectionStateListener;
import com.craxiom.mqttlibrary.connection.ConnectionState;
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IPhoneStateListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.survey.model.Destination;
import com.craxiom.networksurvey.survey.model.RecordType;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private static final String ACTION_CONNECT = "com.craxiom.networksurvey.services.action.connect";
    private static final String ACTION_DISCONNECT = "com.craxiom.networksurvey.services.action.disconnect";

    private static final String HOST_PARAMETER = "com.craxiom.networksurvey.services.extra.host";
    private static final String PORT_PARAMETER = "com.craxiom.networksurvey.services.extra.port";
    private static final String DEVICE_NAME_PARAMETER = "com.craxiom.networksurvey.services.extra.devicename";
    private static final String RECORD_TYPES_PARAMETER = "com.craxiom.networksurvey.services.extra.record_types";
    private static final String ACTION_UPDATE_STREAM_TYPES = "com.craxiom.networksurvey.services.action.update_stream_types";

    private ConnectionServiceBinder connectionServiceBinder;
    private final Handler uiThreadHandler;
    private final SurveyServiceConnection surveyServiceConnection;
    private NetworkSurveyService networkSurveyService;
    private final ScheduledExecutorService executorService;

    private final ConcurrentLinkedQueue<DeviceStatus> deviceStatusQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PhoneState> phoneStateQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<GsmRecord> gsmRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CdmaRecord> cdmaRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<UmtsRecord> umtsRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LteRecord> lteRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<NrRecord> nrRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<WifiBeaconRecord> wifiBeaconRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BluetoothRecord> bluetoothRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<GnssRecord> gnssRecordQueue = new ConcurrentLinkedQueue<>();

    // Cached streaming queue limit value
    private volatile int streamingQueueLimit = 0;

    private final List<IConnectionStateListener> grpcConnectionListeners = new CopyOnWriteArrayList<>();

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

    private String host = null;
    private Integer portNumber = null;
    private String deviceName = "";

    /**
     * The record types requested for this connection, or null when the connect intent did not name
     * any, in which case the types are resolved from the {@link NetworkSurveyService} once the
     * connection is established. Device status is always streamed.
     */
    private volatile Set<RecordType> requestedStreamTypes = null;

    /** The record types that currently have an open gRPC stream and a registered listener. */
    private final Set<RecordType> activeStreamTypes = EnumSet.noneOf(RecordType.class);

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
        connectionServiceBinder = new ConnectionServiceBinder(this);
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
     * Starts this service to connect to a remote gRPC server with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @param context            The context to use to create the service intent.
     * @param connectionSettings The connection settings to use to connect to the gRPC server.
     */
    public static void connectToGrpcServer(Context context, GrpcConnectionSettings connectionSettings)
    {
        connectToGrpcServer(context, connectionSettings, null);
    }

    /**
     * Starts this service to connect to a remote gRPC server streaming the given record types.
     *
     * @param context            The context to use to create the service intent.
     * @param connectionSettings The connection settings to use to connect to the gRPC server.
     * @param recordTypes        The record types to stream, or null to use the record types of the
     *                           survey that is running (or the user's setup) when the connection is
     *                           established.
     */
    public static void connectToGrpcServer(Context context, GrpcConnectionSettings connectionSettings,
                                           Collection<RecordType> recordTypes)
    {
        Timber.d("Creating the ACTION_CONNECT intent to kick off the gRPC connection");

        Intent intent = new Intent(context, GrpcConnectionService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(HOST_PARAMETER, connectionSettings.getHost());
        intent.putExtra(PORT_PARAMETER, connectionSettings.getPort());
        intent.putExtra(DEVICE_NAME_PARAMETER, connectionSettings.getDeviceName());
        if (recordTypes != null) intent.putExtra(RECORD_TYPES_PARAMETER, toWireNames(recordTypes));

        // Report CONNECTING right away rather than when the service gets to the intent, so a Stop in
        // between sees the connection and stops it, and a second start does not connect twice.
        connectionState.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING);
        context.startService(intent);
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
                    requestedStreamTypes = recordTypes == null ? null : fromWireNames(recordTypes);
                    connectToGrpcServer(host, port, deviceName, false, connectionGeneration.incrementAndGet());
                }
            } else if (ACTION_UPDATE_STREAM_TYPES.equals(action))
            {
                final Set<RecordType> types = fromWireNames(intent.getStringArrayExtra(RECORD_TYPES_PARAMETER));
                requestedStreamTypes = types;
                if (isConnected()) applyStreamTypes(types);
            } else if (ACTION_DISCONNECT.equals(action))
            {
                userCanceled = true;
                connectionGeneration.incrementAndGet();
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
        Timber.i("Destroying the Connection Service");

        if (surveyServiceConnection != null)
        {
            getApplicationContext().unbindService(surveyServiceConnection);
        }

        disconnectFromGrpcServer(true);

        // Clear the binder reference to prevent memory leaks
        if (connectionServiceBinder != null)
        {
            connectionServiceBinder.onDestroy();
            connectionServiceBinder = null;
        }

        super.onDestroy();
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        if (isConnected() && deviceStatus != null)
        {
            if (deviceStatusGrpcTask != null && deviceStatusGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                if (canQueueAccept(deviceStatusQueue))
                {
                    deviceStatusQueue.add(deviceStatus);
                }
            }
        }
    }

    @Override
    public void onPhoneState(PhoneState phoneState)
    {
        if (isConnected() && phoneState != null && phoneStateGrpcTask != null && phoneStateGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            if (canQueueAccept(phoneStateQueue))
            {
                phoneStateQueue.add(phoneState);
            }
        }
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        if (isConnected() && gsmRecord != null)
        {
            if (gsmRecordGrpcTask != null && gsmRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                if (canQueueAccept(gsmRecordQueue))
                {
                    gsmRecordQueue.add(gsmRecord);
                }
            }
        }
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        if (isConnected() && cdmaRecord != null)
        {
            if (cdmaRecordGrpcTask != null && cdmaRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                if (canQueueAccept(cdmaRecordQueue))
                {
                    cdmaRecordQueue.add(cdmaRecord);
                }
            }
        }
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        if (isConnected() && umtsRecord != null)
        {
            if (umtsRecordGrpcTask != null && umtsRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                if (canQueueAccept(umtsRecordQueue))
                {
                    umtsRecordQueue.add(umtsRecord);
                }
            }
        }
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        if (isConnected() && lteRecord != null)
        {
            if (lteRecordGrpcTask != null && lteRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                if (canQueueAccept(lteRecordQueue))
                {
                    lteRecordQueue.add(lteRecord);
                }
            }
        }
    }

    @Override
    public void onNrSurveyRecord(NrRecord nrRecord)
    {
        if (isConnected() && nrRecord != null && nrRecordGrpcTask != null && nrRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            if (canQueueAccept(nrRecordQueue))
            {
                nrRecordQueue.add(nrRecord);
            }
        }
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        if (isConnected() && wifiBeaconRecordGrpcTask != null && wifiBeaconRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            if (canQueueAccept(wifiBeaconRecordQueue))
            {
                wifiBeaconRecordQueue.addAll(
                        wifiBeaconRecords.stream().map(WifiRecordWrapper::getWifiBeaconRecord).collect(Collectors.toList()));
            }
        }
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        if (isConnected() && bluetoothRecordGrpcTask != null && bluetoothRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            if (canQueueAccept(bluetoothRecordQueue))
            {
                bluetoothRecordQueue.add(bluetoothRecord);
            }
        }
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        if (isConnected() && bluetoothRecordGrpcTask != null && bluetoothRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            if (canQueueAccept(bluetoothRecordQueue))
            {
                bluetoothRecordQueue.addAll(bluetoothRecords);
            }
        }
    }

    @Override
    public void onGnssSurveyRecord(GnssRecord gnssRecord)
    {
        if (isConnected() && gnssRecord != null && gnssRecordGrpcTask != null && gnssRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            if (canQueueAccept(gnssRecordQueue))
            {
                gnssRecordQueue.add(gnssRecord);
            }
        }
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
     * Checks if a queue can accept more items based on the streaming queue limit.
     * If the limit is 0 (disabled), always returns true.
     *
     * @param queue The queue to check.
     * @return True if the queue can accept more items, false if it has reached the limit.
     */
    private boolean canQueueAccept(ConcurrentLinkedQueue<?> queue)
    {
        if (streamingQueueLimit <= 0) return true; // Limit disabled
        final int currentSize = queue.size();
        if (currentSize >= streamingQueueLimit)
        {
            Timber.w("gRPC streaming queue full (%d >= %d), dropping message", currentSize, streamingQueueLimit);
            return false;
        }
        return true;
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
     * @param generation         The {@link #connectionGeneration} this attempt belongs to. If a newer connect or
     *                           disconnect request arrives before the handshake completes, the attempt is discarded.
     */
    private void connectToGrpcServer(String host, int port, String deviceName, boolean reconnectOnFailure, int generation)
    {
        try
        {
            Timber.d("Starting a connection to the gRPC server");

            this.host = host;
            portNumber = port;
            this.deviceName = deviceName;

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
                        final String errorMessage = "Unable to connect to the Network Survey Server";
                        Timber.w(errorMessage);
                        uiThreadHandler.post(() -> Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show());
                        final boolean attemptReconnection = !userCanceled && reconnectOnFailure;
                        disconnectFromGrpcServer(!attemptReconnection);
                        if (attemptReconnection)
                        {
                            uiThreadHandler.postDelayed(this::reconnectToGrpcServer, RECONNECTION_ATTEMPT_BACKOFF_TIME);
                        }
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

                        // Reported under the same lock as the stale check, so a stop cannot land in
                        // between and have this attempt roll a Mission ID after it.
                        notifyConnectionStateChange(ConnectionState.CONNECTED);
                    }

                    final String message = "Connected to the Network Survey Server!";
                    Timber.i(message);
                    uiThreadHandler.post(() -> Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show());

                    // If the survey service is not bound yet, the bind callback finishes the setup.
                    completeConnectionSetup();
                } catch (Throwable t)
                {
                    Timber.e(t, "An exception occurred in the gRPC connection thread");
                    if (isStaleAttempt(generation)) return;

                    final boolean attemptReconnect = !userCanceled && reconnectOnFailure;

                    disconnectFromGrpcServer(!attemptReconnect);

                    if (attemptReconnect)
                    {
                        uiThreadHandler.postDelayed(this::reconnectToGrpcServer, RECONNECTION_ATTEMPT_BACKOFF_TIME);
                    }
                }
            }).start();
        } catch (Throwable e)
        {
            Timber.e(e, "An exception occurred when trying to connect to the remote gRPC server");

            final boolean attemptReconnect = !userCanceled && reconnectOnFailure;

            disconnectFromGrpcServer(!attemptReconnect);

            if (attemptReconnect)
            {
                uiThreadHandler.postDelayed(this::reconnectToGrpcServer, RECONNECTION_ATTEMPT_BACKOFF_TIME);
            }
        }
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
        applyStreamTypes(resolveStreamTypes());
    }

    /**
     * @return The record types to stream: the ones the connect request named, or else the record
     * types of the running survey (or the user's setup) as reported by the survey service.
     */
    private Set<RecordType> resolveStreamTypes()
    {
        final Set<RecordType> requested = requestedStreamTypes;
        if (requested != null) return requested;

        if (networkSurveyService != null)
        {
            final Set<RecordType> types = networkSurveyService.getStreamTypesForNewDestination(Destination.GRPC);
            requestedStreamTypes = types;
            return types;
        }

        Timber.w("The survey service is not bound yet, so no record types are streamed over gRPC");
        return EnumSet.noneOf(RecordType.class);
    }

    /**
     * Opens a gRPC stream (and registers the record listener) for every record type in [types] that
     * does not have one, and closes the stream of every type no longer in it.
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

        Timber.i("gRPC is streaming %s", activeStreamTypes);
    }

    /**
     * Opens the gRPC streams for one record type and registers this service as its listener.
     *
     * @return True if the record type is supported over gRPC and its stream was opened.
     */
    private boolean startStream(RecordType type)
    {
        final WirelessSurveyGrpc.WirelessSurveyStub stub = wirelessSurveyStub;
        switch (type)
        {
            case CELLULAR:
                gsmRecordGrpcTask = new GrpcTask<>(this, gsmRecordQueue, stub::streamGsmSurvey);
                gsmRecordGrpcTask.executeOnExecutor(executorService);
                cdmaRecordGrpcTask = new GrpcTask<>(this, cdmaRecordQueue, stub::streamCdmaSurvey);
                cdmaRecordGrpcTask.executeOnExecutor(executorService);
                umtsRecordGrpcTask = new GrpcTask<>(this, umtsRecordQueue, stub::streamUmtsSurvey);
                umtsRecordGrpcTask.executeOnExecutor(executorService);
                lteRecordGrpcTask = new GrpcTask<>(this, lteRecordQueue, stub::streamLteSurvey);
                lteRecordGrpcTask.executeOnExecutor(executorService);
                nrRecordGrpcTask = new GrpcTask<>(this, nrRecordQueue, stub::streamNrSurvey);
                nrRecordGrpcTask.executeOnExecutor(executorService);
                networkSurveyService.registerCellularSurveyRecordListener(this);
                return true;
            case PHONE_STATE:
                phoneStateGrpcTask = new GrpcTask<>(this, phoneStateQueue, stub::streamPhoneState);
                phoneStateGrpcTask.executeOnExecutor(executorService);
                networkSurveyService.registerPhoneStateListener(this);
                return true;
            case WIFI:
                wifiBeaconRecordGrpcTask = new GrpcTask<>(this, wifiBeaconRecordQueue, stub::streamWifiBeaconSurvey);
                wifiBeaconRecordGrpcTask.executeOnExecutor(executorService);
                networkSurveyService.registerWifiSurveyRecordListener(this);
                return true;
            case BLUETOOTH:
                bluetoothRecordGrpcTask = new GrpcTask<>(this, bluetoothRecordQueue, stub::streamBluetoothSurvey);
                bluetoothRecordGrpcTask.executeOnExecutor(executorService);
                networkSurveyService.registerBluetoothSurveyRecordListener(this);
                return true;
            case GNSS:
                gnssRecordGrpcTask = new GrpcTask<>(this, gnssRecordQueue, stub::streamGnssSurvey);
                gnssRecordGrpcTask.executeOnExecutor(executorService);
                networkSurveyService.registerGnssSurveyRecordListener(this);
                return true;
            default:
                // CDR is files only.
                return false;
        }
    }

    /**
     * Closes the gRPC streams for one record type and unregisters this service as its listener.
     */
    private void stopStream(RecordType type)
    {
        switch (type)
        {
            case CELLULAR:
                networkSurveyService.unregisterCellularSurveyRecordListener(this);
                gsmRecordGrpcTask = cancelTask(gsmRecordGrpcTask, gsmRecordQueue);
                cdmaRecordGrpcTask = cancelTask(cdmaRecordGrpcTask, cdmaRecordQueue);
                umtsRecordGrpcTask = cancelTask(umtsRecordGrpcTask, umtsRecordQueue);
                lteRecordGrpcTask = cancelTask(lteRecordGrpcTask, lteRecordQueue);
                nrRecordGrpcTask = cancelTask(nrRecordGrpcTask, nrRecordQueue);
                break;
            case PHONE_STATE:
                networkSurveyService.unregisterPhoneStateListener(this);
                phoneStateGrpcTask = cancelTask(phoneStateGrpcTask, phoneStateQueue);
                break;
            case WIFI:
                networkSurveyService.unregisterWifiSurveyRecordListener(this);
                wifiBeaconRecordGrpcTask = cancelTask(wifiBeaconRecordGrpcTask, wifiBeaconRecordQueue);
                break;
            case BLUETOOTH:
                networkSurveyService.unregisterBluetoothSurveyRecordListener(this);
                bluetoothRecordGrpcTask = cancelTask(bluetoothRecordGrpcTask, bluetoothRecordQueue);
                break;
            case GNSS:
                networkSurveyService.unregisterGnssSurveyRecordListener(this);
                gnssRecordGrpcTask = cancelTask(gnssRecordGrpcTask, gnssRecordQueue);
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
            deviceStatusGrpcTask = new GrpcTask<>(this, deviceStatusQueue,
                    statusUpdateReplyStreamObserver -> DeviceStatusGrpc.newStub(connectedChannel).statusUpdate(statusUpdateReplyStreamObserver));
            deviceStatusGrpcTask.executeOnExecutor(executorService);
        }
        if (networkSurveyService != null) networkSurveyService.registerDeviceStatusListener(this);
    }

    /**
     * Cancels a stream task and clears its queue.
     *
     * @return Always null, so the caller can clear its task field in one statement.
     */
    private static <T> T cancelTask(GrpcTask<?, ?> task, ConcurrentLinkedQueue<?> queue)
    {
        if (task != null) task.cancel(true);
        queue.clear();
        return null;
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     *
     * @param stopService True if the service should be stopped after disconnecting from the gRCP server, false otherwise.
     */
    private void disconnectFromGrpcServer(boolean stopService)
    {
        if (stopService) notifyConnectionStateChange(ConnectionState.DISCONNECTING);

        if (networkSurveyService != null)
        {
            networkSurveyService.unregisterDeviceStatusListener(this);
            networkSurveyService.unregisterPhoneStateListener(this);
            networkSurveyService.unregisterCellularSurveyRecordListener(this);
            networkSurveyService.unregisterWifiSurveyRecordListener(this);
            networkSurveyService.unregisterBluetoothSurveyRecordListener(this);
            networkSurveyService.unregisterGnssSurveyRecordListener(this);
        } else
        {
            Timber.w("NetworkSurveyService is null when disconnecting from the gRPC server, cannot unregister listeners");
        }

        if (deviceStatusGrpcTask != null)
        {
            deviceStatusGrpcTask.cancel(true);
            deviceStatusGrpcTask = null;
        }
        if (phoneStateGrpcTask != null)
        {
            phoneStateGrpcTask.cancel(true);
            phoneStateGrpcTask = null;
        }
        if (gsmRecordGrpcTask != null)
        {
            gsmRecordGrpcTask.cancel(true);
            gsmRecordGrpcTask = null;
        }
        if (cdmaRecordGrpcTask != null)
        {
            cdmaRecordGrpcTask.cancel(true);
            cdmaRecordGrpcTask = null;
        }
        if (umtsRecordGrpcTask != null)
        {
            umtsRecordGrpcTask.cancel(true);
            umtsRecordGrpcTask = null;
        }
        if (lteRecordGrpcTask != null)
        {
            lteRecordGrpcTask.cancel(true);
            lteRecordGrpcTask = null;
        }
        if (nrRecordGrpcTask != null)
        {
            nrRecordGrpcTask.cancel(true);
            nrRecordGrpcTask = null;
        }
        if (wifiBeaconRecordGrpcTask != null)
        {
            wifiBeaconRecordGrpcTask.cancel(true);
            wifiBeaconRecordGrpcTask = null;
        }
        if (bluetoothRecordGrpcTask != null)
        {
            bluetoothRecordGrpcTask.cancel(true);
            bluetoothRecordGrpcTask = null;
        }
        if (gnssRecordGrpcTask != null)
        {
            gnssRecordGrpcTask.cancel(true);
            gnssRecordGrpcTask = null;
        }

        synchronized (this)
        {
            activeStreamTypes.clear();
            wirelessSurveyStub = null;
            connectionSetupComplete = false;
        }

        deviceStatusQueue.clear();
        phoneStateQueue.clear();
        gsmRecordQueue.clear();
        cdmaRecordQueue.clear();
        umtsRecordQueue.clear();
        lteRecordQueue.clear();
        nrRecordQueue.clear();
        wifiBeaconRecordQueue.clear();
        bluetoothRecordQueue.clear();
        gnssRecordQueue.clear();

        shutdownChannel(!stopService);

        if (stopService) stopService();
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
     * Used to reconnect to the gRPC server using the last known connection settings.
     */
    private void reconnectToGrpcServer()
    {
        if (host == null || portNumber == null)
        {
            Timber.e("Can't reconnect to the last gRPC server because the host or port is null");
        }

        Timber.i("Reconnecting to the gRPC server");

        connectToGrpcServer(host, portNumber, deviceName, true, connectionGeneration.get());
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
        if (networkSurveyService != null)
        {
            networkSurveyService.unregisterDeviceStatusListener(this);
            networkSurveyService.unregisterPhoneStateListener(this);
            networkSurveyService.unregisterCellularSurveyRecordListener(this);
            networkSurveyService.unregisterWifiSurveyRecordListener(this);
            networkSurveyService.unregisterBluetoothSurveyRecordListener(this);
            networkSurveyService.unregisterGnssSurveyRecordListener(this);
        }

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

        connectionState.set(newConnectionState);

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

        for (IConnectionStateListener listener : grpcConnectionListeners)
        {
            try
            {
                listener.onConnectionStateChange(newConnectionState);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a gRPC Connection State Listener because of an exception");
            }
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always runs in the same process as its clients,
     * we don't need to deal with IPC.
     * <p>
     * This is a static inner class to prevent memory leaks. The binder can be retained by the Android system
     * after the service is destroyed, and if it holds an implicit reference to the outer class (non-static),
     * it will prevent the service from being garbage collected.
     */
    public static class ConnectionServiceBinder extends Binder
    {
        private GrpcConnectionService service;

        public ConnectionServiceBinder(GrpcConnectionService service)
        {
            this.service = service;
        }

        public GrpcConnectionService getService()
        {
            return service;
        }

        public void onDestroy()
        {
            service = null;
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
        private final ConcurrentLinkedQueue<MessageType> messageQueue;
        private final Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall;

        private Throwable failed;

        private GrpcTask(GrpcConnectionService serviceWeakReference, ConcurrentLinkedQueue<MessageType> queue,
                         Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall)
        {
            this.serviceWeakReference = new WeakReference<>(serviceWeakReference);
            messageQueue = queue;
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
                Timber.e(e, "The connection to the remote gRPC server closed with an exception");
                return true;
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
            Timber.i("%s service connected", name);
            final NetworkSurveyService.SurveyServiceBinder binder = (NetworkSurveyService.SurveyServiceBinder) iBinder;
            networkSurveyService = (NetworkSurveyService) binder.getService();

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
