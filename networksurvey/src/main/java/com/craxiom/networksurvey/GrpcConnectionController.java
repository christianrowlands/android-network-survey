package com.craxiom.networksurvey;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGrpcConnectionStateListener;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.messaging.DeviceStatus;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.LteSurveyResponse;
import com.craxiom.networksurvey.messaging.NetworkSurveyStatusGrpc;
import com.craxiom.networksurvey.messaging.StatusUpdateReply;
import com.craxiom.networksurvey.messaging.WirelessSurveyGrpc;
import io.grpc.ManagedChannel;
import io.grpc.android.AndroidChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

    GrpcConnectionController(NetworkSurveyActivity networkSurveyActivity)
    {
        this.networkSurveyActivity = networkSurveyActivity;
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        if (isConnected()) deviceStatusBlockingQueue.add(deviceStatus);
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        if (isConnected()) lteRecordBlockingQueue.add(lteRecord);
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
            notifyConnectionStateChange(ConnectionState.CONNECTING);

            channel = AndroidChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .context(networkSurveyActivity.getApplicationContext())
                    .build();

            notifyConnectionStateChange(ConnectionState.CONNECTED);

            deviceStatusGrpcTask = new GrpcTask<>(this, deviceStatusBlockingQueue,
                    statusUpdateReplyStreamObserver -> NetworkSurveyStatusGrpc.newStub(channel).statusUpdate(statusUpdateReplyStreamObserver));
            deviceStatusGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            lteRecordGrpcTask = new GrpcTask<>(this, lteRecordBlockingQueue,
                    lteRecordReplyStreamObserver -> WirelessSurveyGrpc.newStub(channel).streamLteSurvey(lteRecordReplyStreamObserver));
            lteRecordGrpcTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            channelFinishLatch = new CountDownLatch(2);
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the remote gRPC server");
            shutdownChannel();
            notifyConnectionStateChange(ConnectionState.DISCONNECTED);
        }
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     */
    public void disconnectFromGrpcServer()
    {
        notifyConnectionStateChange(ConnectionState.DISCONNECTED);

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
     * Called whenever a gRPC Task finishes executing.  Once all of the tasks are finished, then the channel is shutdown.
     */
    private void onGrpcTaskFinished()
    {
        channelFinishLatch.countDown();

        if (channelFinishLatch.getCount() <= 0) shutdownChannel();
    }

    /**
     * Closes the gRPC managed channel and assigns null to the instance variable.
     */
    private void shutdownChannel()
    {
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
    private void notifyConnectionStateChange(ConnectionState newConnectionState)
    {
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
    private class GrpcTask<MessageType, Reply> extends AsyncTask<Void, Void, String>
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
        protected String doInBackground(Void... nothing)
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

                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE))
                        {
                            Log.v(LOG_TAG, "Sending a message to the remote gRPC server: " + nextMessageToSend.toString());
                        }

                        outgoingMessageStream.onNext(nextMessageToSend);
                    }
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

                if (failed != null)
                {
                    throw new RuntimeException(failed);
                }

                return ""; // TODO figure out something useful to return from the task
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "The connection to the remote gRPC server closed with an exception", e);
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result)
        {
            Log.i(LOG_TAG, "Completed a gRPC Task");
            GrpcConnectionController grpcConnectionController = controllerWeakReference.get();
            if (grpcConnectionController != null) grpcConnectionController.onGrpcTaskFinished();
        }
    }
}
