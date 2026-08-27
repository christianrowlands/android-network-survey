package com.craxiom.networksurvey.model;

import android.provider.Telephony;

import java.util.List;
import java.util.StringJoiner;

/**
 * Pure helper that turns raw rows from the MMS content provider into CDR event types and
 * originating/destination addresses. Kept free of Android runtime dependencies (the Telephony
 * constants are compile-time constants) so it can be unit tested on the JVM.
 * <p>
 * The PDU message type and address type values come from the MMS Encapsulation Protocol
 * (WAP-209). Android keeps them in the hidden {@code com.google.android.mms.pdu.PduHeaders}
 * class, so they are mirrored here.
 */
public final class MmsCdrClassifier
{
    /**
     * The {@code m_type} value for an outgoing message (M-Send.req).
     */
    public static final int PDU_TYPE_SEND_REQ = 128;

    /**
     * The {@code m_type} value for the notification that an incoming message is waiting on the
     * MMSC (M-Notification.ind). The message has not been downloaded yet at this point.
     */
    public static final int PDU_TYPE_NOTIFICATION_IND = 130;

    /**
     * The {@code m_type} value for a downloaded incoming message (M-Retrieve.conf).
     */
    public static final int PDU_TYPE_RETRIEVE_CONF = 132;

    /**
     * The address {@code type} values from the {@code content://mms/{id}/addr} table.
     */
    public static final int ADDRESS_TYPE_BCC = 129;
    public static final int ADDRESS_TYPE_CC = 130;
    public static final int ADDRESS_TYPE_FROM = 137;
    public static final int ADDRESS_TYPE_TO = 151;

    /**
     * The placeholder the platform stores as the FROM address of an outgoing MMS.
     */
    public static final String INSERT_ADDRESS_TOKEN = "insert-address-token";

    /**
     * Separator used when an MMS has more than one recipient.
     */
    public static final String ADDRESS_SEPARATOR = ";";

    private MmsCdrClassifier()
    {
    }

    /**
     * Maps an MMS row to a CDR event type.
     *
     * @param messageBox     The {@code msg_box} column value.
     * @param pduMessageType The {@code m_type} column value.
     * @return The CDR event type, or null if the row does not represent a loggable event
     * (drafts, failed sends, and not-yet-downloaded notifications are ignored).
     */
    public static CdrEventType classify(int messageBox, int pduMessageType)
    {
        if (pduMessageType == PDU_TYPE_RETRIEVE_CONF && messageBox == Telephony.BaseMmsColumns.MESSAGE_BOX_INBOX)
        {
            return CdrEventType.INCOMING_MMS;
        }

        if (pduMessageType == PDU_TYPE_SEND_REQ
                && (messageBox == Telephony.BaseMmsColumns.MESSAGE_BOX_OUTBOX
                || messageBox == Telephony.BaseMmsColumns.MESSAGE_BOX_SENT))
        {
            return CdrEventType.OUTGOING_MMS;
        }

        return null;
    }

    /**
     * @param addresses The address rows for a single MMS.
     * @return The FROM address, or an empty string if there is none or it is the platform's
     * insert-address-token placeholder.
     */
    public static String findOriginatingAddress(List<MmsAddress> addresses)
    {
        for (MmsAddress address : addresses)
        {
            if (address.type == ADDRESS_TYPE_FROM && !INSERT_ADDRESS_TOKEN.equals(address.address))
            {
                return address.address;
            }
        }

        return "";
    }

    /**
     * @param addresses The address rows for a single MMS.
     * @return All TO, CC, and BCC addresses joined by {@link #ADDRESS_SEPARATOR}, in the order
     * provided, or an empty string if there are none.
     */
    public static String joinDestinationAddresses(List<MmsAddress> addresses)
    {
        StringJoiner joiner = new StringJoiner(ADDRESS_SEPARATOR);
        for (MmsAddress address : addresses)
        {
            if (address.type == ADDRESS_TYPE_TO || address.type == ADDRESS_TYPE_CC || address.type == ADDRESS_TYPE_BCC)
            {
                joiner.add(address.address);
            }
        }

        return joiner.toString();
    }

    /**
     * One row from the {@code content://mms/{id}/addr} table.
     */
    public static final class MmsAddress
    {
        public final int type;
        public final String address;

        public MmsAddress(int type, String address)
        {
            this.type = type;
            this.address = address;
        }
    }
}
