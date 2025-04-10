package com.craxiom.networksurvey.data

import android.content.Context
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * Handles mapping the UUIDs to their human-readable names. The file is loaded from the assets
 * folder, which is pulled from here: https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/company_identifiers/company_identifiers.yaml
 *
 * run `.gradlew downloadBluetoothCompanyIdentifiers to update the file.
 */
class BluetoothCompanyResolver(context: Context) {

    private val companyMap: Map<Int, String>

    init {
        val options = LoaderOptions()
        val yaml = Yaml(options)
        val inputStream = context.assets.open("company_identifiers.yaml")
        val parsedRoot = yaml.load<Map<String, List<Map<String, Any>>>>(inputStream)

        val list = parsedRoot["company_identifiers"] ?: emptyList()

        companyMap = list.mapNotNull { entry ->
            val intId = entry["value"] as? Int
            val name = entry["name"] as? String
            if (intId != null && name != null) intId to name else null
        }.toMap()
    }

    fun getCompanyName(companyId: Int): String? = companyMap[companyId]

    fun getCompanyName(companyIdString: String) =
        companyIdString.toIntOrNull(16)?.let { getCompanyName(it) }
}
