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
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.navigation.NavDeepLinkBuilder;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.DeviceStatusData;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.PhoneState;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.WifiBeaconRecord;
import com.craxiom.messaging.grpc.CdmaSurveyResponse;
import com.craxiom.messaging.grpc.ConnectionHandshakeGrpc;
import com.craxiom.messaging.grpc.ConnectionReply;
import com.craxiom.messaging.grpc.ConnectionRequest;
import com.craxiom.messaging.grpc.DeviceStatusGrpc;
import com.craxiom.messaging.grpc.GsmSurveyResponse;
import com.craxiom.messaging.grpc.LteSurveyResponse;
import com.craxiom.messaging.grpc.NrSurveyResponse;
import com.craxiom.messaging.grpc.StatusUpdateReply;
import com.craxiom.messaging.grpc.UmtsSurveyResponse;
import com.craxiom.messaging.grpc.WifiBeaconSurveyResponse;
import com.craxiom.messaging.grpc.WirelessSurveyGrpc;
import com.craxiom.mqttlibrary.IConnectionStateListener;
import com.craxiom.mqttlibrary.connection.ConnectionState;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.messaging.NetworkSurveyStatusGrpc;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.util.IOUtils;
import com.craxiom.networksurvey.util.LegacyRecordConversion;
import com.craxiom.networksurvey.util.MathUtils;
import com.google.protobuf.Int32Value;

import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
public class GrpcConnectionService extends Service implements IDeviceStatusListener, ICellularSurveyRecordListener, IWifiSurveyRecordListener
{
    public static final long RECONNECTION_ATTEMPT_BACKOFF_TIME = 10_000L;
    private static final int DEVICE_STATUS_REFRESH_RATE_MS = 15_000;
    // number of concurrent linked queues. Does not take into account the old queues
    private static final int NUMBER_OF_QUEUES_TO_PROCESS = 7;
    private static final int QUEUE_PROCESSING_SLEEP_TIME = 1_000;

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

    private final ScheduledExecutorService executorService;

    private final ConcurrentLinkedQueue<DeviceStatus> deviceStatusQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<GsmRecord> gsmRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CdmaRecord> cdmaRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<UmtsRecord> umtsRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LteRecord> lteRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<NrRecord> nrRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<WifiBeaconRecord> wifiBeaconRecordQueue = new ConcurrentLinkedQueue<>();

    private final List<IConnectionStateListener> grpcConnectionListeners = new CopyOnWriteArrayList<>();

    // Old connection approach, delete this when we can update all the grpc code
    private final ConcurrentLinkedQueue<com.craxiom.networksurvey.messaging.DeviceStatus> oldDeviceStatusQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<com.craxiom.networksurvey.messaging.GsmRecord> oldGsmRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<com.craxiom.networksurvey.messaging.CdmaRecord> oldCdmaRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<com.craxiom.networksurvey.messaging.UmtsRecord> oldUmtsRecordQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<com.craxiom.networksurvey.messaging.LteRecord> oldLteRecordQueue = new ConcurrentLinkedQueue<>();
    private GrpcTask<com.craxiom.networksurvey.messaging.DeviceStatus, com.craxiom.networksurvey.messaging.StatusUpdateReply> oldDeviceStatusGrpcTask;
    private GrpcTask<com.craxiom.networksurvey.messaging.GsmRecord, com.craxiom.networksurvey.messaging.GsmSurveyResponse> oldGsmRecordGrpcTask;
    private GrpcTask<com.craxiom.networksurvey.messaging.CdmaRecord, com.craxiom.networksurvey.messaging.CdmaSurveyResponse> oldCdmaRecordGrpcTask;
    private GrpcTask<com.craxiom.networksurvey.messaging.UmtsRecord, com.craxiom.networksurvey.messaging.UmtsSurveyResponse> oldUmtsRecordGrpcTask;
    private GrpcTask<com.craxiom.networksurvey.messaging.LteRecord, com.craxiom.networksurvey.messaging.LteSurveyResponse> oldLteRecordGrpcTask;

    // New connection approach
    private GrpcTask<DeviceStatus, StatusUpdateReply> deviceStatusGrpcTask;
    private GrpcTask<GsmRecord, GsmSurveyResponse> gsmRecordGrpcTask;
    private GrpcTask<CdmaRecord, CdmaSurveyResponse> cdmaRecordGrpcTask;
    private GrpcTask<UmtsRecord, UmtsSurveyResponse> umtsRecordGrpcTask;
    private GrpcTask<LteRecord, LteSurveyResponse> lteRecordGrpcTask;
    private GrpcTask<NrRecord, NrSurveyResponse> nrRecordGrpcTask;
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

    /**
     * To support both the new and old gRPC connections, we keep track of if we were able to use the newer connection
     * setup. Eventually we can get rid of this, but for now we support both.
     *
     * @since 0.2.0
     */
    private boolean oldConnectionApproach = false;

    public GrpcConnectionService()
    {
        connectionState = ConnectionState.DISCONNECTED;

        connectionServiceBinder = new ConnectionServiceBinder();
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
        Timber.d("Creating the ACTION_CONNECT intent to kick off the gRPC connection");

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
        Timber.i("NetworkSurveyService bound in the GrpcConnectionService: %s", bound);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
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
                if (host == null || port == -1 || deviceName == null)
                {
                    Timber.e("A valid hostname (%s) and port (%d) is required to connect to a gRPC server", host, port);
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
        Timber.i("Destroying the Connection Service");

        if (surveyServiceConnection != null)
        {
            getApplicationContext().unbindService(surveyServiceConnection);
        }

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
        if (isConnected() && deviceStatus != null)
        {
            if (deviceStatusGrpcTask != null && deviceStatusGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                deviceStatusQueue.add(deviceStatus);
            } else if (oldConnectionApproach && oldDeviceStatusGrpcTask != null && oldDeviceStatusGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                oldDeviceStatusQueue.add(LegacyRecordConversion.convertDeviceStatus(deviceStatus));
            }
        }
    }

    @Override
    public void onPhoneState(PhoneState phoneState)
    {
        // No-op... for now
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        if (isConnected() && gsmRecord != null)
        {
            if (gsmRecordGrpcTask != null && gsmRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                gsmRecordQueue.add(gsmRecord);
            } else if (oldConnectionApproach && oldGsmRecordGrpcTask != null && oldGsmRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                oldGsmRecordQueue.add(LegacyRecordConversion.convertGsmRecord(gsmRecord));
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
                cdmaRecordQueue.add(cdmaRecord);
            } else if (oldConnectionApproach && oldCdmaRecordGrpcTask != null && oldCdmaRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                oldCdmaRecordQueue.add(LegacyRecordConversion.convertCdmaRecord(cdmaRecord));
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
                umtsRecordQueue.add(umtsRecord);
            } else if (oldConnectionApproach && oldUmtsRecordGrpcTask != null && oldUmtsRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                oldUmtsRecordQueue.add(LegacyRecordConversion.convertUmtsRecord(umtsRecord));
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
                lteRecordQueue.add(lteRecord);
            } else if (oldConnectionApproach && oldLteRecordGrpcTask != null && oldLteRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
            {
                oldLteRecordQueue.add(LegacyRecordConversion.convertLteRecord(lteRecord));
            }
        }
    }

    @Override
    public void onNrSurveyRecord(NrRecord nrRecord)
    {
        if (isConnected() && nrRecord != null && nrRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            nrRecordQueue.add(nrRecord);
        }
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        if (isConnected() && wifiBeaconRecordGrpcTask != null && wifiBeaconRecordGrpcTask.getStatus() != AsyncTask.Status.FINISHED)
        {
            wifiBeaconRecordQueue.addAll(
                    wifiBeaconRecords.stream().map(WifiRecordWrapper::getWifiBeaconRecord).collect(Collectors.toList()));
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
            Timber.d("Starting a connection to the gRPC server");

            this.host = host;
            portNumber = port;
            this.deviceName = deviceName;

            notifyConnectionStateChange(ConnectionState.CONNECTING);
            initializeDeviceStatusReport(deviceStatusGeneratorTaskId.incrementAndGet());

            new Thread(() -> {
                try
                {
                    Thread.currentThread().setName("gRPC Connection Thread");
                    final Context applicationContext = getApplicationContext();
                    channel = AndroidChannelBuilder.forAddress(host, port)
                            .usePlaintext()
                            .context(applicationContext)
                            .build();

                    if (!startConnection())
                    {
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

                    notifyConnectionStateChange(ConnectionState.CONNECTED);
                    final String message = "Connected to the Network Survey Server!";
                    Timber.i(message);
                    uiThreadHandler.post(() -> Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show());

                    if (oldConnectionApproach)
                    {
                        // TODO Delete all this old approach code once we have a chance to update any older gPRC code
                        oldDeviceStatusGrpcTask = new GrpcTask<>(this, oldDeviceStatusQueue,
                                statusUpdateReplyStreamObserver -> NetworkSurveyStatusGrpc.newStub(channel).statusUpdate(statusUpdateReplyStreamObserver));
                        oldDeviceStatusGrpcTask.executeOnExecutor(executorService);

                        final com.craxiom.networksurvey.messaging.WirelessSurveyGrpc.WirelessSurveyStub wirelessSurveyStub = com.craxiom.networksurvey.messaging.WirelessSurveyGrpc.newStub(channel);

                        oldGsmRecordGrpcTask = new GrpcTask<>(this, oldGsmRecordQueue, wirelessSurveyStub::streamGsmSurvey);
                        oldGsmRecordGrpcTask.executeOnExecutor(executorService);

                        oldCdmaRecordGrpcTask = new GrpcTask<>(this, oldCdmaRecordQueue, wirelessSurveyStub::streamCdmaSurvey);
                        oldCdmaRecordGrpcTask.executeOnExecutor(executorService);

                        oldUmtsRecordGrpcTask = new GrpcTask<>(this, oldUmtsRecordQueue, wirelessSurveyStub::streamUmtsSurvey);
                        oldUmtsRecordGrpcTask.executeOnExecutor(executorService);

                        oldLteRecordGrpcTask = new GrpcTask<>(this, oldLteRecordQueue, wirelessSurveyStub::streamLteSurvey);
                        oldLteRecordGrpcTask.executeOnExecutor(executorService);
                    } else
                    {
                        deviceStatusGrpcTask = new GrpcTask<>(this, deviceStatusQueue,
                                statusUpdateReplyStreamObserver -> DeviceStatusGrpc.newStub(channel).statusUpdate(statusUpdateReplyStreamObserver));
                        deviceStatusGrpcTask.executeOnExecutor(executorService);

                        final WirelessSurveyGrpc.WirelessSurveyStub wirelessSurveyStub = WirelessSurveyGrpc.newStub(channel);

                        gsmRecordGrpcTask = new GrpcTask<>(this, gsmRecordQueue, wirelessSurveyStub::streamGsmSurvey);
                        gsmRecordGrpcTask.executeOnExecutor(executorService);

                        cdmaRecordGrpcTask = new GrpcTask<>(this, cdmaRecordQueue, wirelessSurveyStub::streamCdmaSurvey);
                        cdmaRecordGrpcTask.executeOnExecutor(executorService);

                        umtsRecordGrpcTask = new GrpcTask<>(this, umtsRecordQueue, wirelessSurveyStub::streamUmtsSurvey);
                        umtsRecordGrpcTask.executeOnExecutor(executorService);

                        lteRecordGrpcTask = new GrpcTask<>(this, lteRecordQueue, wirelessSurveyStub::streamLteSurvey);
                        lteRecordGrpcTask.executeOnExecutor(executorService);

                        nrRecordGrpcTask = new GrpcTask<>(this, nrRecordQueue, wirelessSurveyStub::streamNrSurvey);
                        nrRecordGrpcTask.executeOnExecutor(executorService);

                        wifiBeaconRecordGrpcTask = new GrpcTask<>(this, wifiBeaconRecordQueue, wirelessSurveyStub::streamWifiBeaconSurvey);
                        wifiBeaconRecordGrpcTask.executeOnExecutor(executorService);
                    }
                } catch (Throwable t)
                {
                    Timber.e(t, "An exception occurred in the gRPC connection thread");

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
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     *
     * @param stopService True if the service should be stopped after disconnecting from the gRCP server, false otherwise.
     */
    private void disconnectFromGrpcServer(boolean stopService)
    {
        if (stopService) notifyConnectionStateChange(ConnectionState.DISCONNECTING);

        if (oldDeviceStatusGrpcTask != null)
        {
            oldDeviceStatusGrpcTask.cancel(true);
            oldDeviceStatusGrpcTask = null;
        }
        if (oldGsmRecordGrpcTask != null)
        {
            oldGsmRecordGrpcTask.cancel(true);
            oldGsmRecordGrpcTask = null;
        }
        if (oldCdmaRecordGrpcTask != null)
        {
            oldCdmaRecordGrpcTask.cancel(true);
            oldCdmaRecordGrpcTask = null;
        }
        if (oldUmtsRecordGrpcTask != null)
        {
            oldUmtsRecordGrpcTask.cancel(true);
            oldUmtsRecordGrpcTask = null;
        }
        if (oldLteRecordGrpcTask != null)
        {
            oldLteRecordGrpcTask.cancel(true);
            oldLteRecordGrpcTask = null;
        }

        if (deviceStatusGrpcTask != null)
        {
            deviceStatusGrpcTask.cancel(true);
            deviceStatusGrpcTask = null;
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

        shutdownChannel(!stopService);

        if (stopService) stopService();
    }

    /**
     * Tries to perform a handshake with the gRPC Server. This should be done anytime we start a new connection with the
     * server. First, a connection is attempted using the newer connection approach. If the method is unimplemented
     * then try the old connection approach.
     *
     * @return True if the handshake is successful, false otherwise.
     */
    private boolean startConnection()
    {
        try
        {
            final ConnectionHandshakeGrpc.ConnectionHandshakeBlockingStub blockingStub = ConnectionHandshakeGrpc
                    .newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS); // TODO make the timeout a user preference

            final ConnectionReply connectionReply = blockingStub.startConnection(ConnectionRequest.newBuilder().build());

            final boolean successful = connectionReply != null && connectionReply.getConnectionAccept();
            if (successful) oldConnectionApproach = false;
            return successful;
        } catch (Exception e)
        {
            if (e instanceof StatusRuntimeException)
            {
                if (((StatusRuntimeException) e).getStatus().getCode() == Status.Code.UNIMPLEMENTED)
                {
                    // Might be using the old server connection methods, so try those before failing the connection
                    final NetworkSurveyStatusGrpc.NetworkSurveyStatusBlockingStub blockingStub = NetworkSurveyStatusGrpc
                            .newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

                    final com.craxiom.networksurvey.messaging.ConnectionReply connectionReply = blockingStub
                            .startConnection(com.craxiom.networksurvey.messaging.ConnectionRequest.newBuilder().build());

                    final boolean successful = connectionReply != null && connectionReply.getConnectionAccept();
                    if (successful) oldConnectionApproach = true;
                    return successful;
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
                        Timber.d("Stopping the device status report because the task ID has changed");
                        return;
                    }

                    onDeviceStatus(generateDeviceStatus());

                    deviceStatusReportHandler.postDelayed(this, DEVICE_STATUS_REFRESH_RATE_MS);
                } catch (SecurityException e)
                {
                    Timber.e(e, "Could not get the required permissions to generate a device status message");
                }
            }
        }, 1000L);
    }

    /**
     * Generate a device status message that can be sent to any remote servers.
     *
     * @return A Device Status message that can be sent to a remote server.
     */
    private DeviceStatus generateDeviceStatus()
    {
        final DeviceStatusData.Builder dataBuilder = DeviceStatusData.newBuilder();
        dataBuilder.setDeviceSerialNumber(deviceId)
                .setDeviceName(deviceName)
                .setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));

        if (gpsListener != null)
        {
            final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));
            }
        }

        final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent batteryStatus = registerReceiver(null, intentFilter);
        if (batteryStatus != null)
        {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final float batteryPercent = (level / (float) scale) * 100;
            dataBuilder.setBatteryLevelPercent(Int32Value.of((int) batteryPercent));
        }

        final DeviceStatus.Builder statusBuilder = DeviceStatus.newBuilder();
        statusBuilder.setMessageType(DeviceStatusMessageConstants.DEVICE_STATUS_MESSAGE_TYPE);
        statusBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        statusBuilder.setData(dataBuilder);

        return statusBuilder.build();
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
            Timber.i("Removing the connection notification");

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
            Timber.e("Can't reconnect to the last gRPC server because the host or port is null");
        }

        Timber.i("Reconnecting to the gRPC server");

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
            networkSurveyService.unregisterCellularSurveyRecordListener(this);
            networkSurveyService.unregisterWifiSurveyRecordListener(this);
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
        Timber.i("gRPC Connection State Changed.  oldConnectionState=%s, newConnectionState=%s", connectionState, newConnectionState);

        connectionState = newConnectionState;

        updateConnectionNotification();

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

                        Timber.v("Sending a message to the remote gRPC server: %s", nextMessageToSend);

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
            deviceId = networkSurveyService.getDeviceId();
            gpsListener = networkSurveyService.getGpsListener();
            networkSurveyService.registerCellularSurveyRecordListener(GrpcConnectionService.this);
            networkSurveyService.registerWifiSurveyRecordListener(GrpcConnectionService.this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Timber.i("%s service disconnected", name);
        }
    }
}
