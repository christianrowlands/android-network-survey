package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

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

        final int rejectCause = ParserUtils.extractIntFromToString(networkRegistrationInfoString, ParserUtils.REJECT_CAUSE_KEY);
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

    @Test
    public void extractCarrierAggregationFromString()
    {
        final String networkRegistrationInfoString1 = "NetworkRegistrationInfo=NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME " +
                "networkRegistrationState=HOME roamingType=NOT_ROAMING accessNetworkTechnology=LTE rejectCause=0 emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO] " +
                "cellIdentity=CellIdentityLte:{ mCi=78716683 mPci=36 mTac=31516 mEarfcn=750 mBands=[2] mBandwidth=10000 mMcc=310 mMnc=260 mAlphaLong=Mint " +
                "mAlphaShort=Mint mAdditionalPlmns={} mCsgInfo=null} voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0} dataSpecificInfo=null nrState=**** rRplmn=310260 isUsingCarrierAggregation=false isNonTerrestrialNetwork=TERRESTRIAL}";
        final Boolean isCa1 = ParserUtils.extractCarrierAggregationFromString(networkRegistrationInfoString1);
        assertEquals(false, isCa1);

        final String networkRegistrationInfoString2 = "NetworkRegistrationInfo=NetworkRegistrationInfo{ domain=PS transportType=WWAN registrationState=HOME " +
                "networkRegistrationState=HOME roamingType=NOT_ROAMING accessNetworkTechnology=LTE rejectCause=0 emergencyEnabled=false availableServices=[DATA,MMS] " +
                "cellIdentity=CellIdentityLte:{ mCi=78716683 mPci=36 mTac=31516 mEarfcn=750 mBands=[2, 12, 4] mBandwidth=10000 mMcc=310 mMnc=260 mAlphaLong=Mint " +
                "mAlphaShort=Mint mAdditionalPlmns={} mCsgInfo=null} voiceSpecificInfo=null dataSpecificInfo=android.telephony.DataSpecificRegistrationInfo " +
                ":{ maxDataCalls = 16 isDcNrRestricted = false isNrAvailable = true isEnDcAvailable = true mLteAttachResultType = 2 mLteAttachExtraInfo = 0 " +
                "LteVopsSupportInfo :  mVopsSupport = 2 mEmcBearerSupport = 2 } nrState=**** rRplmn=310260 isUsingCarrierAggregation=true isNonTerrestrialNetwork=TERRESTRIAL}";
        final Boolean isCa2 = ParserUtils.extractCarrierAggregationFromString(networkRegistrationInfoString2);
        assertEquals(true, isCa2);
    }

    @Test
    public void extractCarrierAggregationFromString_missing()
    {
        final String networkRegistrationInfoString = "NetworkRegistrationInfo{ domain=CS transportType=WWAN registrationState=HOME roamingType=NOT_ROAMING " +
                "accessNetworkTechnology=UMTS emergencyEnabled=false availableServices=[VOICE,SMS,VIDEO" +
                "] cellIdentity=CellIdentityWcdma: { mLac=12974 mCid=217522381 mPsc=353 mUarfcn=612 mMcc=310 mMnc=410 mAlphaLong=AT&T mAlphaShort=AT&T " +
                "mAdditionalPlmns={} mCsgInfo=null } voiceSpecificInfo=VoiceSpecificRegistrationInfo { mCssSupported=false mRoamingIndicator=0 " +
                "mSystemIsInPrl=0 mDefaultRoamingIndicator=0 } dataSpecificInfo=null nrState=NONE rRplmn=310410 }";

        final Boolean isCa = ParserUtils.extractCarrierAggregationFromString(networkRegistrationInfoString);
        assertNull(isCa);
    }
}
