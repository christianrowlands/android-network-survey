package com.craxiom.networksurvey.listeners;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import com.craxiom.networksurvey.model.CdrEventType;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.services.controller.CellularController;
import com.craxiom.networksurvey.services.controller.TelephonyManagerWrapper;

import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;

import timber.log.Timber;

/**
 * Responsible for monitoring for incoming SMS messages so that they can be written to the CDR log
 * file. Note that the SMS content is not included, just the metadata.
 */
public class CdrSmsObserver extends ContentObserver
{
    public static final Uri SMS_URI = Uri.parse("content://sms");
    private static final String SMS_COLUMN_ID = "_id";
    private static final String SMS_COLUMN_TYPE = "type";
    private static final String SMS_COLUMN_ADDRESS = "address";
    private static final String SMS_COLUMN_SUB_ID = "sub_id";
    private static final int SMS_MESSAGE_TYPE_SENT = 2;
    private static final int SMS_MESSAGE_TYPE_RECEIVED = 1;

    private final LinkedHashMap<String, String> smsIdQueue = new EvictingLinkedHashMap();

    private final ContentResolver contentResolver;
    private final CellularController cellularController;
    private final SurveyRecordProcessor surveyRecordProcessor;
    private final ExecutorService executorService;

    public CdrSmsObserver(Handler handler, ContentResolver contentResolver,
                          CellularController cellularController, SurveyRecordProcessor processor,
                          ExecutorService executorService)
    {
        super(handler);
        this.contentResolver = contentResolver;
        this.cellularController = cellularController;
        surveyRecordProcessor = processor;
        this.executorService = executorService;
    }

    @Override
    public void onChange(boolean selfChange)
    {
        // Do I need "date DESC" in the query?
        try (Cursor cursor = contentResolver.query(SMS_URI, null, null, null, null))
        {
            if (cursor != null && cursor.moveToFirst())
            {
                int idColumn = cursor.getColumnIndex(SMS_COLUMN_ID);
                String id = cursor.getString(idColumn);
                // So this is a huge hack, but apparently this content resolver approach for getting
                // outgoing text messages is not officially supported by Android. The result is that
                // I am seeing this onChange event called 3 times for each outgoing message. To prevent
                // duplicate entries in the CDR file we keep track of the most recent messages.
                if (smsIdQueue.containsKey(id))
                {
                    Timber.d("Duplicate outgoing SMS message seen in the CDR processor. Ignoring it.");
                    return;
                }
                smsIdQueue.put(id, id);

                int typeColumn = cursor.getColumnIndex(SMS_COLUMN_TYPE);
                if (typeColumn < 0) return;
                int type = cursor.getInt(typeColumn);

                if (type == SMS_MESSAGE_TYPE_SENT)
                {
                    int addressColumn = cursor.getColumnIndex(SMS_COLUMN_ADDRESS);
                    if (addressColumn < 0) return;
                    int subIdColumn = cursor.getColumnIndex(SMS_COLUMN_SUB_ID);
                    if (subIdColumn < 0) return;
                    String destinationAddress = cursor.getString(addressColumn);
                    int subscriptionId = cursor.getInt(subIdColumn);
                    TelephonyManagerWrapper wrapper = cellularController.getTelephonyManagerForSubscription(subscriptionId);
                    if (wrapper != null)
                    {
                        try
                        {
                            executorService.execute(() -> surveyRecordProcessor.onSmsEvent(CdrEventType.OUTGOING_SMS,
                                    wrapper.getPhoneNumber(), wrapper.getTelephonyManager(), destinationAddress,
                                    wrapper.getSubscriptionId()));
                        } catch (Throwable t)
                        {
                            Timber.w(t, "Could not submit to the executor service");
                        }
                    }
                } else if (type == SMS_MESSAGE_TYPE_RECEIVED)
                {
                    int addressColumn = cursor.getColumnIndex(SMS_COLUMN_ADDRESS);
                    if (addressColumn < 0) return;
                    int subIdColumn = cursor.getColumnIndex(SMS_COLUMN_SUB_ID);
                    if (subIdColumn < 0) return;
                    String sourceAddress = cursor.getString(addressColumn);
                    int subscriptionId = cursor.getInt(subIdColumn);
                    TelephonyManagerWrapper wrapper = cellularController.getTelephonyManagerForSubscription(subscriptionId);
                    if (wrapper != null)
                    {
                        try
                        {
                            executorService.execute(() -> surveyRecordProcessor.onSmsEvent(CdrEventType.INCOMING_SMS,
                                    sourceAddress, wrapper.getTelephonyManager(), wrapper.getPhoneNumber(),
                                    wrapper.getSubscriptionId()));
                        } catch (Throwable t)
                        {
                            Timber.w(t, "Could not submit to the executor service");
                        }
                    }
                }
            }
        }
    }

    /**
     * Acts as a cache and evicts older entries when new ones are added.
     */
    private static class EvictingLinkedHashMap extends LinkedHashMap<String, String>
    {
        @Override
        protected boolean removeEldestEntry(Entry<String, String> eldest)
        {
            return size() > 10;
        }
    }
}

