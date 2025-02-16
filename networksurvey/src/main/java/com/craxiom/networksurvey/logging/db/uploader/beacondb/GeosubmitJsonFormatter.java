package com.craxiom.networksurvey.logging.db.uploader.beacondb;

import com.craxiom.networksurvey.logging.db.model.BaseRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Converts the database survey records to the Geosubmit API format defined
 * <a href="https://ichnaea.readthedocs.io/en/latest/api/geosubmit2.html#api-geosubmit-latest">here</a>
 */
public class GeosubmitJsonFormatter
{
    public static JSONObject formatRecords(
            List<LteRecordEntity> lteRecords,
            List<GsmRecordEntity> gsmRecords,
            List<UmtsRecordEntity> umtsRecords,
            List<NrRecordEntity> nrRecords,
            List<WifiBeaconRecordEntity> wifiRecords) throws JSONException
    {

        JSONArray itemsArray = new JSONArray();

        for (GsmRecordEntity record : gsmRecords)
        {
            itemsArray.put(formatGsmRecord(record));
        }

        for (UmtsRecordEntity record : umtsRecords)
        {
            itemsArray.put(formatUmtsRecord(record));
        }

        for (LteRecordEntity record : lteRecords)
        {
            itemsArray.put(formatLteRecord(record));
        }

        for (NrRecordEntity record : nrRecords)
        {
            itemsArray.put(formatNrRecord(record));
        }

        for (WifiBeaconRecordEntity record : wifiRecords)
        {
            itemsArray.put(formatWifiRecord(record));
        }

        JSONObject geosubmitJson = new JSONObject();
        geosubmitJson.put("items", itemsArray);

        return geosubmitJson;
    }

    private static JSONObject formatGsmRecord(GsmRecordEntity record) throws JSONException
    {
        JSONObject item = createBaseRecord(record);

        JSONArray cellTowers = new JSONArray();
        JSONObject cellTower = new JSONObject();
        cellTower.put("radioType", "gsm");
        cellTower.put("mobileCountryCode", record.mcc);
        cellTower.put("mobileNetworkCode", record.mnc);
        cellTower.put("locationAreaCode", record.lac);
        cellTower.put("cellId", record.ci);
        cellTower.put("signalStrength", record.signalStrength);
        cellTower.put("timingAdvance", record.ta);
        cellTower.put("serving", record.servingCell ? 1 : 0);
        cellTowers.put(cellTower);

        item.put("cellTowers", cellTowers);
        return item;
    }

    private static JSONObject formatUmtsRecord(UmtsRecordEntity record) throws JSONException
    {
        JSONObject item = createBaseRecord(record);

        JSONArray cellTowers = new JSONArray();
        JSONObject cellTower = new JSONObject();
        cellTower.put("radioType", "wcdma");
        cellTower.put("mobileCountryCode", record.mcc);
        cellTower.put("mobileNetworkCode", record.mnc);
        cellTower.put("locationAreaCode", record.lac);
        cellTower.put("cellId", record.cid);
        cellTower.put("primaryScramblingCode", record.psc);
        cellTower.put("signalStrength", record.rscp);
        cellTower.put("serving", record.servingCell ? 1 : 0);
        cellTowers.put(cellTower);

        item.put("cellTowers", cellTowers);
        return item;
    }

    private static JSONObject formatLteRecord(LteRecordEntity record) throws JSONException
    {
        JSONObject item = createBaseRecord(record);

        JSONArray cellTowers = new JSONArray();
        JSONObject cellTower = new JSONObject();
        cellTower.put("radioType", "lte");
        cellTower.put("mobileCountryCode", record.mcc);
        cellTower.put("mobileNetworkCode", record.mnc);
        cellTower.put("locationAreaCode", record.tac);
        cellTower.put("cellId", record.eci);
        cellTower.put("primaryScramblingCode", record.pci);
        cellTower.put("signalStrength", record.rsrp);
        cellTower.put("timingAdvance", record.ta);
        cellTower.put("primaryScramblingCode", record.pci);
        cellTower.put("serving", record.servingCell ? 1 : 0);
        cellTowers.put(cellTower);

        item.put("cellTowers", cellTowers);
        return item;
    }

    private static JSONObject formatNrRecord(NrRecordEntity record) throws JSONException
    {
        JSONObject item = createBaseRecord(record);

        JSONArray cellTowers = new JSONArray();
        JSONObject cellTower = new JSONObject();
        cellTower.put("radioType", "nr");
        cellTower.put("mobileCountryCode", record.mcc);
        cellTower.put("mobileNetworkCode", record.mnc);
        cellTower.put("locationAreaCode", record.tac);
        cellTower.put("cellId", record.nci);
        cellTower.put("primaryScramblingCode", record.pci);
        cellTower.put("signalStrength", record.ssRsrp);
        cellTower.put("timingAdvance", record.ta);
        cellTower.put("serving", record.servingCell ? 1 : 0);
        cellTowers.put(cellTower);

        item.put("cellTowers", cellTowers);
        return item;
    }

    private static JSONObject formatWifiRecord(WifiBeaconRecordEntity record) throws JSONException
    {
        JSONObject item = createBaseRecord(record);

        JSONArray wifiAccessPoints = new JSONArray();
        JSONObject wifiAp = new JSONObject();
        wifiAp.put("ssid", record.ssid);
        wifiAp.put("macAddress", record.bssid);
        wifiAp.put("channel", record.channel);
        wifiAp.put("frequency", record.frequencyMhz);
        wifiAp.put("radioType", record.standard);
        wifiAp.put("signalStrength", record.signalStrength);
        if (record.snr != null)
        {
            wifiAp.put("signalToNoiseRatio", record.snr);
        }

        wifiAccessPoints.put(wifiAp);
        item.put("wifiAccessPoints", wifiAccessPoints);
        return item;
    }

    private static JSONObject createBaseRecord(BaseRecordEntity record) throws JSONException
    {
        JSONObject item = new JSONObject();
        item.put("timestamp", System.currentTimeMillis());

        JSONObject position = new JSONObject();
        position.put("latitude", record.latitude);
        position.put("longitude", record.longitude);
        position.put("accuracy", record.accuracy);
        position.put("altitude", record.altitude);
        position.put("speed", record.speed);
        if (record.locationAge != 0) position.put("age", record.locationAge);
        //position.put("source", "gps");
        item.put("position", position);

        return item;
    }
}
