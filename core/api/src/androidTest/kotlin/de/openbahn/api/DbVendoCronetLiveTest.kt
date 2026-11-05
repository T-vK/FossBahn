package de.openbahn.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Device/emulator live check that Cronet TLS can reach Bahn journey search.
 *
 * ./gradlew :core:api:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=de.openbahn.api.DbVendoCronetLiveTest
 */
@RunWith(AndroidJUnit4::class)
class DbVendoCronetLiveTest {
    @Before
    fun installTls() {
        OpenBahnDebugLog.isEnabled = true
        BahnTls.install(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun lueneburgToHamburg_returnsJourneys() = runBlocking {
        val client = DbVendoClient()
        val from = Location(
            id = "A=1@L=8000238@",
            name = "Lüneburg",
            evaNumber = "8000238",
        )
        val to = Location(
            id = "A=1@L=8002549@",
            name = "Hamburg Hbf",
            evaNumber = "8002549",
        )
        val whenTime = LocalDateTime.now(ZoneId.of("Europe/Berlin")).plusHours(1)
            .withSecond(0).withNano(0)
        val result = client.searchJourneys(from, to, JourneySearchOptions(), whenTime)
        assertTrue(
            "expected journeys, got ${result.journeys.size} (Cronet/TLS still blocked?)",
            result.journeys.isNotEmpty(),
        )
    }
}
