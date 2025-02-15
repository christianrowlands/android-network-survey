# Network Survey Android App Privacy Policy

Christian Rowlands built the Network Survey app as an Open Source app. This app is provided by Christian Rowlands at no cost and is intended for use as is.

This page informs users about my policies regarding the collection, use, and disclosure of information when using the app. By using this app, you agree to the collection and use of information as described in this policy. I do not use or share your information with anyone except as described here.

## Information Collection and Use

The Network Survey app does not collect personally identifiable information. The app may request access to certain device permissions to provide its core functionality. Any collected data remains on the device unless explicitly shared by the user.

The app uses third-party services that may collect information for analytics and crash reporting:

- **[Google Play Services](https://www.google.com/policies/privacy/)**
- **[Google Analytics for Firebase](https://www.google.com/analytics/terms/)**
- **[Firebase Crashlytics](https://firebase.google.com/support/privacy/)**

If the app was installed via the Google Play Store, anonymous crash and analytics data may be stored in Google Firebase to improve app stability. This data is not shared with or sold to third-party entities.

## Location Permissions

### Background Location Permission
The app requests background location access to support the automatic start of Network Survey upon device boot. If this permission is denied, the auto-start feature will not function, but other features will remain available.

### Foreground Location Permission
The app requires location access to collect survey records for cellular, Wi-Fi, Bluetooth, and GNSS. Each record is associated with the device’s location for analysis.

Location data is stored in:
- **GeoPackage log files** (if logging is enabled)
- **MQTT or gRPC streams** (if a remote server is configured)

The app does not provide a default server. Users must configure their own server if they choose to send data externally. Location data is not shared with any third parties beyond those explicitly configured by the user.

## Tower Map View Location Data

If the **Tower Map View** feature is used, the server request will include the user's current map location. This data is:
- Stored in server logs for **90 days** before deletion.
- **Anonymous** and not tied to any user.
- **Optional**, with a privacy notice displayed upon first use.

## File-Based Survey Data

The app allows users to log survey data into GeoPackage and/or CSV files. These files remain on the device unless manually transferred or shared by the user.

## Data Upload Feature

The **Upload Feature** allows users to voluntarily contribute cellular and Wi-Fi survey data to third-party databases. This feature is **optional**, and users can choose if they want to use it.

### What Data Is Uploaded?
When using this feature, the following data may be submitted:

- **Device Location:** Latitude, longitude, altitude, accuracy, speed
- **Cellular Data:** Mobile country code (MCC), mobile network code (MNC), cell ID, signal strength, timing advance, network type (e.g., LTE, 5G)
- **Wi-Fi Data:** Wi-Fi access point BSSID (MAC address), signal strength, channel, frequency, encryption type
- **Timestamp:** The time the data was collected

### Where Is the Data Sent?
If used, data is uploaded to one or both of the following services:

- **[OpenCelliD](https://opencellid.org/)** – A global open-source cell tower database
- **[BeaconDB](https://beacondb.net/)** – A crowdsourced geolocation database

Users can choose which services to upload to within the app’s settings.

### How Is This Data Used?
Uploaded data helps improve cellular and Wi-Fi coverage mapping, supports research, and enhances public geolocation services.

### Anonymization & Privacy
The app does not upload personally identifiable information. No user account, phone number, IMEI, or other uniquely identifying data is included in uploads. However, location and network data may still be considered sensitive.

Users should review the privacy policies of the respective third-party services:

- **[OpenCelliD Privacy Policy](https://community.opencellid.org/privacy)**
- **[BeaconDB Privacy Policy](https://beacondb.net/privacy/)**

For more details, visit the **[Network Survey User Manual](https://networksurvey.app/manual#data-upload)**.

## Changes to This Privacy Policy

I may update this Privacy Policy from time to time. You are encouraged to review this page periodically for any changes. Updates will be posted here and take effect immediately.

## Contact

If you have any questions about this Privacy Policy, feel free to contact me at **craxiomdev@gmail.com**.

This privacy policy was originally created using **[privacypolicytemplate.net](https://privacypolicytemplate.net)** and modified using **[App Privacy Policy Generator](https://app-privacy-policy-generator.firebaseapp.com/)**.
