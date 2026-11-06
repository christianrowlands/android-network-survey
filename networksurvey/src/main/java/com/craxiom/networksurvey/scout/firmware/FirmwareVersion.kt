package com.craxiom.networksurvey.scout.firmware

/**
 * A semantic version such as "1.2.0" or "1.3.0-beta.1", ordered by SemVer 2.0 precedence. A
 * leading "v" and build metadata ("+...") are accepted and ignored for ordering. A port of the iOS
 * app's `FirmwareVersion`, so both apps order releases the same way.
 */
class FirmwareVersion private constructor(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val prerelease: List<String>,
) : Comparable<FirmwareVersion> {

    override fun compareTo(other: FirmwareVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        // A release ranks above its pre-releases
        when {
            prerelease.isEmpty() && other.prerelease.isEmpty() -> return 0
            prerelease.isEmpty() -> return 1
            other.prerelease.isEmpty() -> return -1
        }
        for ((a, b) in prerelease.zip(other.prerelease)) {
            if (a == b) continue
            val aNumber = isNumber(a)
            val bNumber = isNumber(b)
            return when {
                // Numeric, compared by length first so long numbers never overflow
                aNumber && bNumber -> compareValuesBy(a, b, { it.length }, { it })
                aNumber -> -1
                bNumber -> 1
                else -> a.compareTo(b)
            }
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    override fun equals(other: Any?): Boolean = other is FirmwareVersion && compareTo(other) == 0

    override fun hashCode(): Int = listOf(major, minor, patch, prerelease).hashCode()

    override fun toString(): String =
        "$major.$minor.$patch" + if (prerelease.isEmpty()) "" else "-" + prerelease.joinToString(".")

    companion object {
        private const val CORE_PARTS = 3

        /** @return The version in [text], or null when it is not a semantic version. */
        @JvmStatic
        fun parse(text: String?): FirmwareVersion? {
            var core = text?.trim() ?: return null
            if (core.startsWith("v") || core.startsWith("V")) core = core.substring(1)
            core = core.substringBefore('+')
            var pre = emptyList<String>()
            val dash = core.indexOf('-')
            if (dash >= 0) {
                pre = core.substring(dash + 1).split(".")
                if (!pre.all(::isIdentifier)) return null
                core = core.substring(0, dash)
            }
            val parts = core.split(".")
            if (parts.size != CORE_PARTS || !parts.all(::isNumber)) return null
            val numbers = parts.map { it.toLongOrNull() ?: return null }
            return FirmwareVersion(numbers[0], numbers[1], numbers[2], pre)
        }

        /** Digits only, no leading zero (a SemVer numeric identifier). */
        private fun isNumber(text: String): Boolean =
            text.isNotEmpty() && text.all { it in '0'..'9' } && (text == "0" || !text.startsWith("0"))

        private fun isIdentifier(text: String): Boolean {
            if (text.isEmpty() || !text.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '-' }) return false
            return !text.all { it in '0'..'9' } || isNumber(text)
        }

        /** @return The higher of two version strings, ignoring any that is not a semantic version. */
        @JvmStatic
        fun higher(a: String?, b: String?): String? {
            val parsedA = parse(a) ?: return b?.takeIf { parse(it) != null }
            val parsedB = parse(b) ?: return a
            return if (parsedB > parsedA) b else a
        }
    }
}

/** How an offered firmware relates to what the board runs. */
enum class FirmwareComparison {
    NEWER,
    SAME,
    OLDER,

    /** One of the versions is not a semantic version. */
    UNKNOWN;

    companion object {
        /** @return How [offered] compares with [installed]. */
        @JvmStatic
        fun of(offered: String?, installed: String?): FirmwareComparison {
            val offeredVersion = FirmwareVersion.parse(offered)
            val installedVersion = FirmwareVersion.parse(installed)
            if (offeredVersion == null || installedVersion == null) return if (offered == installed) SAME else UNKNOWN
            return when {
                offeredVersion > installedVersion -> NEWER
                offeredVersion == installedVersion -> SAME
                else -> OLDER
            }
        }
    }
}
