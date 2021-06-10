package com.craxiom.networksurvey.util;

import com.craxiom.networksurvey.util.ParserUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Basic unit tests for the {@link ParserUtils} class.
 *
 * @since 1.4.0
 */
public class ParserUtilsTest
{
    @Test
    public void extractRejectCode_zero()
    {
        final String networkRegistrationInfoString = "NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME roamingType=NOT_ROAMING " +
                "accessNetworkTechnology=UMTS rejectCause=0 emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO" +
                "] cellIdentity=CellIdentityWcdma: { mLac=12974 mCid=217522381 mPsc=353 mUarfcn=612 mMcc=310 mMnc=410 mAlphaLong=AT&T mAlphaShort=AT&T " +
                "mAdditionalPlmns={} mCsgInfo=null } voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0 } dataSpecificInfo=null nrState=NONE rRplmn=310410 }";

        final int rejectCause = ParserUtils.extractRejectCause(networkRegistrationInfoString);
        assertEquals(0, rejectCause);
    }

    @Test
    public void extractRejectCode_empty()
    {
        final String networkRegistrationInfoString = "NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME roamingType=NOT_ROAMING " +
                "accessNetworkTechnology=UMTS rejectCause= emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO" +
                "] cellIdentity=CellIdentityWcdma: { mLac=12974 mCid=217522381 mPsc=353 mUarfcn=612 mMcc=310 mMnc=410 mAlphaLong=AT&T mAlphaShort=AT&T " +
                "mAdditionalPlmns={} mCsgInfo=null } voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0 } dataSpecificInfo=null nrState=NONE rRplmn=310410 }";

        final int rejectCause = ParserUtils.extractRejectCause(networkRegistrationInfoString);
        assertEquals(Integer.MAX_VALUE, rejectCause);
    }

    @Test
    public void extractRejectCode_oneDigit()
    {
        final String networkRegistrationInfoString = "NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME roamingType=NOT_ROAMING " +
                "accessNetworkTechnology=UMTS rejectCause=6 emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO" +
                "] cellIdentity=CellIdentityWcdma: { mLac=12974 mCid=217522381 mPsc=353 mUarfcn=612 mMcc=310 mMnc=410 mAlphaLong=AT&T mAlphaShort=AT&T " +
                "mAdditionalPlmns={} mCsgInfo=null } voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0 } dataSpecificInfo=null nrState=NONE rRplmn=310410 }";

        final int rejectCause = ParserUtils.extractRejectCause(networkRegistrationInfoString);
        assertEquals(6, rejectCause);
    }

    @Test
    public void extractRejectCode_twoDigit()
    {
        final String networkRegistrationInfoString = "NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME roamingType=NOT_ROAMING " +
                "accessNetworkTechnology=UMTS rejectCause=12 emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO" +
                "] cellIdentity=CellIdentityWcdma: { mLac=12974 mCid=217522381 mPsc=353 mUarfcn=612 mMcc=310 mMnc=410 mAlphaLong=AT&T mAlphaShort=AT&T " +
                "mAdditionalPlmns={} mCsgInfo=null } voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0 } dataSpecificInfo=null nrState=NONE rRplmn=310410 }";

        final int rejectCause = ParserUtils.extractRejectCause(networkRegistrationInfoString);
        assertEquals(12, rejectCause);
    }

    @Test
    public void extractRejectCode_threeDigit()
    {
        final String networkRegistrationInfoString = "NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME roamingType=NOT_ROAMING " +
                "accessNetworkTechnology=UMTS rejectCause=123 emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO" +
                "] cellIdentity=CellIdentityWcdma: { mLac=12974 mCid=217522381 mPsc=353 mUarfcn=612 mMcc=310 mMnc=410 mAlphaLong=AT&T mAlphaShort=AT&T " +
                "mAdditionalPlmns={} mCsgInfo=null } voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0 } dataSpecificInfo=null nrState=NONE rRplmn=310410 }";

        final int rejectCause = ParserUtils.extractRejectCause(networkRegistrationInfoString);
        assertEquals(123, rejectCause);
    }

    @Test
    public void extractRejectCode_missingKey()
    {
        final String networkRegistrationInfoString = "NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME roamingType=NOT_ROAMING " +
                "accessNetworkTechnology=UMTS emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO" +
                "] cellIdentity=CellIdentityWcdma: { mLac=12974 mCid=217522381 mPsc=353 mUarfcn=612 mMcc=310 mMnc=410 mAlphaLong=AT&T mAlphaShort=AT&T " +
                "mAdditionalPlmns={} mCsgInfo=null } voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0 } dataSpecificInfo=null nrState=NONE rRplmn=310410 }";

        final int rejectCause = ParserUtils.extractRejectCause(networkRegistrationInfoString);
        assertEquals(Integer.MAX_VALUE, rejectCause);
    }
}
