package com.craxiom.networksurvey.logging.db.uploader.ocid

import com.craxiom.networksurvey.logging.db.model.BaseRecordEntity
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity
import com.craxiom.networksurvey.logging.db.model.UploadRecordsWrapper
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Checks every OpenCelliD CSV row against the upload header by name, so a value can never land in
 * the next column over (a UMTS row once had 19 values for the 20 columns, which put the scrambling
 * code in `bid`, the signal in `pci` and the time in `ta`). The column meanings come from
 * OpenCelliD's measurement fields page: psc is UMTS only, pci LTE and NR only, ta GSM and LTE only,
 * signal an integer in dBm, rating the GPS accuracy in meters, speed in meters per second.
 *
 * The default locale is German for the whole class: the upload must be locale independent.
 */
class OpenCelliDCsvFormatterTest {
    private lateinit var originalLocale: Locale

    @Before
    fun useCommaDecimalLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `gsm row carries each value in its own column`() {
        val record = GsmRecordEntity().apply {
            base()
            mcc = 310; mnc = 260; lac = 1234; ci = 5678; signalStrength = -85.4f; ta = 3
        }

        val row = parseSingleRow(wrapper(gsm = listOf(record)))

        assertCommon(row, act = "GSM")
        assertEquals("310", row["mcc"])
        assertEquals("260", row["mnc"])
        assertEquals("1234", row["lac"])
        assertEquals("5678", row["cellid"])
        assertEquals("-85", row["signal"])
        assertEquals("3", row["ta"])
        assertEmpty(row, "sid", "tac", "nid", "bid", "psc", "pci", "direction")
    }

    @Test
    fun `umts row carries the scrambling code in psc and the signal in signal`() {
        val record = UmtsRecordEntity().apply {
            base()
            mcc = 310; mnc = 410; lac = 4321; cid = 98765; psc = 287; rscp = -97.6f
        }

        val row = parseSingleRow(wrapper(umts = listOf(record)))

        assertCommon(row, act = "UMTS")
        assertEquals("310", row["mcc"])
        assertEquals("410", row["mnc"])
        assertEquals("4321", row["lac"])
        assertEquals("98765", row["cellid"])
        assertEquals("287", row["psc"])
        assertEquals("-98", row["signal"])
        assertEmpty(row, "sid", "tac", "nid", "bid", "pci", "ta", "direction")
    }

    @Test
    fun `lte row carries tac, pci and ta in their columns`() {
        val record = LteRecordEntity().apply {
            base()
            mcc = 311; mnc = 480; tac = 7001; eci = 123456789; pci = 401; rsrp = -101f; ta = 12
        }

        val row = parseSingleRow(wrapper(lte = listOf(record)))

        assertCommon(row, act = "LTE")
        assertEquals("311", row["mcc"])
        assertEquals("480", row["mnc"])
        assertEquals("7001", row["tac"])
        assertEquals("123456789", row["cellid"])
        assertEquals("401", row["pci"])
        assertEquals("-101", row["signal"])
        assertEquals("12", row["ta"])
        assertEmpty(row, "sid", "lac", "nid", "bid", "psc", "direction")
    }

    @Test
    fun `nr row carries tac and pci and leaves ta to gsm and lte`() {
        val record = NrRecordEntity().apply {
            base()
            mcc = 310; mnc = 260; tac = 9002; nci = 68_719_476_735L; pci = 1007; ssRsrp = -88.6f; ta = 5
        }

        val row = parseSingleRow(wrapper(nr = listOf(record)))

        assertCommon(row, act = "NR")
        assertEquals("310", row["mcc"])
        assertEquals("260", row["mnc"])
        assertEquals("9002", row["tac"])
        assertEquals("68719476735", row["cellid"])
        assertEquals("1007", row["pci"])
        assertEquals("-89", row["signal"])
        assertEmpty(row, "sid", "lac", "nid", "bid", "psc", "ta", "direction")
    }

    @Test
    fun `missing optional values are empty cells rather than the word null`() {
        val record = LteRecordEntity().apply {
            base()
            mcc = 311; mnc = 480; tac = 7001; eci = 123456789; pci = null; rsrp = -101f; ta = null
            speed = null
        }

        val row = parseSingleRow(wrapper(lte = listOf(record)))

        assertEmpty(row, "pci", "ta", "speed")
    }

    @Test
    fun `an unreadable device time leaves measured_at empty instead of using the upload time`() {
        val record = GsmRecordEntity().apply {
            base()
            deviceTime = "not a time"
            mcc = 310; mnc = 260; lac = 1234; ci = 5678; signalStrength = -85f
        }

        val row = parseSingleRow(wrapper(gsm = listOf(record)))

        assertEmpty(row, "measured_at")
    }

    @Test
    fun `every row has as many values as the header has columns`() {
        val csv = OpenCelliDCsvFormatter.formatRecords(
            wrapper(
                gsm = listOf(GsmRecordEntity().apply { base(); mcc = 1; mnc = 1; lac = 1; ci = 1; signalStrength = -1f }),
                umts = listOf(UmtsRecordEntity().apply { base(); mcc = 1; mnc = 1; lac = 1; cid = 1; psc = 1; rscp = -1f }),
                lte = listOf(LteRecordEntity().apply { base(); mcc = 1; mnc = 1; tac = 1; eci = 1; pci = 1; rsrp = -1f }),
                nr = listOf(NrRecordEntity().apply { base(); mcc = 1; mnc = 1; tac = 1; nci = 1L; pci = 1; ssRsrp = -1f })
            )
        )

        val lines = csv.trimEnd('\n').split('\n')
        val headerCount = parse(lines.first()).first().size()
        assertEquals(5, lines.size)
        for (line in lines.drop(1)) {
            assertEquals("Columns in: $line", headerCount, parse(line).first().size())
        }
    }

    private fun assertCommon(row: CSVRecord, act: String) {
        assertEquals("38.889484", row["lat"])
        assertEquals("-77.035278", row["lon"])
        assertEquals(DEVICE_TIME_EPOCH_MILLIS.toString(), row["measured_at"])
        assertEquals("7", row["rating"])
        assertEquals("1.25", row["speed"])
        assertEquals(act, row["act"])
        assertFalse("devn must be present", row["devn"].isNullOrEmpty())
    }

    private fun assertEmpty(row: CSVRecord, vararg columns: String) {
        for (column in columns) assertEquals("Column $column", "", row[column])
    }

    private fun BaseRecordEntity.base() {
        deviceSerialNumber = "serial"
        deviceName = "device"
        deviceTime = DEVICE_TIME
        latitude = 38.8894841
        longitude = -77.0352781
        accuracy = 7
        speed = 1.25f
    }

    private fun wrapper(
        gsm: List<GsmRecordEntity> = emptyList(),
        umts: List<UmtsRecordEntity> = emptyList(),
        lte: List<LteRecordEntity> = emptyList(),
        nr: List<NrRecordEntity> = emptyList()
    ) = UploadRecordsWrapper(gsm, umts, lte, nr, emptyList())

    private fun parseSingleRow(wrapper: UploadRecordsWrapper): CSVRecord {
        val csv = OpenCelliDCsvFormatter.formatRecords(wrapper)
        val format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
        val records = format.parse(csv.reader()).records
        assertEquals(1, records.size)
        assertEquals("Row is consistent with the header", true, records.single().isConsistent)
        return records.single()
    }

    private fun parse(line: String) = CSVFormat.DEFAULT.parse(line.reader()).records

    private companion object {
        const val DEVICE_TIME = "2026-10-28T09:15:42.123-04:00"
        const val DEVICE_TIME_EPOCH_MILLIS = 1_793_193_342_123L
    }
}
