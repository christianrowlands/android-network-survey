package com.craxiom.networksurvey.logging.db.uploader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    public void markSuccessful(UploadTarget target)
    {
        results.put(target, UploadResult.Success);
    }

    public void setResult(UploadTarget target, UploadResult result)
    {
        results.put(target, result);
    }

    public Map<UploadTarget, UploadResult> getResults()
    {
        return Collections.unmodifiableMap(results);
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

    public boolean isRetryableError()
    {
        for (UploadTarget target : UploadTarget.values())
        {
            UploadResult result = results.get(target);
            if (result == null) return false;
            return switch (result)
            {
                case ServerError, ConnectionError, Failure -> true;
                case LimitExceeded, PermissionDenied, InvalidApiKey, InvalidData, DeleteFailed,
                     Cancelled, NoData, NotStarted, Success, PartiallySucceeded,
                     UploadDisabledForTarget -> false;
            };
        }
        return false;
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
            case Success, PartiallySucceeded, UploadDisabledForTarget -> false;
            case NotStarted, NoData, ConnectionError, ServerError, InvalidApiKey, InvalidData,
                 Failure,
                 DeleteFailed, Cancelled, LimitExceeded, PermissionDenied -> true;
        };
    }
}
