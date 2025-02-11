package com.craxiom.networksurvey.logging.db.uploader.beacondb;

import com.craxiom.networksurvey.logging.db.model.UploadRecordsWrapper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * Responsible for creating the JSON formatter that converts to the Geosubmit API
 * <p>
 * defined <a href="https://ichnaea.readthedocs.io/en/latest/api/geosubmit2.html#api-geosubmit-latest">here</a>
 */
public class GeosubmitJsonConverterFactory extends Converter.Factory
{
    public static GeosubmitJsonConverterFactory create()
    {
        return new GeosubmitJsonConverterFactory();
    }

    @Override
    public Converter<UploadRecordsWrapper, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit)
    {
        return recordsWrapper -> {
            try
            {
                JSONObject jsonObject = GeosubmitJsonFormatter.formatRecords(
                        recordsWrapper.lteRecords(),
                        recordsWrapper.gsmRecords(),
                        recordsWrapper.umtsRecords(),
                        recordsWrapper.nrRecords(),
                        recordsWrapper.wifiRecords()
                );

                return RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        jsonObject.toString()
                );
            } catch (JSONException e)
            {
                throw new IOException("Error formatting geosubmit JSON", e);
            }
        };
    }
}
