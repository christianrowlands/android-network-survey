/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.craxiom.networksurvey.logging.db.uploader;

import com.craxiom.networksurvey.R;

import java.util.Arrays;
import java.util.List;

/**
 * This code was pulled from the Tower Collector app and modified to work with Network Survey.
 * <p>
 * See: <a href="https://github.com/zamojski/TowerCollector/blob/e7709a4a74a113bf9cccc8db0ecd0cd04b022383/app/src/main/java/info/zamojski/soft/towercollector/enums/UploadResult.java#L7">here</a>
 */
public enum UploadResult
{
    NotStarted, UploadDisabledForTarget, NoData, Success, PartiallySucceeded, ConnectionError, ServerError, InvalidApiKey, InvalidData, Failure, DeleteFailed, Cancelled, PermissionDenied, LimitExceeded;

    public static final List<UploadResult> SEVERITY_ORDER = Arrays.asList(
            LimitExceeded,
            PermissionDenied,
            ServerError,
            ConnectionError,
            InvalidApiKey,
            InvalidData,
            Failure,
            DeleteFailed,
            Cancelled,
            NoData,
            Success,
            PartiallySucceeded,
            UploadDisabledForTarget,
            NotStarted
    );

    public static int getMessage(UploadResult uploadResult)
    {
        return switch (uploadResult)
        {
            case NotStarted -> R.string.uploader_not_started;
            case UploadDisabledForTarget -> R.string.uploader_disabled;
            case NoData -> R.string.uploader_no_data;
            case InvalidApiKey -> R.string.uploader_invalid_api_key;
            case InvalidData -> R.string.uploader_invalid_input_data;
            case Cancelled -> R.string.uploader_aborted;
            case Success -> R.string.uploader_success;
            case PartiallySucceeded -> R.string.uploader_partially_succeeded;
            case DeleteFailed -> R.string.uploader_delete_failed;
            case ConnectionError -> R.string.uploader_connection_error;
            case ServerError -> R.string.uploader_server_error;
            case Failure -> R.string.uploader_failure;
            case PermissionDenied -> R.string.permission_denied;
            case LimitExceeded -> R.string.uploader_limit_exceeded;
            default -> R.string.unknown_error;
        };
    }

    public int getDescription()
    {
        return switch (this)
        {
            case NotStarted -> R.string.uploader_not_started_description;
            case UploadDisabledForTarget -> R.string.uploader_disabled_description;
            case NoData -> R.string.uploader_no_data_description;
            case InvalidApiKey -> R.string.uploader_invalid_api_key_description;
            case InvalidData -> R.string.uploader_invalid_input_data_description;
            case Cancelled -> R.string.uploader_aborted_description;
            case Success -> R.string.uploader_success_description;
            case PartiallySucceeded -> R.string.uploader_partially_succeeded_description;
            case DeleteFailed -> R.string.uploader_delete_failed_description;
            case ConnectionError -> R.string.uploader_connection_error_description;
            case ServerError -> R.string.uploader_server_error_description;
            case Failure -> R.string.uploader_failure_description;
            case PermissionDenied -> R.string.permission_uploader_denied_message;
            case LimitExceeded -> R.string.uploader_limit_exceeded_description;
            default -> R.string.unknown_error;
        };
    }
}
