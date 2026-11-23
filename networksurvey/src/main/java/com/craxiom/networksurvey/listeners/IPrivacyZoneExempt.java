package com.craxiom.networksurvey.listeners;

/**
 * Marks a survey record listener that still receives records while the phone is in a privacy zone,
 * although it is not a live screen ({@link ILiveScanView}). Only the Watchlist detector is exempt: a
 * watched network still raises its alert, joins the history and goes out as a watchlist event with
 * its location, by the user's choice. A log file, stream or upload must never be exempt.
 * <p>
 * The test {@code PrivacyZoneSinkWalkTest} lists every class allowed to be exempt or live, so a new
 * one has to be added there on purpose.
 */
public interface IPrivacyZoneExempt
{
}
