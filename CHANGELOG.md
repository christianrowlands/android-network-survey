# Changelog

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
