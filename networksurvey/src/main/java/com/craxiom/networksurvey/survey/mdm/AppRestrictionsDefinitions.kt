package com.craxiom.networksurvey.survey.mdm

import android.content.RestrictionEntry
import android.content.res.Resources
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber

/** One managed configuration key as `res/xml/app_restrictions.xml` declares it. */
data class RestrictionDefinition(
    val key: String,
    @StringRes val titleRes: Int,
    val type: Int,
)

/**
 * Reads the app's managed configuration schema, the same file EMM consoles read, so the
 * Organization settings page lists each key under the title an administrator sees. Parsed from the
 * resource directly (not through RestrictionsManager) so it also works in Robolectric tests.
 */
object AppRestrictionsDefinitions {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val RESTRICTION_TAG = "restriction"

    /** @return Every restriction in declaration order. */
    @JvmStatic
    fun read(resources: Resources): List<RestrictionDefinition> {
        val definitions = mutableListOf<RestrictionDefinition>()
        try {
            resources.getXml(R.xml.app_restrictions).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG || parser.name != RESTRICTION_TAG) continue
                    val key = parser.getAttributeValue(ANDROID_NS, "key") ?: continue
                    definitions += RestrictionDefinition(
                        key = key,
                        titleRes = parser.getAttributeResourceValue(ANDROID_NS, "title", 0),
                        type = parser.getAttributeIntValue(ANDROID_NS, "restrictionType", RestrictionEntry.TYPE_NULL),
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not read the managed configuration schema")
        }
        return definitions
    }
}
