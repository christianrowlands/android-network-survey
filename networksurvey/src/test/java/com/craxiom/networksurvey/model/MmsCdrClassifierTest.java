package com.craxiom.networksurvey.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.provider.Telephony;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link MmsCdrClassifier}, which turns raw MMS provider rows into CDR event types and
 * originating/destination addresses.
 */
public class MmsCdrClassifierTest
{
    private static final String MY_NUMBER = "15551234567";
    private static final String OTHER_NUMBER = "15559876543";
    private static final String THIRD_NUMBER = "15550001111";

    @Test
    public void classifyInboxRetrieveConfAsIncomingMms()
    {
        assertEquals(CdrEventType.INCOMING_MMS, MmsCdrClassifier.classify(
                Telephony.BaseMmsColumns.MESSAGE_BOX_INBOX, MmsCdrClassifier.PDU_TYPE_RETRIEVE_CONF));
    }

    @Test
    public void classifySentSendReqAsOutgoingMms()
    {
        assertEquals(CdrEventType.OUTGOING_MMS, MmsCdrClassifier.classify(
                Telephony.BaseMmsColumns.MESSAGE_BOX_SENT, MmsCdrClassifier.PDU_TYPE_SEND_REQ));
    }

    @Test
    public void classifyOutboxSendReqAsOutgoingMms()
    {
        assertEquals(CdrEventType.OUTGOING_MMS, MmsCdrClassifier.classify(
                Telephony.BaseMmsColumns.MESSAGE_BOX_OUTBOX, MmsCdrClassifier.PDU_TYPE_SEND_REQ));
    }

    @Test
    public void classifyNotificationIndIsIgnored()
    {
        assertNull(MmsCdrClassifier.classify(
                Telephony.BaseMmsColumns.MESSAGE_BOX_INBOX, MmsCdrClassifier.PDU_TYPE_NOTIFICATION_IND));
    }

    @Test
    public void classifyDraftIsIgnored()
    {
        assertNull(MmsCdrClassifier.classify(
                Telephony.BaseMmsColumns.MESSAGE_BOX_DRAFTS, MmsCdrClassifier.PDU_TYPE_SEND_REQ));
    }

    @Test
    public void classifyFailedIsIgnored()
    {
        assertNull(MmsCdrClassifier.classify(
                Telephony.BaseMmsColumns.MESSAGE_BOX_FAILED, MmsCdrClassifier.PDU_TYPE_SEND_REQ));
    }

    @Test
    public void findOriginatingAddressReturnsFromAddress()
    {
        List<MmsCdrClassifier.MmsAddress> addresses = List.of(
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_TO, MY_NUMBER),
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_FROM, OTHER_NUMBER));

        assertEquals(OTHER_NUMBER, MmsCdrClassifier.findOriginatingAddress(addresses));
    }

    @Test
    public void findOriginatingAddressSkipsInsertAddressToken()
    {
        List<MmsCdrClassifier.MmsAddress> addresses = List.of(
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_FROM, MmsCdrClassifier.INSERT_ADDRESS_TOKEN),
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_TO, OTHER_NUMBER));

        assertEquals("", MmsCdrClassifier.findOriginatingAddress(addresses));
    }

    @Test
    public void joinDestinationAddressesJoinsToCcBccWithSemicolon()
    {
        List<MmsCdrClassifier.MmsAddress> addresses = List.of(
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_FROM, MmsCdrClassifier.INSERT_ADDRESS_TOKEN),
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_TO, OTHER_NUMBER),
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_CC, THIRD_NUMBER),
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_BCC, MY_NUMBER));

        assertEquals(OTHER_NUMBER + ";" + THIRD_NUMBER + ";" + MY_NUMBER,
                MmsCdrClassifier.joinDestinationAddresses(addresses));
    }

    @Test
    public void joinDestinationAddressesSingleRecipient()
    {
        List<MmsCdrClassifier.MmsAddress> addresses = List.of(
                new MmsCdrClassifier.MmsAddress(MmsCdrClassifier.ADDRESS_TYPE_TO, OTHER_NUMBER));

        assertEquals(OTHER_NUMBER, MmsCdrClassifier.joinDestinationAddresses(addresses));
    }

    @Test
    public void joinDestinationAddressesEmptyWhenNoRecipients()
    {
        assertEquals("", MmsCdrClassifier.joinDestinationAddresses(Collections.emptyList()));
    }
}
