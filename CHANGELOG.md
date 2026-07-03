# Changelog

## [1.56](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.56) - 2026-07-03

**BUG FIXES**
* Fix duplicate Mission IDs within a single survey session, where phone state records could be assigned an earlier Mission ID than the other records in the same session.

**NEW FEATURES**
* Add a Wi-Fi Watchlist. Save networks by SSID and/or BSSID, get alerted when a watched network is seen during a survey, and browse a history of past sightings with a per-sighting details screen showing the sighting locations on a map. 
* Import a watchlist from a shared link, with a confirmation preview that merges the imported networks into your existing list without deleting or overwriting anything.
* Stream the Wi-Fi Watchlist over MQTT. Publish a match event whenever a watched network is seen, plus the full watchlist as a snapshot on connect and on every change. A Watchlist stream toggle (on by default) lives in the Watchlist settings and can be controlled by MDM.
* Add a display filter to the Wi-Fi network list, with SSID/BSSID search and band selection opened from the app bar, and an "Excluded" tag on rows and groups for networks that are on the SSID Exclusion List.
* Display the timing advance (TA) for 5G NR.
* Add a Chinese (Simplified) translation. [#75](https://github.com/christianrowlands/android-network-survey/issues/75) (Thanks [zhengyang3552](https://github.com/zhengyang3552))

**IMPROVEMENTS**
* Default NS Analytics uploads to allowed unless explicitly blocked by MDM, with a clearer upload-status message when uploads are disabled by policy.
* Migrate all dialogs to a shared Jetpack Compose dialog system for a consistent look and feel, and consolidate the three sequential startup permission rationale dialogs into a single screen.

## [1.55](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.55) - 2026-06-03

**BUG FIXES**
* Fix ann error when a survey's CSV logging is toggled off before any record is written.

**NEW FEATURES**
* Roll the Mission ID per survey session instead of once per app lifetime, and surface it to users with a copyable Dashboard card (with help dialog) and a copyable Mission ID row on the Survey Monitor Status tab.
* Add a "Last Updated" filter to the tower map to hide towers older than 6mo / 1yr / 2yr / 5yr, applied server-side and persisted across launches. [#131](https://github.com/christianrowlands/android-network-survey/issues/131)
* Pause NS Analytics uploads when a workspace quota is exceeded, with an "uploads paused" notification and an in-app banner showing quota usage and a manage-subscription link.

**IMPROVEMENTS**
* Move the OpenCelliD/BeaconDB Upload card to the bottom of the Dashboard to avoid confusion between the NS Analytics and Upload Start buttons.

## [1.54](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.54) - 2026-05-15

**BUG FIXES**
* Fix CSV survey log files appending to the same file across logging toggle cycles instead of rotating.
* Fix a nav drawer resource-not-found crash by converting the remaining drawer PNG icons to vector drawables.
* Fix the tower map empty state when filtered to the serving cell only, showing "No serving cell tower found" and correctly flagging the empty result when there is no serving cell to look up.

**NEW FEATURES**
* Add a PLMN Lookup screen for searching MCC/MNC operators by identifier, MCC, MNC, country, brand, or operator, with sortable results, a details screen, and an info bottom sheet explaining PLMN, MCC/MNC, brand vs operator, and TADIG.
* Add an OUI Lookup screen for searching MAC address manufacturers.

**IMPROVEMENTS**
* Reorganize the navigation drawer into labeled "Streaming & Cloud", "Reference Tools", and "Help & Resources" sections.
* Bump targetSdkVersion and compileSdk to 37 (Android 17) and request ACCESS_LOCAL_NETWORK for the gRPC and MQTT connection workflows.

## [1.53](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.53) - 2026-04-27

**BUG FIXES**
* Fix a resource not found crash on certain devices by replacing additional PNG icons (bug, calculator) with vector drawables.

**NEW FEATURES**
* Redesign the Wi-Fi list and Wi-Fi Network Details UIs in Jetpack Compose. The list now defaults to grouping rows by SSID.
* Resolve manufacturer names for Wi-Fi BSSIDs and Bluetooth MAC addresses via an OUI lookup service. Adds a Bluetooth manufacturer card that reconciles Company ID, UUID vendor, and OUI sources, with a new "Network lookups" settings toggle (MDM-overridable) and an offline cache.
* Add long-press copy on the SSID and BSSID in the Wi-Fi list and Network Details UIs.

**IMPROVEMENTS**
* Replace the expandable help card on the gRPC Connection screen with an app bar help icon, matching the MQTT screen pattern.
* Tighten dashboard spacing and shorten the OpenCelliD/BeaconDB upload description.
* Switch the per-ABI versionCodeOverride to the F-Droid splitabi convention. [#127](https://github.com/christianrowlands/android-network-survey/issues/127)

## [1.52](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.52) - 2026-04-08

**BUG FIXES**
* Fix the MQTT dashboard toggle not showing the connecting status immediately when toggling the connection on.

**NEW FEATURES**
* Encrypt MQTT credentials and the OpenCelliD API key with the Android Keystore using AES-256-GCM, including a one-time migration of existing plain-text credentials to encrypted storage.
* Add a PLMN field to preserve MNC leading zeros across the app, including GeoPackage tables, CSV exports, protobuf messages, and the UI display.

**IMPROVEMENTS**
* Migrate the Dashboard from Fragment to Jetpack Compose with a full MVVM architecture using per-card StateFlows for efficient recomposition.
* Use string MCC/MNC across the app to preserve leading zeros, including the tower cache database, tower map API, and cell search.

## [1.51](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.51) - 2026-03-19

**BUG FIXES**
* Fix the uploaded records count in the upload progress status UI.
* Fix the JSON creation for the Bluetooth message by updating the protobuf to have UNKNOWN as the default value instead of PUBLIC.

**NEW FEATURES**
* Add an auto-upload setting for the OpenCelliD and BeaconDB survey along with an option to only auto-upload over wifi (to prevent cellular data use). [#112](https://github.com/christianrowlands/android-network-survey/issues/112)
* Add a user setting to disable the creation of cellular neighbor cell records, so only serving cell records are included in log files, MQTT/gRPC streaming, NS Analytics upload, and UI display. [#124](https://github.com/christianrowlands/android-network-survey/issues/124)
* Generate APKs per ABI to reduce the download size for each processor architecture. [#117](https://github.com/christianrowlands/android-network-survey/issues/117)
* Display a badge with the count of operators/providers on each tower in the tower map view. [#95](https://github.com/christianrowlands/android-network-survey/issues/95)

**IMPROVEMENTS**
* Rework the Location Card UI to be more compact. Teplace the card with a status row, with click-through for full location details. Also removed from the Cellular Details UI. [#61](https://github.com/christianrowlands/android-network-survey/issues/61)
* Migrate kapt to KSP, remove Jetifier, and clean up build warnings.
* Update to v3 of the Vico charts library.
* Upgrade AGP to version 9.1.0 and Gradle to version 9.3.1.

## [1.50](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.50) - 2026-03-04

**BUG FIXES**
* Prevent an edge case crash where the user removes the Bluetooth permission while a survey is ongoing.
* Fix a location provider issue on de-googled OSs (e.g. GrapheneOS) where FUSED causes an error state when ALL is selected as the location provider. [#115](https://github.com/christianrowlands/android-network-survey/issues/115)
* Fix an edge case crash on certain devices running older Android OS versions by switching from SwitchCompat to MaterialSwitch.
* Fix an issue where the GeoPackage feature DAO cache was not cleared when turning off file logging.

**NEW FEATURES**
* Display multiple towers at the same location on the map with a tower list bottom sheet, replacing the old tower details dialog. [#95](https://github.com/christianrowlands/android-network-survey/issues/95)
* Display a number badge indicating the tower count at each map location.
* Add a color picker for selecting which color is used for each PLMN on the tower map for both tower icons and tower list colors. [#95](https://github.com/christianrowlands/android-network-survey/issues/95)
* Use different colors in the tower list and tower icons on the map for different cellular providers to help visually differentiate them.
* Display the eNB ID and Sector ID in the tower list and tower details bottom sheet for LTE towers.
* Add the missionId and recordNumber fields to the CDR and device status messages.

**IMPROVEMENTS**
* Switch the default map layer to OpenFreeMap and fall back to OpenFreeMap if MapTiler is unavailable.
* Remove the apply button from the tower map filter bottom sheet and instead apply changes on dismiss.
* Increase the map view tower icon halo width for better visibility.
* Upgrade AGP to version 9.0.1.
* Update the MQTT library to version 1.2.0.
* Reduce memory pressure by reusing the GeoPackage feature DAO instead of creating a new one for each record.
* Update the Bluetooth Company Identifiers and member UUIDs.

## [1.49](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.49) - 2026-02-18

**BUG FIXES**
* Fix a race condition crash related to layout measurement by removing the focusable aspect of certain Compose components.
* Fix the position of the C/N0 indicators on the GNSS Sky View meter bar when in landscape mode.
* Fix resource not found crashes by replacing additional PNG icons with SVG versions.

**NEW FEATURES**
* Redesign the File Logging section of the Dashboard UI to be more user friendly, and rewrite it in Jetpack Compose.
* Rework Phone State surveys to be independent of the cellular and device status surveys. Phone state surveys can now be started and stopped independently for file logging, MQTT streaming, NS Analytics uploads, and gRPC streaming. An auto-start setting ties phone state surveys to the cellular survey.
* Add a custom landscape view for the GNSS Sky View to better fit the sky view, C/N0 meter, and legend on the screen.
* Add links to the NS Analytics web app and analytics details screen from the app.

**IMPROVEMENTS**
* Improve the help dialogs for phone state, CDR, file vs MQTT, upload, and analytics to use a more modern UI approach.
* Update the phone state icon.
* Upgrade AGP to version 9.0.0.
* Upgrade the Java version to 21 in the CI/CD pipeline.
* Update all libraries and the Kotlin version to the latest.

## [1.48](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.48) - 2026-01-29

**BUG FIXES**
* Fix a bug where the second time the Details UI was opened the upload status UI was not being updated properly after an upload completed.
* Disable focus on the compose components of the GNSS Sky View and Cellular Details UI to prevent an edge case crash when switching tabs rapidly.
* Fixed an edge case crash with a fragment initialization race condition where the AndroidViewBinding inflates a layout containing FragmentContainerView and the fragment may not be attached to the FragmentManager yet.
* Fix a resource not found crash on certain devices that can't load specific png files by replacing the settings and cancel icons with SVG only files.
* Prevent a crash when centering the map on the user's location by using a local reference to the map just in case the map is no longer available when it completes.

**NEW FEATURES**
* Adds a user preference for switching between metric and imperial units in the various UIs. This does not change the unit of measurement written to the csv files, or sent over MQTT. [#118](https://github.com/christianrowlands/android-network-survey/issues/118)
* Implements a max MQTT queue size user preference that pauses the survey once the limit is reached to prevent Out of Memory app crashes when the MQTT queue grows too large. Also applies to gRPC queue and the preference can be set via MDM or in the local settings. When set to 0 the old behavior is applied where the queue can grow until a crash occurs.

**IMPROVEMENTS**
* Display a proper failure message when the auto-upload is unsuccessful.
* Added a location provider hint that is displayed if the location is not obtained in 15 seconds. This is to help address a bug where Android says a location provider is available, but it never provides a location. [#115](https://github.com/christianrowlands/android-network-survey/issues/115)
* Improve the performance of loading towers in the map view.
* Display an error dialog if the the MQTT settings is are too large to fit in the QR code.
* Adds a basic monochrome launcher icon to support the icon theme setting for Android (need to add a better one later). [#100](https://github.com/christianrowlands/android-network-survey/issues/100)

## [1.47](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.47) - 2025-12-18

**BUG FIXES**
* Handle the edge case where the Android OS does not have an activity for displaying the developer settings UI for wifi scan throttling.
* Fix an edge case resource not found crash by switching the help icon to a vector drawable instead of a png.
* Fix a bug where an old unsuccessful connection attempt was being retried and causing the current connection to be disconnected.

**NEW FEATURES**
* Set the locationAge field on the device status message.
* Allow the MQTT QoS to be set for the MQTT broker connection. [#113](https://github.com/christianrowlands/android-network-survey/issues/113)

**IMPROVEMENTS**
* Disable the upload button on the dashboard if there are not any records to upload.
* Improve the registration confirmation dialog UI.
* Request the bluetooth permission when it has not been granted yet when toggling the bluetooth survey.
* Improvements to the NS Analytics Upload process such as removing the dialog in favor of inline upload status and improve the upload now button to bypass the queued auto-upload workers and trigger an immediate upload.
* Update the Bluetooth company identifiers and member uuids.
* Rework the MQTT connection help information to be in a help dialog accessed via an app bar icon instead of the expandable section in the connection UI.
* Update AGP and all the libraries to the latest version.

## [1.46](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.46) - 2025-11-24

**IMPROVEMENTS**
* Adds deep link support for scanning a QR code to register the device.

## [1.45](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.45) - 2025-11-20

**BUG FIXES**
* Fix several edge case crashes.

**NEW FEATURES**
* Added a setting to auto start upload scanning when the phone reboots. [#112](https://github.com/christianrowlands/android-network-survey/issues/112)

**IMPROVEMENTS**
* Put the processing of phone state messages and other record processors on a single thread to ensure the record numbers are always in order.
* Several code improvements and library updates.
* Display an error dialog when the user tries to upload records that will exceed the quota limit for their workspace.

## [1.44.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.44.1) - 2025-10-28

**BUG FIXES**
* Fix a crash that occurs when staring Network Survey after upgrading from a previous version.

## [1.44](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.44) - 2025-10-27

**NEW FEATURES**
* A new custom server upload feature was added to allow for uploading survey data to a custom server using HTTP API calls. This will allow for uploading straight to NS Analytics once it is released.

**IMPROVEMENTS**
* Replace the bottom nav bar png icons with vector icons.
* Change the default log file type from "Both" to "CSV only".
* Add the deviceSerialNumber column to the CDR CSV file to help with uniqueness of records and linking to a specific device.
* Rename "Server Connection" to "gRPC Connection" and reorder the nav menu options.
* Increase the default wifi scan interval to 15 seconds, and the GNSS scan interval to 45 seconds (from 10 and 30) to improve battery life.
* Minor UI improvements (lighter card titles and icon colors).
* Pulled in the latest geopackage android library to resolve the not 16 KB aligned problem and to allow the F-Droid build to work again. [#109](https://github.com/christianrowlands/android-network-survey/issues/109)
* Remove the deprecated old connection approach for gRPC connections and update to version 2.0.0 of the NS messaging API library (along with other library version updates).

**BUG FIXES**
* Prevent an edge case crash that occurs when the GNSS failure dialog is displayed after the app is put in the background or the screen is rotated.
* Fix an edge case crash when starting Network Survey at boot.
* Handle when the operation is canceled for getting the location for a CDR message to prevent a crash.
* Fix a crash when the MapLibre tower map runs low on memory causing a stack overflow error.
* Move CellInfoCallback and TelephonyCallback outside of CellularController to prevent errors when running on older Android versions. [#104](https://github.com/christianrowlands/android-network-survey/issues/104)
* Only filter cellular scan results based on age on newer devices because of a possible bug where the timestamp is reported incorrectly. [#104](https://github.com/christianrowlands/android-network-survey/issues/104)
* Properly cleanup the maplibre location listener when exiting the map screen.
* Update the serving cell range circle and line when only displaying the serving cell tower on the map. [#103](https://github.com/christianrowlands/android-network-survey/issues/103)
* Fix an edge case crash if the user toggles BT file logging on while BT is turned off on Android, and navigates away from NS before the dialog to turn BT on is displayed.
* Fix an edge case crash on specific version of Android 10 where there is a framework bug where ParcelableException is null causing an NPE.
* Several memory leak fixes.
* Fix a race condition where the same bluetooth record number can be used twice for two different records.

## [1.43](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.43) - 2025-09-15

* Restart the GNSS survey when the user changes the scan rate for GNSS, and change the min and max gps listener rates.
* Ensure scan rate changes for WiFi and Device Status are applied even if the survey is already started.
* Include both the Service UUID and Manufacturer Specific Data Company ID in the Bluetooth record if both are present (previously only included the Service UUID if it was present and ignored the Manufacturer Specific Data Advertisement).
* Detect if a Bluetooth record is from an Apple AirTag, and if so display an indicator in the Bluetooth devices list.
* Rework the serving cell info box to have a better UI. [#99](https://github.com/christianrowlands/android-network-survey/issues/99)
* Rework of the tower map UI. [#99](https://github.com/christianrowlands/android-network-survey/issues/99)
* Parse and set the manufacturer specific advertisement on the bluetooth record. Logs it to CSV, GeoPackage, and send over MQTT and gRPC.
* Show the distance to the tower over the serving cell line. [#99](https://github.com/christianrowlands/android-network-survey/issues/99)
* Added an acknowledgement section to the app.
* Improved the font color contrast for the wifi scan throttling snackbar message.
* Added an option to tower map layers for displaying only the serving cell. [#103](https://github.com/christianrowlands/android-network-survey/issues/103)
* Fix for displaying out of range SNR values on the cellular details UI. [#107](https://github.com/christianrowlands/android-network-survey/issues/107)
* Allow for viewing the serving cell info on the survey monitor view.

## [1.42](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.42) - 2025-08-19

* Reverting to 8.11.1 of AGP because of an F-Droid build issue. [#97](https://github.com/christianrowlands/android-network-survey/issues/97)
* Include the NS App Serial Number in the settings UI for easy reference.
* Rename the upload card on the dashboard to OpenCelliD & BeaconDB Upload.
* Update the Bluetooth company identifiers and member UUIDs.
* Fix BT Survey: Don't use batch scanning as it causes BT records to never be seen and change the filtering approach to be more permissive of record updates.
* Set a minimum scan rate of 23 seconds for Bluetooth in the user settings and MDM config.
* Prevent a crash on devices with extra security software that restrict location permissions further and set the location age on the CDR csv records.
* Fix a crash when loading more than 7,500 towers to the map.
* Remove the extra map view location button by consolidating the "My Location" and "Follow Me" buttons. [#99](https://github.com/christianrowlands/android-network-survey/issues/99)
* Fix the text color of "No towers found in area" to make it more readable over the map.

## [1.41](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.41) - 2025-08-13

* Set better values for the SS_RSRP SS_RSRQ chart ranges for NR on the cellular details UI.
* Make the tower search fields in the bottom sheet a 2x2 grid instead of stacked to save vertical space.
* Make sure the tower coverage area is visible when searching for a tower on the map.
* Adds a status message and pauses cellular record processing when in airplane mode.
* Fixed duplicate alerts for the same tower.
* Indicate the currently connected wifi network in the network list.

## [1.40](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.40) - 2025-08-06

* Pause all survey operations when the battery drops below a user defined value. - [#90](https://github.com/christianrowlands/android-network-survey/issues/90)
* Adds an exclusion filter for WiFi SSIDs that allows the user to specify SSIDs to exclude from the survey data. - [#91](https://github.com/christianrowlands/android-network-survey/issues/91)
* Play a sound alert and show a notification when a new tower is seen that is not in the OpenCelliD DB. - [#81](https://github.com/christianrowlands/android-network-survey/issues/81)
* Adds a tower search feature to the tower map and survey monitor map. - [#89](https://github.com/christianrowlands/android-network-survey/issues/89)
* Improve the Survey Monitor Status UI.
* Adds an MDM field for setting the deviceName field when sending messages over MQTT.
* Improve the Tower Information Dialog for the towers on the map.
* Keep the protocol selection when locking and unlocking the tower map screen (it was previously being reverted).
* Fix a bug by triggering an update to the NR NCI calculator when the gNB ID bit length is changed.
* Restore the "my location" dot on the map after locking and unlocking the screen.
* Remove the extra dot at the end of the upload progress bar.
* Make the record count help dialog scrollable for larger font sizes.
* Prevent a crash on Android 8.1 where the device scan rate was 0 because of a race condition.
* Prevent a crash on Android 10 due to a known Android 10 bug.
* Add OpenFreeMap as a map tile option.

## [1.39](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.39) - 2025-07-14

* Prompt the user to disable Android's battery optimization for the app. This prevents the Android OS from silently pausing the survey due to battery optimizations.
* Increase the default scan rate for GNSS to 30 seconds (previously 20 seconds) and 10 seconds (previously 8 seconds) for Wi-Fi.
* Improve the interval of the bluetooth scanning to prevent multiple back to back scans.
* Fix a bug where the signal strength bars in the Cellular Details view would not go past a certain value.

## [1.38](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.38) - 2025-07-02

* Acquire a wake lock when starting a survey to make sure the Android OS does not pause the survey. This prevents gaps in the survey data.
* Created a new Survey Monitor UI that keeps the screen active and lets the user know when a survey is running.
* Adds a new map view to the Survey Monitor UI that displays the user's survey path and the BeaconDB coverage area.
* Update the Bluetooth company identifiers and member UUIDs.

## [1.37](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.37) - 2025-06-09

* **Tower Map Improvements**: Switched from OSM to MapLibre for enhanced map performance and visual quality.
* **Enhanced Cellular Details**: LTE and 5G NR bands are now displayed in their own dedicated sections with both band numbers and descriptive names - [#85](https://github.com/christianrowlands/android-network-survey/issues/85).
* **Improved Band Information**: Added frequency details for 5G NR bands (NARFCN) in the cellular details view.
* **Customizable Tower Display**: Added option to toggle cellular towers on/off on the map view.
* **Enhanced Coverage Visualization**: BeaconDB coverage areas now display as a layer on the tower map - [#83](https://github.com/christianrowlands/android-network-survey/issues/83).
* **Multi-SIM Support**: Improved the map serving cell display for devices with multiple SIM cards.
* **Map Preferences**: Added new settings including tower coverage colors, opacity controls, and screen-on option for the tower map - [#79](https://github.com/christianrowlands/android-network-survey/issues/79), [#82](https://github.com/christianrowlands/android-network-survey/issues/82).

## [1.36](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.36) - 2025-05-08

* Adds the `addressType`, `deviceClass`, `serviceUuids`, and `companyId` fields to the Bluetooth message.
* Displays the new fields in the Bluetooth UI.
* Resolve the company name and display it in the Bluetooth UI.
* Stop scrolling to the bottom when the upload status is updated.
* UI improvements for larger font settings in Wi-Fi and Bluetooth UIs.
* For database upload, ignore records that have a location accuracy worse than 100 meters.

## [1.35](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.35) - 2025-04-07

* Fixes a crash that occurs when the user quickly navigates to another app after enabling logging.
* Switches the default location provider to ALL (previously FUSED).
* Clarifies the description of the FUSED and ALL location providers in the app's Settings.
* Fixes a crash when uploading records to OpenCelliD or BeaconDB when running on Android 10 or lower.

## [1.34](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.34) - 2025-03-25

* Ensure the app notification is displayed when the MQTT connection is lost.
* Adds a UMTS Cell Id to RNC ID and Short Cell ID calculator - [#69](https://github.com/christianrowlands/android-network-survey/issues/69).
* Adds a link to the privacy policy in the app's settings.
* Fix a bug where the cellular icon would have a blue tint after opening the tower map view.
* Display the serving cell coverage area on the tower map - [#58](https://github.com/christianrowlands/android-network-survey/issues/58).
* Adds tower map settings, and link to the tower map settings from the map screen.
* Save and restore the last selected tower source for the map view.
* Reset the upload status when a new upload scan is started.
* Adds a preference for hiding the upload dialog.
* Move the upload to database section up on the dashboard.
* Save the last view position so it can be restored the next time the map is opened - [#65](https://github.com/christianrowlands/android-network-survey/issues/65).
* Fix a bug with uploading records on Android 11 and below - [#73](https://github.com/christianrowlands/android-network-survey/issues/73).

## [1.33](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.33) - 2025-02-17

* Adds support for uploading cellular and Wi-Fi survey data to OpenCelliD and BeaconDB.
* Set the locationAge field on all records streamed over MQTT as well as CSV and GeoPackage records (excluding CDR records).
* Allow the user to select the tower location data source on the Tower Map view (OpenCelliD or BTSearch).
* Update the tower map view protocol dropdown to show up under the button.
* Various improvements to the UI to aid with larger font size settings.
* Update all libraries to the latest versions.

## [1.32](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.32) - 2025-01-16

* Stale out old locations so they are not set on records and implement a location update rate ceiling - [#59](https://github.com/christianrowlands/android-network-survey/issues/59).
* Adds a PLMN filter option for the Tower Map - [#56](https://github.com/christianrowlands/android-network-survey/issues/56).
* Group the MCC and MNC together on the cellular UI - [#61](https://github.com/christianrowlands/android-network-survey/issues/61).
* Fix a bug with displaying the Sector ID for the LTE Cellular Calculator.
* Correctly display the Location Provider and log file type preferences in the settings when set via MDM.
* Trim the locations in the CSV log file to 6 decimal places - [#67](https://github.com/christianrowlands/android-network-survey/issues/67).
* Rounds the speed, altitude, and accuracy to 2 decimal places in the CSV log file for all survey types - [#68](https://github.com/christianrowlands/android-network-survey/issues/68).
* Fix clicking on the Server Connection (gRPC) notification to open the connection UI.

## [1.31](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.31) - 2024-11-04

* UI updates throughout the app.
* Sets the nonTerrestrialNetwork field in the phone state message (MQTT, gRPC, CSV, and GeoPackage)
* Prevent a crash when viewing the GNSS UI when no GPS provider is available on the device.
* Upgrade to SDK 35 (Android 15).
* Leverage new Android 15 getRejectCause API.
* Prevent really small speed values so that they are not displayed in scientific notation in JSON messages.
* Add additional permission checks for the other paths that turn on CDR logging to improve the UX and prevent crashes.
* Go to the devices location when the tower map is first opened and the info dialog is accepted.

## [1.30.2](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.30.2) - 2024-09-26

* Log the battery percentage in the device status message CSV file as an int instead of a protobuf value.

## [1.30.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.30.1) - 2024-09-25

* Don't add GMS and crashlytics to the classpath unless the google-services.json file is present.

## [1.30](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.30) - 2024-09-24

* Display the UMTS RNC ID and Short CID in the cellular details UI.
* Fixes a crash that was triggered when CDR logging was enabled while enabling a second SIM card.
* Other edge case crash fixes.

## [1.29](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.29) - 2024-08-28

* Support multiple SIM cards for the phone state messages (adds the slot field to the phone state message).
* Adds multi-SIM support for the CDR feature of NS.
* Adds the SIM slot to the CDR CSV records to track which SIM each record is associated with.
* Fixes the gRPC connection (server connection) bug on Android 14.
* Adds gRPC streaming for Phone State, Bluetooth, and GNSS records.
* Adds stream options for all the different survey types to the gRPC connection.
* Fixes a bug where the MDM override property was set differently in two places causing inconsistent override behavior.
* Hide the MQTT share QR code button if the password is set via MDM.
* Don't include the device name when sharing the MQTT connection information since it needs to be unique for each MQTT broker.
* Save the latest connection parameters before creating the MQTT connection QR Code so that the latest values are shared.
* Store the connection parameters on MDM override to facilitate MQTT start on boot and other edge case scenarios.

## [1.28](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.28) - 2024-08-08

* Adds a display overlay on the map with current serving cell information.
* Update the Tower Map if the SIM count changes.
* Adds the ability to start a survey via an Intent (see https://www.networksurvey.app/intent-api ).
* Adds the streaming options to the MQTT Settings QR Code.
* Adds a setting to turn on or off the ability for other apps to send intents to start/stop Network Survey.
* Set the NS version number in the device status message.
* Display the GSM BSIC in the cellular details view.
* Fix a couple of edge case crashes.
* Fixes a bug where the the LTE signal labels would remain visible when switching to a different technology.

## [1.27](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.27) - 2024-07-24

* Create a more detailed info window for the towers in the map view.
* Imported the Tower data from BTSearch.
* Adds the ability to share the MQTT connection settings via a QR Code.
* Adds a start auto logging setting for CDR.
* Logs the phone state messages to CSV.
* Prevent the background location permission info dialog from displaying on every app opening.
* Properly format the network registration info for the phone state message logging to CSV and GeoPackage.
* Adds speed and deviceSerialNumber columns to the GeoPackage files, and deviceSerialNumber to the CSV files. Also adds deviceModel to the GNSS CSV files.
* Fix a few edge case crash bugs.

## [1.26](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.26) - 2024-06-28

* Fixes a bug with gRPC streaming on Android 14. (Thanks [PeregrineFalcon](https://github.com/PeregrineFalcon))
* Fixes a couple edge case app crashes.
* Display the serving cell on the Tower Map.
* Draws a line to the serving cell on the Tower Map.
* Adds a follow me button to the Tower Map.
* Keeps the same zoom level when going to your location on the Tower Map.

## [1.25](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.25) - 2024-06-14

* Adds a Tower Map view that shows the location of cellular towers.

## [1.24](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.24) - 2024-05-14

* Adds a 5G NR calculator to the cellular calculators.
* Updates the cellular calculators UI to Jetpack Compose.
* Adds a link to GitHub in the Nav Drawer.
* Adds a link to report a bug in the Nav Drawer.
* Displays the Override Network Type (aka marketing network) in the cellular details view.
* Pulls in the latest GPSTest code, to include support for SouthPAN and a compass rotating sky view.
* Fixes the Avg C/N0 Slider on the Sky View screen.
* Adds support for sorting and filtering in the GNSS UIs.
* Prevents upside down screen rotation. (Thanks [joelkoen](https://github.com/joelkoen))

## [1.23](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.23) - 2024-04-15

* The Reference Signal SNR is now logged in LTE messages as well as displayed in the UI.
* Adds a fallback for getting the cellular providers name if it is not available from the system.
* Switched the donut cellular signal strength indicators to a horizontal signal bar.
* "Anchor" the view at the bottom of the cellular details screen when scrolled to the bottom to ensure that newly added neighbors are immediately visible.
* Adds "band" in title after EARFCN in LTE neighbor table. (Thanks [high3eam](https://github.com/high3eam))

## [1.22.5](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.22.5) - 2024-04-01

* Adds the band next to the EARFCN in the LTE neighbors table.
* Aligns the neighbor column headers with the data rows.

## [1.22.4](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.22.4) - 2024-03-25

* Display the app version number and Firebase app instance ID in the settings UI.

## [1.22.3](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.22.3) - 2024-03-21

* Prevent ANRs when the data connection drops.

## [1.22.2](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.22.2) - 2024-03-20

* App stability improvements.

## [1.22.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.22.1) - 2024-03-14

* Fixed several bugs causing app crashes.

## [1.22](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.22) - 2024-03-13

* Adds a 6 GHz Wi-Fi spectrum view.
* Fixed an edge case app crash issue when stopping surveys.

## [1.21](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.21) - 2024-03-05

* Fixed several memory leaks that would eventually result in app crashes.
* Fixed some edge case app crashes.
* Adds support for 6 GHz Wi-Fi channels.
* Adds CQI to the LTE cellular details view.
* Other minor UI improvements.

## [1.20](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.20) - 2024-02-16

* Add SSID labels to the Wi-Fi spectrum charts.
* Several other improvements to the Wi-Fi spectrum charts.
* Adds an MDM setting to stop showing the Wi-Fi throttling warning snackbar message.
* Adds a location provider preference to select which location provider to use.
* An ALL option was added to allow for adding locations from each location provider to the device
  status message which can be used for comparison and location analysis.
* Adds a CSV log file for the device status message.
* CSV header comments are now added to the top of the CSV log files.

## [1.19](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.19) - 2024-01-31

* Adds a chart showing the Wi-Fi Spectrum usage.
* The Wi-Fi Standard and Wi-Fi Bandwidth are now logged in Wi-Fi log files and MQTT messages, and
  displayed in the UI.
* The Wi-Fi center channel is displayed next to the channel.
* Reduced the range of the cellular signal chart to bring it more inline with reality.
* Removed the extra comma after the NR band(s).

## [1.18](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.18) - 2024-01-22

* Adds a chart showing the last two minutes of cellular signal strength to the cellular details
  view.
* Adds an information dialog about the cellular terms definitions.

## [1.17](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.17) - 2024-01-12

* Adds Details views for Wi-Fi and Bluetooth that display a chart of the signal strength over time.
* Adds support for setting a custom MQTT topic prefix.
* Fixes a bug where Bluetooth permissions were not being requested correctly.
* Other various bug fixes and improvements.

## [1.16](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.16) - 2023-12-20

* Adds support for Dual/Multi-SIM devices both in the UI as well as the messages (MQTT and file
  logging).
* Autogenerate the MQTT client ID when the app is opened for the first time to improve the UX.
* Move the cellular calculators to the nav drawer menu instead of a tab in cellular.
* Sets the CQI and Signal Strength (RSSI) on the LteRecord on supported devices.
* Makes several UI fields selectable so they can be copied.
* Fixes a bug with auto-starting Bluetooth logging on app opening.

## [1.15](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.15) - 2023-11-22

* Battery improvement for GNSS when the scan rate is set to 30 seconds or more.
* Adds support for logging AGC with GNSS on certain devices.
* Logs if a Wi-Fi access point supports Passpoint.
* Displays and logs WPA2/WPA3 and WPA3 for APs in the Wi-Fi Survey.
* Displays the LTE band number on the cellular details screen.
* Changes the default scan rates to 8 seconds for Wi-Fi, and 20 seconds for GNSS (from 5 and 10
  respectively).
* Adds the altitude to the location view.

## [1.14](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.14) - 2023-11-09

* Added support for logging Cellular, Wi-Fi, Bluetooth, and GNSS surveys to CSV files.
* Added a help dialog explaining the difference between file logging and MQTT.
* Added links to the user manual and NS Messaging API docs in the Nav menu.
* Allow for horizontal display (landscape mode).
* Updates for Android 14.

## [1.13](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.13) - 2023-06-29

* The speed (in meters per second) is now included in all messages.
* Fixed a bug where the MQTT toggle switch was not displaying when MDM override was enabled.

## [1.12.2](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.12.2) - 2023-03-28

* Changed the color of the MQTT protocol stream status light on the Dashboard.
* Fixed a bug where the MDM configured stream settings were not being reflected on the Dashboard.

## [1.12.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.12.1) - 2023-03-13

* Fixed a Bluetooth Permissions bug.
* Exclude the Google Protobuf Audit library.

## [1.12](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.12) - 2023-03-04

* Remove the use of the GMS library for CDR location.
* Fixed some bugs that resulted in the app crashing.

## [1.11](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.11) - 2023-02-27

* Adds support for logging Call Detail Record (CDR) events to a CSV file.
* Caches the Bluetooth UI results so the results are still visible when switching between tabs.
* Adds a connection toggle switch and direct link to the MQTT Connection Fragment from the
  Dashboard.
* Changes the default location provider to Fused, which should improve battery life.

## [1.10.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.10.0) - 2023-01-24

* Sets the mdmOverride field on the device status message instead of using firebase analytics
  events.
* Adds a Dashboard UI for toggling logging to files as well as viewing the MQTT connection status.

## [1.9.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.9.0) - 2022-10-28

* Library updates, permission updates, logging updates, and other minor changes.

## [1.8.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.8.0) - 2022-09-26

* Empty GNSS survey messages are now sent when GNSS survey is turned on and no GNSS satellites are
  observed. This is to indicate that the device is surveying as expected, but no satellites are
  visible.

## [1.7.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.7.0) - 2022-07-03

* Scan QR Code for configuring the MQTT Broker connection information. (
  Thanks [dtufekcic](https://github.com/dtufekcic)!)

## [1.6.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.6.0) - 2021-12-18

* The WiFi UI is saved when swapping between fragments, so you don’t have to wait for the next scan
  to see something.
* The Cellular UI got a total overhaul, and now displays all protocols (except CDMA) and all
  neighbor cells as well.
* Updated to compile against Android 12.

## [1.5.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.5.1) - 2021-11-30

* Fixed a bug where incorrect 5G NR values were being reported.
* Updated to NS Messaging API version 0.8.0.
* Added support for setting the EcNo field for UMTS.

## [1.5.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.5.0) - 2021-09-13

* Added support for 5G New Radio (NR) survey.
* Added the AGC to the GNSS Status Display.
* Added a location accuracy field to each message (both GeoPackage and MQTT).

## [1.4.3](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.4.3) - 2021-08-08

* Fixed several bugs that could cause the app to crash in various scenarios.

## [1.4.2](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.4.2) - 2021-07-08

* Fixed a bug where the survey record queue would fill up and reject new records.
* Added the missionId and recordNumber fields to the Phone State message.
* Added support for logging the Phone State message to GeoPackage.

## [1.4.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.4.1) - 2021-06-28

* Fixed a bug where the MQTT connection would not reconnect when the phone dropped its data
  connection.

## [1.4.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.4.0) - 2021-06-11

* Fixed a bug where permissions were not being requested on Android 11.
* Added support for streaming Phone State messages out over MQTT. The Phone State message is used to
  report some basic information about the phone such as the current serving cell, current
  technology, if a SIM is present, etc.
* Updated the default Bluetooth scan interval to 30 seconds because I kept seeing messages that the
  previous scan was not done when using 15 and 20 seconds as defaults.
* Updated the default GNSS scan interval to 10 seconds since 8 seconds seemed too often.
* Added a Device Model field to the GNSS and Device Status messages.
* Added the Mission ID field to the GeoPackage files.
* Improved the UX for error scenarios when connecting to an MQTT broker (e.g. notify the user of
  invalid username/password).
* Fixed the GNSS Raw Measurements information link.

## [1.3.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.3.0) - 2021-05-18

* Updated the permissions dialog with some extra details on why the background location is needed.

## [1.2.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.2.0) - 2021-04-29

* Improved the MQTT Connection stability and fixed a few bugs that resulted in the app crashing.

## [1.1.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.1.0) - 2021-04-15

* Added support for streaming a Device Status message over an MQTT connection.
* Fixed the logging buttons on the toolbar so that they are always visible.

## [1.0.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v1.0.0) - 2021-01-20

* Added Bluetooth survey support for streaming over an MQTT connection and logging to a GeoPackage
  file.
* Added a Bluetooth survey UI for viewing all Bluetooth devices within range.
* Updated the Wi-Fi Status UI to reflect when Wi-Fi is disabled.

## [0.4.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.4.0) - 2020-11-17

* Fixed a bug that caused an app crash if it was opened, hidden, and reopened in short sequence.
* Fixed a bug where the app would crash if trying to enable GNSS logging with location services
  turned off.
* Added a survey log file rollover option to prevent the log file from growing too large.
* Added support for streaming GNSS records over an MQTT connection.
* Added a dialog to warn the user if the device does not support raw GNSS measurements.
* Added several more app restrictions to allow more control when the device is under MDM.

## [0.3.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.3.0) - 2020-10-01

* Reduced the GNSS GeoPackage file size by around 100x.
* Changed the GNSS GeoPackage table format.
* Added scan rate interval user preferences for Cellular, Wi-Fi, and GNSS.

## [0.2.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.2.1) - 2020-08-21

* Updated the device time field to use RFC 3339 instead of Unix Epoch time.
* Fixed a bug where the connection would not stop if the server shutdown before the client.

## [0.2.0](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.2.0) - 2020-08-11

* Updated to use the new Network Survey Messaging connection library.
* Updated to use the new Network Survey Messaging format for the MQTT messages.

## [0.1.5](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.1.5) - 2020-07-02

* Fixed a bug where the MDM override setting was not being saved.

## [0.1.4](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.1.4) - 2020-07-02

* Changed the TLS Enabled MDM setting from a string to a boolean.

## [0.1.3](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.1.3) - 2020-06-30

* Added the user entered device name to the outgoing MQTT messages.
* When the MQTT connection is configured via MDM, the configuration is now displayed in the MQTT
  connection UI.
* Added a user preference to auto start the MQTT connection when the phone is booted.

## [0.1.2](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.1.2) - 2020-06-03

* Wi-Fi beacon survey records can now be logged to a GeoPackage file, and sent over the connections.
* Added support for displaying the list of visible Wi-Fi networks.
* Improved the stability of the MQTT connection.
* The app's version number is now displayed in the navigation drawer.

## [0.1.1](https://github.com/christianrowlands/android-network-survey/releases/tag/v0.1.1) - 2020-05-08

* Added support for connecting to an MQTT broker and streaming cellular survey records.
* Added support for allowing the MQTT broker connection information to be set via MDM.
* Fixed a bug that caused the calculator text field to be covered on screens with low resolution and
  large font.

## [0.1.0](https://github.com/christianrowlands/android-network-survey/releases/tag/release-0.1.0) - 2020-03-24

* Added support for logging GNSS information to a GeoPackage file.

## [0.0.9](https://github.com/christianrowlands/android-network-survey/releases/tag/release-0.0.9) - 2020-01-10

* Moved the file logging and connection logic to foreground services to prevent the Android System
  from stopping them.
* The connection now supports sending GSM, CDMA, UMTS, and LTE survey records.
* Added a navigation drawer and put the calculators and connection in it.
* Added a settings UI.
* Other general improvements.
