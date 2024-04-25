package com.craxiom.networksurvey.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.CallSuper;
import androidx.fragment.app.Fragment;

import com.craxiom.networksurvey.services.NetworkSurveyService;

import timber.log.Timber;

/**
 * Abstract base class for all "Service Data" fragments. In other words, any fragments that want to display data that
 * the {@link com.craxiom.networksurvey.services.NetworkSurveyService} is responsible for generating. This base class
 * provides some common code that is needed to start (if necessary) and bind to the service so that the fragment can
 * register as a listener to the Service's data.
 *
 * @since 1.6.0
 */
public abstract class AServiceDataFragment extends Fragment
{
    protected final SurveyServiceConnection surveyServiceConnection = new SurveyServiceConnection();

    /**
     * The reference to the bound service. Will be null before and after the service is bound.
     */
    protected NetworkSurveyService service;

    @Override
    public void onPause()
    {
        if (service != null) onSurveyServiceDisconnecting(service);

        try
        {
            requireContext().getApplicationContext().unbindService(surveyServiceConnection);
        } catch (Throwable t)
        {
            Timber.e(t, "Could not unbind the service because it is not bound.");
        }

        super.onPause();
    }

    @Override
    public void onDestroy()
    {
        service = null;

        super.onDestroy();
    }

    /**
     * Called once the service has been bound and set to {@link #service}. This is the appropriate time to interact
     * with the service in any way needed for the Fragment's specific use case such as registering listeners
     * or initializing fragment state.
     *
     * @param service The service reference to make calls on.
     */
    protected abstract void onSurveyServiceConnected(NetworkSurveyService service);

    /**
     * Cleanup by unregistering any data listeners. This will be called right before the service is unbinded.
     *
     * @param surveyService The service reference to make calls on.
     */
    @CallSuper
    protected void onSurveyServiceDisconnecting(NetworkSurveyService surveyService)
    {
        service = null;
    }

    /**
     * Start the Network Survey Service (it won't start if it is already started), and then bind to the service.
     * <p>
     * Starting the service will cause the basic items to be initialized. The next step is to bind to it (which this
     * method does) and then register a listener for the appropriate data types. The act of registering a listener
     * causes the data to be generated if you are the first listener.
     */
    protected void startAndBindToService()
    {
        try
        {
            // Start the survey service
            @SuppressWarnings("ConstantConditions") final Context applicationContext = getContext().getApplicationContext();
            final Intent startServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
            applicationContext.startService(startServiceIntent);

            // Bind to the service
            final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
            final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, Context.BIND_ABOVE_CLIENT);
            Timber.i("NetworkSurveyService bound in a Fragment: %s", bound);
        } catch (IllegalStateException e)
        {
            // It appears that an IllegalStateException will occur if the user opens this app but the then quickly
            // switches away from it. The IllegalStateException indicates that we can't call startService while the
            // app is in the background. We catch this here so that we can prevent the app from crashing.
            Timber.w(e, "Could not start/bind the Network Survey service.");
        } catch (Throwable t)
        {
            Timber.e(t, "Something unexpected went wrong when trying to start/bind ot the service");
        }
    }

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link #service}.
     */
    private class SurveyServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder)
        {
            Timber.i("%s service connected", name);

            service = (NetworkSurveyService) ((NetworkSurveyService.SurveyServiceBinder) binder).getService();
            onSurveyServiceConnected(service);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Timber.e("%s service disconnected", name);
            service = null;
        }
    }
}
