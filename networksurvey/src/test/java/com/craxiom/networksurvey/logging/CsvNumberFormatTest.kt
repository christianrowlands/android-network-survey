package com.craxiom.networksurvey.logging

import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.networksurvey.services.NetworkSurveyService
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.StringWriter
import java.util.Locale

/**
 * The app's CSV log files keep the user's decimal separator (NS Analytics reads a comma decimal)
 * but must stay machine readable in every locale: the comma decimal field is quoted, there are no
 * grouping separators, and digits and the minus sign stay ASCII even where the locale would write
 * Arabic-Indic digits, the Arabic decimal separator or a U+2212 minus sign.
 */
class CsvNumberFormatTest {
    private lateinit var originalLocale: Locale

    @Before
    fun rememberLocale() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `german keeps the comma decimal separator`() {
        val format = CsvNumberFormat(Locale.GERMANY)

        assertEquals("38,889484", format.toSixDecimalPlaces(38.8894841))
        assertEquals("-77,035278", format.toSixDecimalPlaces(-77.0352781))
        assertEquals("1234567,5", format.toAtMostTwoDecimalPlaces(1_234_567.5))
        assertEquals("-12,34", format.toAtMostTwoDecimalPlaces(-12.341))
    }

    @Test
    fun `french keeps the comma and never writes its narrow no break space grouping`() {
        val format = CsvNumberFormat(Locale.FRANCE)

        assertEquals("1234567,25", format.toAtMostTwoDecimalPlaces(1_234_567.25))
        assertEquals("48,858400", format.toSixDecimalPlaces(48.8584))
    }

    @Test
    fun `us english keeps the dot`() {
        val format = CsvNumberFormat(Locale.US)

        assertEquals("38.889484", format.toSixDecimalPlaces(38.8894841))
        assertEquals("0.5", format.toAtMostTwoDecimalPlaces(0.5))
        assertEquals("12", format.toAtMostTwoDecimalPlaces(12.0))
    }

    @Test
    fun `swedish keeps the comma but writes an ascii minus sign`() {
        val format = CsvNumberFormat(Locale.forLanguageTag("sv-SE"))

        assertEquals("-12,5", format.toAtMostTwoDecimalPlaces(-12.5))
        assertEquals("-77,035278", format.toSixDecimalPlaces(-77.0352781))
    }

    @Test
    fun `arabic persian and devanagari locales write ascii digits and a dot`() {
        for (tag in listOf("ar-EG", "fa-IR", "mr-IN", "bn-BD")) {
            val format = CsvNumberFormat(Locale.forLanguageTag(tag))

            assertEquals(tag, "-38.123457", format.toSixDecimalPlaces(-38.1234567))
            assertEquals(tag, "-12345.67", format.toAtMostTwoDecimalPlaces(-12345.671))
        }
    }

    @Test
    fun `a german log row is valid csv that reads back to the same values`() {
        Locale.setDefault(Locale.GERMANY)
        assertCsvRoundTrip(decimal = ',')
    }

    @Test
    fun `a french log row is valid csv that reads back to the same values`() {
        Locale.setDefault(Locale.FRANCE)
        assertCsvRoundTrip(decimal = ',')
    }

    @Test
    fun `an egyptian arabic log row is valid csv with ascii digits`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertCsvRoundTrip(decimal = '.')
    }

    /**
     * Writes a Device Status row (the logger with the most decimal columns) through the logger's
     * own CSV format under the current default locale, parses it back, and checks each decimal
     * column holds one ASCII number with the expected separator.
     */
    private fun assertCsvRoundTrip(decimal: Char) {
        val logger = DeviceStatusCsvLogger(mock(NetworkSurveyService::class.java))
        val out = StringWriter()
        CSVPrinter(out, logger.createCsvFormat(arrayOf("CSV Version=0.6.0"))).use {
            it.printRecord(*logger.convertToObjectArray(deviceStatus()))
        }

        val format = CSVFormat.DEFAULT.builder().setCommentMarker('#').setHeader().setSkipHeaderRecord(true).build()
        val row = format.parse(out.toString().reader()).records.single()

        assertTrue("Row is consistent with the header", row.isConsistent)
        val expected = mapOf(
            "latitude" to "-33.868820", "longitude" to "151.209296", "altitude" to "-1234.56",
            "speed" to "1.5", "accuracy" to "4", "gnssLatitude" to "-33.868821",
            "gnssLongitude" to "151.209297", "gnssAltitude" to "-1234.57", "gnssAccuracy" to "3"
        )
        for ((column, value) in expected) {
            val written = row[column]
            assertEquals(column, value.replace('.', decimal), written)
            assertTrue("$column is ASCII: $written", written.all { it.code < 128 })
            assertEquals(column, value.toDouble(), written.replace(',', '.').toDouble(), 0.0)
        }
    }

    private fun deviceStatus(): DeviceStatus = DeviceStatus.newBuilder()
        .setData(
            DeviceStatusData.newBuilder()
                .setDeviceTime("2026-10-28T09:15:42.123+11:00")
                .setLatitude(-33.86882)
                .setLongitude(151.209296)
                .setAltitude(-1234.567f)
                .setSpeed(1.5f)
                .setAccuracy(4)
                .setGnssLatitude(-33.868821)
                .setGnssLongitude(151.209297)
                .setGnssAltitude(-1234.577f)
                .setGnssAccuracy(3)
                .setDeviceSerialNumber("serial")
                .setMissionId("mission")
                .setRecordNumber(1)
        )
        .build()
}
