package tare.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.datetime.LocalDate
import tare.body.Sex
import tare.units.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TareRepositoryTest {
    private val t0 = Instant.parse("2026-10-26T22:21:00Z")

    private fun repo() = TareRepository(createSqlDriver(JdbcSqliteDriver.IN_MEMORY))

    @Test
    fun storedReadingMatchingALiveOneIsSkipped() {
        val repo = repo()
        repo.add(t0, 78.52, 500, 460, Source.SCALE)
        assertNull(repo.importStored(t0 + 2.minutes, 78.52, 500, 460))
        assertNotNull(repo.importStored(t0 + 40.minutes, 78.52, 500, 460))
        assertNull(repo.importStored(t0 + 2.hoursish(), 7.2, null, null))
        assertEquals(2, repo.all().size)
        assertEquals(Source.SCALE_OFFLINE, repo.all().last().source)
    }

    @Test
    fun settingsRoundTrip() {
        val driver = createSqlDriver(JdbcSqliteDriver.IN_MEMORY)
        val repo = TareRepository(driver)
        repo.updateSettings {
            it.copy(
                profile = Profile(Sex.FEMALE, LocalDate(1991, 2, 3), 168.0, athlete = true),
                unit = WeightUnit.ST,
                goalWeightKg = 60.5,
                pairedScale = PairedScale("A1B2", "ESF-24"),
                importOfflineReadings = false,
                onboarded = true,
            )
        }
        val reloaded = TareRepository(driver).settings.value
        assertEquals(repo.settings.value, reloaded)
        assertEquals(WeightUnit.ST, reloaded.unit)
    }

    @Test
    fun healthSyncFlags() {
        val repo = repo()
        val a = repo.add(t0, 80.0, null, null, Source.MANUAL)
        repo.add(t0 + 1.minutes, 80.1, null, null, Source.MANUAL)
        repo.markHealthSynced(a.id)
        assertEquals(1, repo.unsyncedToHealth().size)
        repo.clearHealthSynced()
        assertEquals(2, repo.unsyncedToHealth().size)
    }

    @Test
    fun compositionNeedsImpedanceAndProfile() {
        val w = WeighIn(1, t0, 74.5, 524, null, Source.SCALE, false)
        assertNull(w.composition(Profile()))
        val profile = Profile(Sex.MALE, LocalDate(1983, 1, 1), 170.0)
        assertEquals(20.7, w.composition(profile)!!.bodyFatPercent)
        assertNull(w.copy(impedanceOhms = null).composition(profile))
    }

    @Test
    fun csvHasHeaderAndRows() {
        val repo = repo()
        repo.add(t0, 74.5, 524, 480, Source.SCALE)
        val csv = CsvExport.build(repo.all(), Profile(Sex.MALE, LocalDate(1983, 1, 1), 170.0))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains(",74.50,scale,524,480,25.77,20.7,"))
    }

    private fun Int.hoursish() = (this * 60).minutes
}
