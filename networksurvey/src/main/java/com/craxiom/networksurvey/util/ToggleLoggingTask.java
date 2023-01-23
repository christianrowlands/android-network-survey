package com.craxiom.networksurvey.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A task to move the action of starting or stopping logging off of the UI thread.
 *
 * @since 1.10.0
 */
@SuppressLint("StaticFieldLeak")
public class ToggleLoggingTask extends AsyncTask<Void, Void, Boolean>
{
    private final Supplier<Boolean> toggleLoggingFunction;
    private final Function<Boolean, String> postExecuteFunction;
    private final Context context;

    public ToggleLoggingTask(Supplier<Boolean> toggleLoggingFunction, Function<Boolean, String> postExecuteFunction, Context context)
    {
        this.toggleLoggingFunction = toggleLoggingFunction;
        this.postExecuteFunction = postExecuteFunction;
        this.context = context;
    }

    @Override
    protected Boolean doInBackground(Void... nothing)
    {
        return toggleLoggingFunction.get();
    }

    @Override
    protected void onPostExecute(Boolean enabled)
    {
        Toast.makeText(context, postExecuteFunction.apply(enabled),
                enabled == null ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }
}