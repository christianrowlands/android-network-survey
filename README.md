# Network Survey Android App

[![Build Status](https://travis-ci.com/christianrowlands/android-network-survey.svg?branch=develop)](https://travis-ci.com/github/christianrowlands/android-network-survey)
[![License](https://img.shields.io/badge/license-Apache%202-green.svg?style=flat)](https://github.com/christianrowlands/android-network-survey/blob/develop/LICENSE)

The Network Survey Android App provides a basic survey capability for Cellular networks, Wi-Fi
networks, Bluetooth Devices, and GNSS constellations.  

For cellular data, in its current state it can be used to examine the network details of the current 
serving cell, and log GSM, CDMA, UMTS, LTE, and NR records to a GeoPackage file. Wi-Fi survey
records can also be logged to a GeoPackage file, and the current list of Wi-Fi networks in range is
displayed in the UI. The App also supports connecting to a remote gRPC server and live streaming the
cellular and Wi-Fi records. For GNSS data, it can display the latest information about the satellite
vehicles and also log the information to a GeoPackage file. The Bluetooth support allows for scanning
and displaying a list of the nearby Bluetooth devices.

<img src="screenshots/cellular_details.png" alt="Cellular Details" width="200"/>
<img src="screenshots/wi-fi_details.png" alt="Wi-Fi Details" width="200"/>
<img src="screenshots/gnss_details.png" alt="GNSS Details" width="200"/>


## Getting Started

To build and install the project follow the steps below:

    1) Clone the repo.
    2) Open Android Studio, and then open the root directory of the cloned repo.
    3) Connect an Android Phone (make sure debugging is enabled on the device).
    4) Install and run the app by clicking the "Play" button in Android Studio.

If you want to build using the command line, the apk can be built and installed using the following
commands. Make sure your phone is connected to your computer before running the install command.

> NOTE: If building on Windows, replace `./gradlew` with `gradlew`

```shell
./gradlew assembleDebug
./gradlew installDebug
```

### Run Tests

> NOTE: This requires a connected device (physical device or Android Emulator)

```
./gradlew connectedAndroidTest
```

### Prerequisites

Install Android Studio to work on this code.

## Built With

* [GeoPackage Android](https://github.com/ngageoint/geopackage-android) - The logging file standard
  and library

## Available At

[The Google Play Listing for this app](https://play.google.com/store/apps/details?id=com.craxiom.networksurvey)

[IzzyOnDroid F-Droid](https://apt.izzysoft.de/fdroid/index/apk/com.craxiom.networksurvey)

## gRPC Survey Record Streaming

The Network Survey app supports streaming GSM, CDMA, UMTS, LTE, and 802.11 survey records to a gRPC
server. More specifically,
the [Network Survey Messaging](https://github.com/christianrowlands/network-survey-messaging)
library can be used to stand up a gRPC server. From there it is up to the implementation to handle
the incoming survey messages.

## MQTT Broker Survey Record Streaming

Currently, GSM, CDMA, UMTS, LTE, NR, 802.11, Bluetooth and GNSS survey records are sent to a
connected MQTT broker. They are published on the following MQTT Topics:

* gsm_message
* cdma_message
* umts_message
* lte_message
* nr_message
* 80211_beacon_message
* bluetooth_message
* gnss_message

There is also a DeviceStatus and a PhoneState message that is published on
the `device_status_message` topic.

The MQTT Broker connection supports both plain text and TLS/SSL connections.

The survey messages are sent in JSON format following the protobuf definitions from
the [Network Survey Messaging](https://github.com/christianrowlands/network-survey-messaging)
library. [The API documentation is published to a web page here](https://messaging.networksurvey.app/).

QR Code for MQTT Broker connection setting needs to provide a JSON string with the following fields.
```json
{"mqtt_username":"auser","mqtt_password":"apassword","mqtt_host":"cloud.azure.com","mqtt_port":8883,"mqtt_client":"aclient","mqtt_tls":true}
```

## Changelog

See the change log for details about each release: [`CHANGELOG.md`](CHANGELOG.md)

## Contact

* **Christian Rowlands** - [Craxiom](https://github.com/christianrowlands)
