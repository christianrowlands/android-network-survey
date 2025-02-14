package com.craxiom.networksurvey.logging.db.uploader.beacondb;

import static com.google.common.net.HttpHeaders.USER_AGENT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.logging.db.model.UploadRecordsWrapper;
import com.craxiom.networksurvey.logging.db.uploader.RequestResult;
import com.craxiom.networksurvey.logging.db.uploader.UploadConstants;
import com.craxiom.networksurvey.logging.db.uploader.UploadResult;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;
import timber.log.Timber;

/**
 * @noinspection NewClassNamingConvention
 */
public interface BeaconDbUploadClient
{
    @POST("v2/geosubmit")
    Call<ResponseBody> uploadToBeaconDB(@Body UploadRecordsWrapper records);

    static BeaconDbUploadClient getInstance()
    {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor())
                .build();

        return new Retrofit.Builder()
                .baseUrl(UploadConstants.BEACONDB_URL)
                .client(client)
                .addConverterFactory(GeosubmitJsonConverterFactory.create())  // Add custom converter
                .addConverterFactory(GsonConverterFactory.create())  // Use Gson for other JSON handling
                .build()
                .create(BeaconDbUploadClient.class);
    }

    class UserAgentInterceptor implements Interceptor
    {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException
        {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header(USER_AGENT, "NetworkSurvey/" + BuildConfig.VERSION_NAME)
                    .build();
            return chain.proceed(requestWithUserAgent);
        }
    }

    /**
     * Takes the result of an upload HTTP call to BeaconDB and returns the corresponding RequestResult.
     */
    static RequestResult handleBeaconDbResponse(int code, @Nullable ResponseBody body)
    {
        if (code >= 200 && code <= 299)
        {
            return RequestResult.Success;
        } else
        {
            String bodyString = "";
            try
            {
                if (body != null) bodyString = body.string();
            } catch (IOException e)
            { //no-op
            }
            Timber.w("Received status code %s when uploading records to BeaconDB; body=%s", code, bodyString);
        }

        if (code >= 500 && code <= 599)
        {
            return RequestResult.ServerError;
        }
        if (code == 400)
        {
            return RequestResult.ConfigurationError;
        }
        if (code == 403)
        {
            return RequestResult.LimitExceeded;
        }

        return RequestResult.ConnectionError;
    }

    static UploadResult mapRequestResultToUploadResult(RequestResult response)
    {
        return switch (response)
        {
            case ConfigurationError -> UploadResult.InvalidData;
            case ServerError -> UploadResult.ServerError;
            case ConnectionError -> UploadResult.ConnectionError;
            case LimitExceeded -> UploadResult.LimitExceeded;
            case Failure -> UploadResult.Failure;
            case InvalidApiKey -> UploadResult.InvalidApiKey;
            case Success ->
            {
                Timber.d("Upload successful.");
                yield UploadResult.Success;
            }
            default -> throw new UnsupportedOperationException(
                    String.format("Unsupported upload result: %s", response)
            );
        };
    }
}
