package com.craxiom.networksurvey.logging.db.uploader;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The result of a community upload for each target (OpenCelliD and BeaconDB).
 */
public class UploadResultBundle
{
    private final Map<UploadTarget, UploadResult> results = new EnumMap<>(UploadTarget.class);

    public UploadResultBundle()
    {
        for (UploadTarget target : UploadTarget.values())
        {
            results.put(target, UploadResult.NotStarted);
        }
    }

    public UploadResult getResult(UploadTarget target)
    {
        return results.getOrDefault(target, UploadResult.NotStarted);
    }

    public void setResult(UploadTarget target, UploadResult result)
    {
        results.put(target, result);
    }

    public boolean isAllSuccess()
    {
        for (UploadTarget target : UploadTarget.values())
        {
            if (isConsideredFailure(results.get(target)))
            {
                return false;
            }
        }
        return true;
    }

    public boolean hasAnyFailures()
    {
        for (UploadTarget target : UploadTarget.values())
        {
            if (isConsideredFailure(results.get(target)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @return True if any target failed in a way that can heal on its own (the server or the
     * connection), so trying again later can succeed.
     */
    public boolean isRetryableError()
    {
        for (UploadTarget target : UploadTarget.values())
        {
            if (isRetryable(results.get(target))) return true;
        }
        return false;
    }

    private static boolean isRetryable(UploadResult result)
    {
        if (result == null) return false;
        return switch (result)
        {
            case ServerError, ConnectionError, Failure -> true;
            case LimitExceeded, PermissionDenied, InvalidApiKey, InvalidData, DeleteFailed,
                 Cancelled, NoData, NotStarted, Success, PartiallySucceeded,
                 UploadDisabledForTarget -> false;
        };
    }

    public void markAllFailure()
    {
        for (UploadTarget target : UploadTarget.values())
        {
            results.put(target, UploadResult.Failure);
        }
    }

    public void markAllCancelled()
    {
        for (UploadTarget target : UploadTarget.values())
        {
            results.put(target, UploadResult.Cancelled);
        }
    }

    public void merge(UploadResultBundle other)
    {
        for (UploadTarget target : UploadTarget.values())
        {
            UploadResult existingResult = getResult(target);
            UploadResult newResult = other.getResult(target);

            // If no existing result, just set the new one
            if (existingResult == null)
            {
                setResult(target, newResult);
                continue;
            }

            // If no new result, keep the existing one
            if (newResult == null)
            {
                continue;
            }

            // Choose the more severe result
            if (isMoreSevere(newResult, existingResult))
            {
                setResult(target, newResult);
            }
        }
    }

    private boolean isMoreSevere(UploadResult newResult, UploadResult existingResult)
    {
        List<UploadResult> severityOrder = UploadResult.SEVERITY_ORDER;

        // Higher index means more severe
        return severityOrder.indexOf(newResult) < severityOrder.indexOf(existingResult);
    }

    private boolean isConsideredFailure(UploadResult uploadResult)
    {
        if (uploadResult == null) return true;
        return switch (uploadResult)
        {
            // A target with nothing to send this time has nothing to fail
            case Success, PartiallySucceeded, UploadDisabledForTarget, NoData -> false;
            case NotStarted, ConnectionError, ServerError, InvalidApiKey, InvalidData,
                 Failure,
                 DeleteFailed, Cancelled, LimitExceeded, PermissionDenied -> true;
        };
    }
}
