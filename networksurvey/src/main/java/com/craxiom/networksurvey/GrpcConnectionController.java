package com.craxiom.networksurvey;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGrpcConnectionStateListener;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.messaging.CdmaRecord;
import com.craxiom.networksurvey.messaging.ConnectionReply;
import com.craxiom.networksurvey.messaging.ConnectionRequest;
import com.craxiom.networksurvey.messaging.DeviceStatus;
import com.craxiom.networksurvey.messaging.GsmRecord;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.LteSurveyResponse;
import com.craxiom.networksurvey.messaging.NetworkSurveyStatusGrpc;
import com.craxiom.networksurvey.messaging.StatusUpdateReply;
import com.craxiom.networksurvey.messaging.UmtsRecord;
import com.craxiom.networksurvey.messaging.WirelessSurveyGrpc;

import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.android.AndroidChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * A controller for allowing the user to connect to a remote gRPC based server.  This allows them to stream the survey results back to a server.
 *
 * @since 0.0.4
 */
public class GrpcConnectionController implements IDeviceStatusListener, ISurveyRecordListener
{
    private static final String LOG_TAG = GrpcConnectionController.class.getSimpleName();

    private final NetworkSurveyActivity networkSurveyActivity;

    private final BlockingQueue<DeviceStatus> deviceStatusBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<LteRecord> lteRecordBlockingQueue = new LinkedBlockingQueue<>();

    private final List<IGrpcConnectionStateListener> grpcConnectionListeners = new CopyOnWriteArrayList<>();

    private ConnectionState currentConnectionState;
    private GrpcTask<DeviceStatus, StatusUpdateReply> deviceStatusGrpcTask;
    private GrpcTask<LteRecord, LteSurveyResponse> lteRecordGrpcTask;
    private ManagedChannel channel;
    private CountDownLatch channelFinishLatch;

    private String host = null;
    private Integer portNumber = null;

    GrpcConnectionController(NetworkSurveyActivity networkSurveyActivity)
    {
        this.networkSurveyActivity = networkSurveyActivity;
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        if (isConnected() && deviceStatus != null) deviceStatusBlockingQueue.add(deviceStatus);
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        // TODO Add support for streaming these records
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        // TODO Add support for streaming these records
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        // TODO Add support for streaming these records
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        if (isConnected() && lteRecord != null) lteRecordBlockingQueue.add(lteRecord);
    }

    public void registerConnectionListener(IGrpcConnectionStateListener connectionListener)
    {
        grpcConnectionListeners.add(connectionListener);
    }

    public void unregisterConnectionListener(IGrpcConnectionStateListener connectionListener)
    {
        grpcConnectionListeners.remove(connectionListener);
    }

    /**
     * Connect to a gRPC server by establishing the {@link ManagedChannel}, and then kick off the appropriate tasks so that
     * streaming is started.
     * <p>
     * If the server is already connected, then first disconnect and then start a new connection.
     *
     * @param host The Host Name or IP Address of the remote gRPC server.
     * @param port The Port Number of the gRPC server.
     */
    public void connectToGrpcServer(String host, int port)
    {
        try
        {
            this.host = host;
            this.portNumber = port;

            new Thread(() -> {

                Thread.currentThread().setName("gRPC Connection Thread");
                channel = AndroidChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .context(networkSurveyActivity.getApplicationContext())
                        .build();

                channel.notifyWhenStateChanged(ConnectivityState.CONNECTING, () -> notifyConnectionStateChange(ConnectionState.CONNECTING));
                channel.notifyWhenStateChanged(ConnectivityState.SHUTDOWN, () -> notifyConnectionStateChange(ConnectionState.DISCONNECTED));
                channel.notifyWhenStateChanged(ConnectivityState.IDLE, () -> notifyConnectionStateChange(ConnectionState.CONNECTED));
                channel.notifyWhenStateChanged(ConnectivityState.READY, () -> notifyConnectionStateChange(ConnectionState.CONNECTED));

                if (!startConnection())
                {
                    final String errorMessage = "Unable to connect to the Server";
                    Log.w(LOG_TAG, errorMessage);
                    networkSurveyActivity.runOnUiThread(() -> Toast.makeText(networkSurveyActivity, errorMessage, Toast.LENGTH_SHORT).show());
                    shutdownChannel();
                    notifyConnectionStateChange(ConnectionState.DISCONNECTED);
                    return;
                }

                final String message = "Connected to the Server!";
                Log.i(LOG_TAG, message);
                networkSurveyActivity.runOnUiThread(() -> Toast.makeText(networkSurveyActivity, message, Toast.LENGTH_SHORT).show());

                channelFinishLatch = new CountDownLatch(2);

                deviceStatusGrpcTask = new GrpcTask<>(this, deviceStatusBlockingQueue,
                        statusUpdateReplyStreamObserver -> NetworkSurveyStatusGrpc.newStub(channel).statusUpdate(statusUpdateReplyStreamObserver));
                deviceStatusGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                lteRecordGrpcTask = new GrpcTask<>(this, lteRecordBlockingQueue,
                        lteRecordReplyStreamObserver -> WirelessSurveyGrpc.newStub(channel).streamLteSurvey(lteRecordReplyStreamObserver));
                lteRecordGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }).start();
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the remote gRPC server", e);
            shutdownChannel();
        }
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     */
    public void disconnectFromGrpcServer()
    {
        if (deviceStatusGrpcTask != null) deviceStatusGrpcTask.cancel(true);

        if (lteRecordGrpcTask != null) lteRecordGrpcTask.cancel(true);

        shutdownChannel();
    }

    /**
     * @return True if the gRPC connection is active, false otherwise.
     */
    boolean isConnected()
    {
        return currentConnectionState == ConnectionState.CONNECTED;
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
     * Called whenever a gRPC Task finishes executing.  Once all of the tasks are finished, then the channel is shutdown.
     */
    private void onGrpcTaskFinished()
    {
        channelFinishLatch.countDown();

        if (channelFinishLatch.getCount() <= 0) shutdownChannel();
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

        connectToGrpcServer(host, portNumber);
    }

    /**
     * Closes the gRPC managed channel and assigns null to the instance variable.
     */
    private void shutdownChannel()
    {
        notifyConnectionStateChange(ConnectionState.DISCONNECTED);
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
            Log.i(LOG_TAG, "gRPC Connection State Changed.  oldConnectionState=" + currentConnectionState + ", newConnectionState=" + newConnectionState);
        }

        currentConnectionState = newConnectionState;

        for (IGrpcConnectionStateListener listener : grpcConnectionListeners)
        {
            try
            {
                listener.onGrpcConnectionStateChange(newConnectionState);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to notify a gRPC Connection State Listener because of an exception", e);
            }
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
        private final WeakReference<GrpcConnectionController> controllerWeakReference;
        private final BlockingQueue<MessageType> messageBlockingQueue;
        private final Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall;

        private Throwable failed;

        private GrpcTask(GrpcConnectionController grpcConnectionController, BlockingQueue<MessageType> messageBlockingQueue,
                         Function<StreamObserver<Reply>, StreamObserver<MessageType>> asyncStubCall)
        {
            this.controllerWeakReference = new WeakReference<>(grpcConnectionController);
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
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted()
                    {
                        finishLatch.countDown();
                    }
                };

                final StreamObserver<MessageType> outgoingMessageStream = asyncStubCall.apply(responseObserver);

                try
                {
                    while (finishLatch.getCount() != 0)
                    {
                        final GrpcConnectionController grpcConnectionController = controllerWeakReference.get();
                        if (grpcConnectionController == null)
                        {
                            finishLatch.countDown();
                            break;
                        }

                        final MessageType nextMessageToSend = messageBlockingQueue.poll(60, TimeUnit.SECONDS);

                        if (nextMessageToSend == null) continue;

                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE))
                        {
                            Log.v(LOG_TAG, "Sending a message to the remote gRPC server: " + nextMessageToSend.toString());
                        }

                        outgoingMessageStream.onNext(nextMessageToSend);
                    }
                } catch (InterruptedException ignore)
                {
                    Log.i(LOG_TAG, "The Connection was interrupted, likely due to the user stopping the connection");
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
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "The connection to the remote gRPC server closed with an exception", e);
                return false;  // TODO Should this be true so that a reconnection happens?
            }
        }

        @Override
        protected void onPostExecute(Boolean reconnect)
        {
            Log.i(LOG_TAG, "Completed a gRPC Task, should reconnect= " + reconnect);
            GrpcConnectionController grpcConnectionController = controllerWeakReference.get();
            if (grpcConnectionController != null)
            {
                grpcConnectionController.onGrpcTaskFinished();
                grpcConnectionController.reconnectToGrpcServer();
            }
        }
    }
}
