package com.craxiom.networksurvey

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the resource folders and sources at the finish line the redesign cleanup reached: icons
 * are vectors (only the launcher mipmaps and the GNSS constellation flags stay raster), no values
 * resource (strings, arrays, plurals) in any locale or source set and no Kotlin or Java source in
 * any source set (tests included) carries an em dash, and no folder still carries a `-v26`
 * qualifier, which the minimum SDK makes redundant.
 */
class ResourceHygieneTest {

    @Test
    fun drawables_areVectors_exceptTheConstellationFlags() {
        val rasters = resourceDirs()
            .filter { it.name.startsWith(DRAWABLE_DIR_PREFIX) }
            .flatMap { it.listFiles().orEmpty().asList() }
            .filter { it.extension.lowercase() in RASTER_EXTENSIONS }
        val (flags, others) = rasters.partition { it.name.startsWith(FLAG_PREFIX) }
        val offenders = others.map { it.relativeTo(sourceRoot()).path }

        assertTrue("The constellation flags were not found", flags.isNotEmpty())
        assertTrue("Raster drawables found; use a vector instead: $offenders", offenders.isEmpty())
    }

    @Test
    fun valuesResources_haveNoEmDash() {
        val files = resourceDirs()
            .filter { it.name.startsWith(VALUES_DIR_PREFIX) }
            .flatMap { it.listFiles().orEmpty().asList() }
            .filter { it.extension == XML_EXTENSION }
        assertTrue("No values files found", files.any { it.name == STRINGS_FILE })

        val offenders = files
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (EM_DASH in line) "${file.relativeTo(sourceRoot()).path}:${index + 1}" else null
                }
            }

        assertTrue("Em dashes found in values resources: $offenders", offenders.isEmpty())
    }

    @Test
    fun sourceFiles_haveNoEmDash() {
        val files = sourceRoot().listFiles().orEmpty()
            .flatMap { sourceSet -> SOURCE_DIRS.map { File(sourceSet, it) } }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXTENSIONS }.toList() }
        assertTrue("This test's own source was not found", files.any { it.name == THIS_FILE })
        assertTrue("Only ${files.size} source files found", files.size > MIN_SOURCE_FILES)

        val offenders = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (EM_DASH in line) "${file.relativeTo(sourceRoot()).path}:${index + 1}" else null
            }
        }

        assertTrue("Em dashes found in sources: $offenders", offenders.isEmpty())
    }

    @Test
    fun resourceFolders_haveNoRedundantV26Qualifier() {
        val folders = resourceDirs()
            .filter { it.name.endsWith(V26_QUALIFIER) }
            .map { it.relativeTo(sourceRoot()).path }

        assertTrue("Folders with a redundant -v26 qualifier: $folders", folders.isEmpty())
    }

    /** Every folder directly under each source set's `res` directory (main, regular, cdr, ...). */
    private fun resourceDirs(): List<File> {
        val dirs = sourceRoot().listFiles().orEmpty()
            .map { File(it, RES_DIR) }
            .filter { it.isDirectory }
            .flatMap { it.listFiles().orEmpty().filter(File::isDirectory) }
        assertTrue("No resource folders found under ${sourceRoot().absolutePath}", dirs.isNotEmpty())
        return dirs
    }

    /** The module's `src` folder: the working directory is the module under Gradle, the repository root in some IDE runs. */
    private fun sourceRoot(): File =
        SOURCE_ROOT_CANDIDATES.map(::File).firstOrNull { File(it, MAIN_RES_PATH).isDirectory }
            ?: error("Could not find the module's src folder from ${File(".").absolutePath}")

    private companion object {
        val SOURCE_ROOT_CANDIDATES = listOf("src", "networksurvey/src")
        const val MAIN_RES_PATH = "main/res"
        const val RES_DIR = "res"
        const val DRAWABLE_DIR_PREFIX = "drawable"
        const val VALUES_DIR_PREFIX = "values"
        const val FLAG_PREFIX = "ic_flag_"
        const val V26_QUALIFIER = "-v26"
        const val EM_DASH = '\u2014'
        const val XML_EXTENSION = "xml"
        const val STRINGS_FILE = "strings.xml"
        val RASTER_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")
        val SOURCE_DIRS = listOf("java", "kotlin")
        val SOURCE_EXTENSIONS = setOf("kt", "java")
        const val THIS_FILE = "ResourceHygieneTest.kt"
        const val MIN_SOURCE_FILES = 100
    }
}
