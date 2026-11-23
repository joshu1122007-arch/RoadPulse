package com.example

import com.example.data.LocationHelper
import com.example.model.DemoDataCatalog
import com.example.model.HazardCategoryCatalog
import com.example.model.HazardGroup
import com.example.model.HazardSeverity
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.model.IndicativeRiskEngine
import com.example.model.RegionCatalog
import com.example.model.WeatherContext
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class RoadPulseDomainTest {

    @Test
    fun hazardCatalogIncludesUrbanAndDisasterCategories() {
        val urbanCount = HazardCategoryCatalog.categories.count { it.group == HazardGroup.URBAN }
        val disasterCount = HazardCategoryCatalog.categories.count { it.group == HazardGroup.DISASTER }
        assertTrue(urbanCount >= 7)
        assertTrue(disasterCount >= 6)
        assertEquals("Bengaluru Metro", RegionCatalog.defaultRegion.name)
    }

    @Test
    fun indicativeRiskNeverClaimsRouteSafetyWhenZeroIncidents() {
        val areaRisk = IndicativeRiskEngine.assessArea(
            incidents = emptyList(),
            weather = WeatherContext(isAvailable = false),
            regionName = "Bengaluru Metro"
        )
        assertEquals(HazardSeverity.LOW, areaRisk.level)
        assertTrue(areaRisk.summary.contains("does not guarantee road safety", ignoreCase = true))
        assertTrue(areaRisk.disclaimer.contains("Rule-based indicative", ignoreCase = true))
    }

    @Test
    fun indicativeRiskElevatesWithVerifiedDisasterAndHeavyRain() {
        val weather = WeatherContext(
            isAvailable = true,
            temperatureC = 23.0,
            rainfallMm = 18.5,
            conditionLabel = "Heavy Rain",
            observedAtMillis = System.currentTimeMillis()
        )
        val result = IndicativeRiskEngine.assessIncident(
            categoryId = "landslide",
            severity = HazardSeverity.HIGH,
            status = IncidentStatus.VERIFIED,
            createdAt = Timestamp(Date()),
            weather = weather
        )
        assertEquals(HazardSeverity.HIGH, result.level)
        assertTrue(result.factors.any { it.contains("Heavy local rainfall") })
    }

    @Test
    fun haversineAndDuplicateDetectionFindNearbySameCategory() {
        val distKm = LocationHelper.haversineKm(12.9177, 77.6238, 12.9182, 77.6241)
        assertTrue(distKm < 0.1)

        val now = Timestamp(Date())
        val existing = listOf(
            IncidentRecord(
                id = "inc_a",
                categoryId = "waterlogging",
                title = "Silk Board pooling",
                latitude = 12.9177,
                longitude = 77.6238,
                status = IncidentStatus.PENDING_VERIFICATION.name,
                createdAt = now
            ),
            IncidentRecord(
                id = "inc_far",
                categoryId = "waterlogging",
                title = "Hebbal pooling",
                latitude = 13.0358,
                longitude = 77.5970,
                status = IncidentStatus.VERIFIED.name,
                createdAt = now
            ),
            IncidentRecord(
                id = "inc_rejected",
                categoryId = "waterlogging",
                title = "Rejected near Silk Board",
                latitude = 12.9178,
                longitude = 77.6239,
                status = IncidentStatus.REJECTED.name,
                createdAt = now
            )
        )

        val matches = existing.filter { candidate ->
            candidate.statusEnum != IncidentStatus.REJECTED &&
                candidate.categoryId == "waterlogging" &&
                (LocationHelper.haversineKm(12.9180, 77.6240, candidate.latitude, candidate.longitude) * 1000.0) <= 300.0
        }
        assertEquals(1, matches.size)
        assertEquals("inc_a", matches.first().id)
        assertFalse(DemoDataCatalog.sampleIncidents().any { !it.isDemoRecord })
    }
}
