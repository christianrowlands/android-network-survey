package com.craxiom.networksurvey;

import android.os.AsyncTask;
import android.util.Log;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGrpcConnectionStateListener;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.messaging.DeviceStatus;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.NetworkSurveyStatusGrpc;
import com.craxiom.networksurvey.messaging.StatusUpdateReply;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A controller for allowing the user to connect to a remote gRPC based server.  This allows them to stream the survey results back to a server.
 *
 * @since 0.0.4
 */
public class GrpcConnectionController implements IDeviceStatusListener, ISurveyRecordListener
{
    private static final String LOG_TAG = GrpcConnectionController.class.getSimpleName();

    private final BlockingQueue<DeviceStatus> deviceStatusBlockingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<LteRecord> lteRecordBlockingQueue = new LinkedBlockingQueue<>();

    private final List<IGrpcConnectionStateListener> grpcConnectionListeners = new CopyOnWriteArrayList<>();

    private GrpcDeviceStatusTask grpcDeviceStatusTask;

    private ConnectionState currentConnectionState;

    public GrpcConnectionController()
    {

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
     * @return True if the connection is successful, false otherwise.
     */
    public boolean connectToGrpcServer(String host, int port)
    {
        try
        {
            notifyConnectionStateChange(ConnectionState.CONNECTING);

            final ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

            grpcDeviceStatusTask = new GrpcDeviceStatusTask(channel, this);
            notifyConnectionStateChange(ConnectionState.CONNECTED);
            grpcDeviceStatusTask.execute();
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the remote gRPC server");
            notifyConnectionStateChange(ConnectionState.DISCONNECTED);
            return false;
        }

        return true;
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     */
    public void disconnectFromGrpcServer()
    {
        if (grpcDeviceStatusTask != null)
        {
            grpcDeviceStatusTask.cancel(true);
        }

        notifyConnectionStateChange(ConnectionState.DISCONNECTED);
    }

    /**
     * @return True if the gRPC connection is active, false otherwise.
     */
    public boolean isConnected()
    {
        return currentConnectionState == ConnectionState.CONNECTED;
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

    private static class GrpcDeviceStatusTask extends AsyncTask<Void, Void, String>
    {
        private final ManagedChannel channel;
        private final WeakReference<GrpcConnectionController> controllerWeakReference;

        private Throwable failed;

        private GrpcDeviceStatusTask(ManagedChannel channel, GrpcConnectionController grpcConnectionController)
        {
            this.channel = channel;
            this.controllerWeakReference = new WeakReference<>(grpcConnectionController);
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
                        final GrpcConnectionController grpcConnectionController = controllerWeakReference.get();
                        if (grpcConnectionController != null)
                        {
                            final DeviceStatus deviceStatus = grpcConnectionController.deviceStatusBlockingQueue.poll(60, TimeUnit.SECONDS);

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

            GrpcConnectionController grpcConnectionController = controllerWeakReference.get();
            if (grpcConnectionController != null)
            {
                grpcConnectionController.notifyConnectionStateChange(ConnectionState.DISCONNECTED);
            }
        }
    }
}
