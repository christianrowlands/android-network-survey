package com.craxiom.networksurvey.logging.db.uploader.ocid;

import static com.google.common.net.HttpHeaders.USER_AGENT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.logging.db.uploader.RequestResult;
import com.craxiom.networksurvey.logging.db.uploader.UploadEndpoints;
import com.craxiom.networksurvey.logging.db.uploader.UploadResponses;
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
                .baseUrl(UploadEndpoints.getOpenCelliDBaseUrl())
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

    /** The body OCID answers a successful upload with. */
    String OCID_OK_BODY = "0,OK";
    /** The body OCID answers a bad key with when the request is URL-encoded, with a 200. */
    String OCID_INVALID_TOKEN_BODY = "Err: Invalid token";
    /** The body OCID answers an upload that is too large with. */
    String OCID_FILESIZE_LIMIT_BODY = "Exceeded filesize limit";

    /**
     * Takes the result of an upload HTTP call to OCID and returns the corresponding RequestResult.
     *
     * @param body The response body, or for a non-2xx response the error body.
     */
    static RequestResult handleOcidResponse(int code, @Nullable ResponseBody body)
    {
        String bodyString = "";
        try
        {
            if (body != null)
            {
                bodyString = body.string();
            }
        } catch (IOException e)
        {
            Timber.e(e, "Error reading response body from OCID");
        }
        return handleOcidResponse(code, bodyString);
    }

    /**
     * Maps an OCID upload response to its RequestResult. A 403 that is an HTML page comes from the
     * proxy in front of OCID, not from OCID's key check (as when OCID blocked every multipart
     * request in October 2026), so it is a server problem that a later retry can get past; any
     * other 403 is still a rejected key.
     */
    static RequestResult handleOcidResponse(int code, @NonNull String body)
    {
        final String bodyString = body.trim();
        if (code == 200 && OCID_OK_BODY.equalsIgnoreCase(bodyString))
        {
            return RequestResult.Success;
        } else
        {
            Timber.w("Received status code %s when uploading records to OCID; body=%s", code, UploadResponses.logSnippet(bodyString));
        }

        if (code == 403)
        {
            return UploadResponses.looksLikeHtml(bodyString) ? RequestResult.ServerError : RequestResult.InvalidApiKey;
        }
        if (code == 401 || OCID_INVALID_TOKEN_BODY.equalsIgnoreCase(bodyString))
        {
            return RequestResult.InvalidApiKey;
        }
        if (code >= 500 && code <= 599)
        {
            return RequestResult.ServerError;
        }
        if (code == 400)
        {
            return RequestResult.ConfigurationError;
        }
        if (bodyString.equalsIgnoreCase(OCID_FILESIZE_LIMIT_BODY))
        {
            Timber.e("Exceeded filesize limit.");
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
