package com.craxiom.networksurvey.listeners;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony;

import com.craxiom.networksurvey.model.CdrEventType;
import com.craxiom.networksurvey.model.MmsCdrClassifier;
import com.craxiom.networksurvey.model.MmsCdrClassifier.MmsAddress;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.services.controller.CellularController;
import com.craxiom.networksurvey.services.controller.TelephonyManagerWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import timber.log.Timber;

/**
 * Monitors the MMS content provider so that sent and received MMS messages can be written to the
 * CDR log file. Only metadata (direction, addresses, SIM subscription) is read; the message
 * content and attachments are never touched.
 * <p>
 * This observer must be registered on {@link #MMS_SMS_URI} rather than
 * {@link Telephony.Mms#CONTENT_URI} because the platform MmsProvider only announces outgoing
 * (outbox and sent) rows on the combined mms-sms URI. That URI is also notified for SMS activity,
 * so every change re-reads the newest MMS row and relies on {@link #loggedMmsIds} to avoid
 * logging the same message more than once. Only IDs that were actually logged go in the cache,
 * because an outgoing message keeps its ID while it moves from drafts to the outbox to sent.
 *
 * @since 1.58
 */
public class CdrMmsObserver extends ContentObserver
{
    public static final Uri MMS_SMS_URI = Telephony.MmsSms.CONTENT_URI;

    private static final String MMS_ADDR_PATH = "addr";
    private static final String MMS_SORT_NEWEST_FIRST = Telephony.BaseMmsColumns.DATE + " DESC";
    private static final String[] MMS_PROJECTION = {
            Telephony.BaseMmsColumns._ID,
            Telephony.BaseMmsColumns.MESSAGE_BOX,
            Telephony.BaseMmsColumns.MESSAGE_TYPE,
            Telephony.BaseMmsColumns.SUBSCRIPTION_ID};
    private static final String[] ADDR_PROJECTION = {Telephony.Mms.Addr.TYPE, Telephony.Mms.Addr.ADDRESS};
    private static final int MAX_TRACKED_MESSAGE_IDS = 10;

    private final Map<Long, Long> loggedMmsIds = new EvictingLinkedHashMap<>(MAX_TRACKED_MESSAGE_IDS);

    private final ContentResolver contentResolver;
    private final CellularController cellularController;
    private final SurveyRecordProcessor surveyRecordProcessor;
    private final ExecutorService executorService;

    public CdrMmsObserver(Handler handler, ContentResolver contentResolver,
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
        if (cellularController.isPaused())
        {
            Timber.v("MMS change received but scanning is paused, ignoring");
            return;
        }

        try (Cursor cursor = contentResolver.query(Telephony.Mms.CONTENT_URI, MMS_PROJECTION, null, null, MMS_SORT_NEWEST_FIRST))
        {
            if (cursor == null || !cursor.moveToFirst()) return;

            int idColumn = cursor.getColumnIndex(Telephony.BaseMmsColumns._ID);
            int boxColumn = cursor.getColumnIndex(Telephony.BaseMmsColumns.MESSAGE_BOX);
            int typeColumn = cursor.getColumnIndex(Telephony.BaseMmsColumns.MESSAGE_TYPE);
            int subIdColumn = cursor.getColumnIndex(Telephony.BaseMmsColumns.SUBSCRIPTION_ID);
            if (idColumn < 0 || boxColumn < 0 || typeColumn < 0 || subIdColumn < 0) return;

            CdrEventType eventType = MmsCdrClassifier.classify(cursor.getInt(boxColumn), cursor.getInt(typeColumn));
            if (eventType == null) return;

            long id = cursor.getLong(idColumn);
            if (loggedMmsIds.containsKey(id))
            {
                Timber.v("MMS %d has already been logged to the CDR file, ignoring the change", id);
                return;
            }

            if (submitEvent(eventType, queryAddresses(id), cursor.getInt(subIdColumn)))
            {
                loggedMmsIds.put(id, id);
            }
        } catch (Exception e)
        {
            Timber.e(e, "Could not read the MMS provider for CDR logging");
        }
    }

    /**
     * Reads the address rows ({@code content://mms/{id}/addr}) for a single MMS.
     */
    private List<MmsAddress> queryAddresses(long mmsId)
    {
        List<MmsAddress> addresses = new ArrayList<>();
        Uri addressUri = Uri.withAppendedPath(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId), MMS_ADDR_PATH);
        try (Cursor cursor = contentResolver.query(addressUri, ADDR_PROJECTION, null, null, null))
        {
            if (cursor == null) return addresses;

            int typeColumn = cursor.getColumnIndex(Telephony.Mms.Addr.TYPE);
            int addressColumn = cursor.getColumnIndex(Telephony.Mms.Addr.ADDRESS);
            if (typeColumn < 0 || addressColumn < 0) return addresses;

            while (cursor.moveToNext())
            {
                String address = cursor.getString(addressColumn);
                if (address != null)
                {
                    addresses.add(new MmsAddress(cursor.getInt(typeColumn), address));
                }
            }
        }

        return addresses;
    }

    /**
     * Builds the originating/destination addresses for the event and hands it to the record
     * processor on the executor. Incoming messages mirror the SMS behavior: originating is the
     * sender and destination is this device's number. Outgoing messages list every recipient.
     *
     * @return True if the event was handed off for logging, false if it had to be skipped (so the
     * caller does not mark the message as logged and a later change notification can retry).
     */
    private boolean submitEvent(CdrEventType eventType, List<MmsAddress> addresses, int subscriptionId)
    {
        TelephonyManagerWrapper wrapper = cellularController.getTelephonyManagerForSubscription(subscriptionId);
        if (wrapper == null)
        {
            Timber.w("No telephony manager for subscription %d, skipping the MMS CDR event", subscriptionId);
            return false;
        }

        final String originatingAddress;
        final String destinationAddress;
        if (eventType == CdrEventType.INCOMING_MMS)
        {
            originatingAddress = MmsCdrClassifier.findOriginatingAddress(addresses);
            destinationAddress = wrapper.getPhoneNumber();
        } else
        {
            originatingAddress = wrapper.getPhoneNumber();
            destinationAddress = MmsCdrClassifier.joinDestinationAddresses(addresses);
        }

        try
        {
            executorService.execute(() -> surveyRecordProcessor.onMessageEvent(eventType, originatingAddress,
                    wrapper.getTelephonyManager(), destinationAddress, wrapper.getSubscriptionId()));
            return true;
        } catch (Throwable t)
        {
            Timber.w(t, "Could not submit to the executor service");
            return false;
        }
    }
}
