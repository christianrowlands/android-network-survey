package com.craxiom.networksurvey.logging.db.uploader.ocid;

import static com.google.common.net.HttpHeaders.USER_AGENT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.logging.db.uploader.RequestResult;
import com.craxiom.networksurvey.logging.db.uploader.UploadConstants;
import com.craxiom.networksurvey.logging.db.uploader.UploadResult;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import timber.log.Timber;

/**
 * @noinspection NewClassNamingConvention
 */
public interface OpenCelliDUploadClient
{
    @Multipart
    @POST("/measure/uploadCsv")
    Call<ResponseBody> uploadToOcid(
            @Part("key") RequestBody apiKey,
            @Part("appId") RequestBody appId,
            @Part MultipartBody.Part dataFile
    );

    static OpenCelliDUploadClient getInstance()
    {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor())
                .build();

        return new Retrofit.Builder()
                .baseUrl(UploadConstants.OPENCELLID_URL)
                .client(client)
                .build()
                .create(OpenCelliDUploadClient.class);
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
     * Takes the result of an upload HTTP call to OCID and returns the corresponding RequestResult.
     */
    static RequestResult handleOcidResponse(int code, @Nullable ResponseBody body)
    {
        String bodyString = "";
        try
        {
            if (body != null)
            {
                bodyString = body.string().trim();
            }
        } catch (IOException e)
        {
            Timber.e(e, "Error reading response body from OCID");
        }

        if (code == 200 && "0,OK".equalsIgnoreCase(bodyString))
        {
            return RequestResult.Success;
        } else
        {
            Timber.w("Received status code %s when uploading records to OCID; body=%s", code, bodyString);
        }

        if (code >= 500 && code <= 599)
        {
            return RequestResult.ServerError;
        }
        if (code == 401 || code == 403 || "Err: Invalid token".equalsIgnoreCase(bodyString))
        {
            return RequestResult.InvalidApiKey;
        }
        if (code == 400)
        {
            return RequestResult.ConfigurationError;
        }
        // don't report captive portals
        if (code != 302)
        {
            if (bodyString.equalsIgnoreCase("Exceeded filesize limit"))
            {
                Timber.e("Exceeded filesize limit.");
            }
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
