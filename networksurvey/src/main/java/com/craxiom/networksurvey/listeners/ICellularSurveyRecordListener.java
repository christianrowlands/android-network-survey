package com.craxiom.networksurvey.listeners;

import com.craxiom.networksurvey.messaging.CdmaRecord;
import com.craxiom.networksurvey.messaging.GsmRecord;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.UmtsRecord;

/**
 * Listener interface for those interested in being notified when a new cellular Survey Record is ready.
 *
 * @since 0.0.4
 */
public interface ICellularSurveyRecordListener
{
    /**
     * Called when a new GSM Survey Record is ready.
     *
     * @param gsmRecord the GSM Record.
     * @since 0.0.5
     */
    void onGsmSurveyRecord(GsmRecord gsmRecord);

    /**
     * Called when a new CDMA Survey Record is ready.
     *
     * @param cdmaRecord the CDMA Record.
     * @since 0.0.5
     */
    void onCdmaSurveyRecord(CdmaRecord cdmaRecord);

    /**
     * Called when a new UMTS Survey Record is ready.
     *
     * @param umtsRecord the UMTS Record.
     * @since 0.0.5
     */
    void onUmtsSurveyRecord(UmtsRecord umtsRecord);

    /**
     * Called when a new LTE Survey Record is ready.
     *
     * @param lteRecord the LTE Record.
     */
    void onLteSurveyRecord(LteRecord lteRecord);
}
