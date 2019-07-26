package com.craxiom.networksurvey;

import com.craxiom.networksurvey.messaging.LteRecord;

public interface ISurveyRecordListener
{
    void onLteSurveyRecord(LteRecord lteRecord);
}
