package com.craxiom.networksurvey.data

import android.content.Context
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * Handles mapping the UUIDs to their human-readable names. The file is loaded from the assets
 * folder, which is pulled from here: https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/member_uuids.yaml
 *
 * run `.gradlew downloadBluetoothMemberUuid to update the file.
 */
class BluetoothUuidResolver(context: Context) {

    private val uuidMap: Map<Int, String>

    init {
        val options = LoaderOptions()
        val yaml = Yaml(options)
        val inputStream = context.assets.open("member_uuids.yaml")
        val parsedRoot = yaml.load<Map<String, List<Map<String, Any>>>>(inputStream)

        val list = parsedRoot["uuids"] ?: emptyList()

        uuidMap = list.mapNotNull { entry ->
            val uuid = entry["uuid"] as? Int
            val name = entry["name"] as? String
            if (uuid != null && name != null) uuid to name else null
        }.toMap()
    }

    fun getNameForUuid(uuid: Int): String? = uuidMap[uuid]

    fun getNameForUuid(uuidString: String) = uuidString.toIntOrNull(16)?.let { getNameForUuid(it) }
}
