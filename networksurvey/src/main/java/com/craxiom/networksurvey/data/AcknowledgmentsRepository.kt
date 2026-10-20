package com.craxiom.networksurvey.data

import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.LibraryAcknowledgment
import com.craxiom.networksurvey.model.LibraryCategory

/**
 * Repository providing acknowledgment data for open source libraries used in Network Survey. Names,
 * authors and licenses are proper names and stay as written; descriptions are translatable.
 */
class AcknowledgmentsRepository {

    /**
     * Returns the complete list of library acknowledgments grouped by category.
     */
    fun getLibraryAcknowledgments(): List<LibraryAcknowledgment> {
        return buildList {
            // Special Acknowledgments
            add(
                LibraryAcknowledgment(
                    name = "GPSTest",
                    author = "Sean J. Barbeau",
                    description = R.string.ack_desc_gpstest,
                    sourceUrl = "https://github.com/barbeau/gpstest",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.SPECIAL_ACKNOWLEDGMENTS
                )
            )

            // Core Libraries
            add(
                LibraryAcknowledgment(
                    name = "Dagger/Hilt",
                    author = "Google",
                    description = R.string.ack_desc_dagger_hilt,
                    sourceUrl = "https://github.com/google/dagger",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.CORE_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Room",
                    author = "Google",
                    description = R.string.ack_desc_room,
                    sourceUrl = "https://developer.android.com/jetpack/androidx/releases/room",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.CORE_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Retrofit",
                    author = "Square",
                    description = R.string.ack_desc_retrofit,
                    sourceUrl = "https://github.com/square/retrofit",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.CORE_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "gRPC",
                    author = "Google",
                    description = R.string.ack_desc_grpc,
                    sourceUrl = "https://github.com/grpc/grpc-java",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.CORE_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "MQTT Library",
                    author = "Craxiom",
                    description = R.string.ack_desc_mqtt_library,
                    sourceUrl = "https://github.com/craxiom/android-mqtt",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.CORE_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Network Survey Messaging",
                    author = "Craxiom",
                    description = R.string.ack_desc_network_survey_messaging,
                    sourceUrl = "https://github.com/christianrowlands/network-survey-messaging",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.CORE_LIBRARIES
                )
            )

            // UI Libraries
            add(
                LibraryAcknowledgment(
                    name = "Jetpack Compose",
                    author = "Google",
                    description = R.string.ack_desc_jetpack_compose,
                    sourceUrl = "https://developer.android.com/jetpack/compose",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UI_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Material Design Components",
                    author = "Google",
                    description = R.string.ack_desc_material_design_components,
                    sourceUrl = "https://github.com/material-components/material-components-android",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UI_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "MapLibre GL",
                    author = "MapLibre",
                    description = R.string.ack_desc_maplibre_gl,
                    sourceUrl = "https://github.com/maplibre/maplibre-gl-native",
                    licenseName = "BSD 2-Clause License",
                    licenseUrl = "https://opensource.org/licenses/BSD-2-Clause",
                    category = LibraryCategory.UI_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Vico Charts",
                    author = "Patryk Goworowski and Patrick Michalik",
                    description = R.string.ack_desc_vico_charts,
                    sourceUrl = "https://github.com/patrykandpatrick/vico",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UI_LIBRARIES
                )
            )

            // Utility Libraries
            add(
                LibraryAcknowledgment(
                    name = "Timber",
                    author = "Jake Wharton",
                    description = R.string.ack_desc_timber,
                    sourceUrl = "https://github.com/JakeWharton/timber",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Apache Commons CSV",
                    author = "Apache Software Foundation",
                    description = R.string.ack_desc_apache_commons_csv,
                    sourceUrl = "https://github.com/apache/commons-csv",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "SnakeYAML",
                    author = "SnakeYAML Contributors",
                    description = R.string.ack_desc_snakeyaml,
                    sourceUrl = "https://bitbucket.org/snakeyaml/snakeyaml",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "ZXing",
                    author = "ZXing Authors",
                    description = R.string.ack_desc_zxing,
                    sourceUrl = "https://github.com/zxing/zxing",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Code Scanner",
                    author = "Yuriy Budiyev",
                    description = R.string.ack_desc_code_scanner,
                    sourceUrl = "https://github.com/yuriy-budiyev/code-scanner",
                    licenseName = "MIT License",
                    licenseUrl = "https://opensource.org/licenses/MIT",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Protobuf",
                    author = "Google",
                    description = R.string.ack_desc_protobuf,
                    sourceUrl = "https://github.com/protocolbuffers/protobuf",
                    licenseName = "BSD 3-Clause License",
                    licenseUrl = "https://opensource.org/licenses/BSD-3-Clause",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "Gson",
                    author = "Google",
                    description = R.string.ack_desc_gson,
                    sourceUrl = "https://github.com/google/gson",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
            add(
                LibraryAcknowledgment(
                    name = "WorkManager",
                    author = "Google",
                    description = R.string.ack_desc_workmanager,
                    sourceUrl = "https://developer.android.com/jetpack/androidx/releases/work",
                    licenseName = "Apache License 2.0",
                    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                    category = LibraryCategory.UTILITY_LIBRARIES
                )
            )
        }
    }
}