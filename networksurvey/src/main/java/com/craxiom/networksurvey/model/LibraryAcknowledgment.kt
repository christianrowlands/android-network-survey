package com.craxiom.networksurvey.model

/**
 * Represents an open source library acknowledgment.
 *
 * @property name The name of the library
 * @property author The author or organization that created the library
 * @property description A brief description of what the library does
 * @property sourceUrl The URL to the library's source code (typically GitHub)
 * @property licenseName The name of the license (e.g., "Apache License 2.0")
 * @property licenseUrl The URL to the full license text
 * @property category The category this library belongs to for grouping in the UI
 */
data class LibraryAcknowledgment(
    val name: String,
    val author: String,
    val description: String,
    val sourceUrl: String,
    val licenseName: String,
    val licenseUrl: String,
    val category: LibraryCategory
)

/**
 * Categories for grouping libraries in the acknowledgments screen.
 */
enum class LibraryCategory {
    SPECIAL_ACKNOWLEDGMENTS,
    CORE_LIBRARIES,
    UI_LIBRARIES,
    UTILITY_LIBRARIES
}