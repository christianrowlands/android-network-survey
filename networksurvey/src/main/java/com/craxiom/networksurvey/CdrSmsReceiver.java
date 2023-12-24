package com.craxiom.networksurvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.craxiom.networksurvey.services.NetworkSurveyService;

import timber.log.Timber;

/**
 * Handles receiving new SMS message events from the Android OS and converting it to a local
 * broadcast message so that the {@link NetworkSurveyService} can add it to the CDR log file if
 * CDR logging is enabled.
 *
 * @since 1.11
 */
public class CdrSmsReceiver extends BroadcastReceiver
{
    public static final String SMS_RECEIVED_INTENT = "SmsReceivedIntent";
    public static final String ORIGINATING_ADDRESS_EXTRA = "OriginatingAddress";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        Timber.d("Received the sms received intent action");

        final SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null) return;

        for (SmsMessage message : messages)
        {
            Intent smsIntent = new Intent(SMS_RECEIVED_INTENT);
            String originatingAddress = message.getOriginatingAddress();
            smsIntent.putExtra(ORIGINATING_ADDRESS_EXTRA, originatingAddress);
            LocalBroadcastManager.getInstance(context).sendBroadcast(smsIntent);
        }
    }
}
