package com.example.model

import com.google.firebase.Timestamp

data class RiskAssessmentResult(
    val level: HazardSeverity,
    val score: Int,
    val factors: List<String>,
    val summary: String,
    val disclaimer: String = "Rule-based indicative risk assessment — does not guarantee route safety or predict natural disasters."
)

object IndicativeRiskEngine {

    fun assessIncident(
        categoryId: String,
        severity: HazardSeverity,
        status: IncidentStatus,
        createdAt: Timestamp?,
        weather: WeatherContext?
    ): RiskAssessmentResult {
        val category = HazardCategoryCatalog.findById(categoryId)
        val factors = mutableListOf<String>()
        var points = 0

        // 1. Category base weight
        points += category.baseRiskWeight * 2
        factors.add("Category: ${category.label} (${category.group.label})")

        // 2. Reported / assessed severity
        when (severity) {
            HazardSeverity.HIGH -> {
                points += 4
                factors.add("High reported hazard severity")
            }
            HazardSeverity.MODERATE -> {
                points += 2
                factors.add("Moderate reported hazard severity")
            }
            HazardSeverity.LOW -> {
                points += 1
                factors.add("Low reported hazard severity")
            }
        }

        // 3. Verification status
        when (status) {
            IncidentStatus.VERIFIED -> {
                points += 2
                factors.add("Verified by RoadPulse administrator")
            }
            IncidentStatus.UNDER_INVESTIGATION -> {
                points += 1
                factors.add("Currently under investigation")
            }
            IncidentStatus.PENDING_VERIFICATION -> {
                factors.add("Pending independent verification")
            }
            IncidentStatus.RESOLVED -> {
                points = 1
                factors.add("Marked resolved")
            }
            IncidentStatus.REJECTED -> {
                points = 0
                factors.add("Report rejected")
            }
        }

        // 4. Report recency
        val ageHours = createdAt?.let {
            ((System.currentTimeMillis() - it.toDate().time).coerceAtLeast(0L)) / (1000L * 3600L)
        } ?: 0L
        if (status != IncidentStatus.RESOLVED && status != IncidentStatus.REJECTED) {
            if (ageHours <= 12L) {
                points += 1
                factors.add("Recent report (within last 12 hours)")
            } else if (ageHours > 72L) {
                points = (points - 1).coerceAtLeast(1)
                factors.add("Aging report (${ageHours}h old)")
            }
        }

        // 5. Weather context (only if real weather is available)
        if (weather != null && weather.isAvailable) {
            val rain = weather.rainfallMm ?: 0.0
            if (rain >= 10.0) {
                points += 3
                factors.add("Heavy local rainfall (${String.format("%.1f", rain)} mm) via ${weather.source}")
            } else if (rain >= 2.0) {
                points += 1
                factors.add("Active rainfall (${String.format("%.1f", rain)} mm) via ${weather.source}")
            } else {
                factors.add("Weather context available (${weather.conditionLabel}, ${String.format("%.1f", rain)} mm rain)")
            }
        } else {
            factors.add("Live weather context unavailable — assessment excludes rainfall data")
        }

        val level = when {
            status == IncidentStatus.RESOLVED || status == IncidentStatus.REJECTED -> HazardSeverity.LOW
            points >= 9 -> HazardSeverity.HIGH
            points >= 5 -> HazardSeverity.MODERATE
            else -> HazardSeverity.LOW
        }

        val summary = buildString {
            append("${level.label} indicative risk")
            append(" — ")
            when (status) {
                IncidentStatus.VERIFIED -> append("a verified ${category.label.lowercase()} incident is active.")
                IncidentStatus.PENDING_VERIFICATION -> append("a citizen ${category.label.lowercase()} report is awaiting verification.")
                IncidentStatus.UNDER_INVESTIGATION -> append("this ${category.label.lowercase()} report is under field investigation.")
                IncidentStatus.RESOLVED -> append("this ${category.label.lowercase()} hazard has been marked resolved.")
                IncidentStatus.REJECTED -> append("this report was marked invalid or duplicate.")
            }
            if (weather != null && weather.isAvailable) {
                append(" Weather data (${weather.conditionLabel}) is factored as context.")
            } else {
                append(" Weather data is currently unavailable.")
            }
        }

        return RiskAssessmentResult(
            level = level,
            score = points,
            factors = factors,
            summary = summary
        )
    }

    fun assessArea(
        incidents: List<IncidentRecord>,
        weather: WeatherContext?,
        regionName: String
    ): RiskAssessmentResult {
        val active = incidents.filter {
            it.statusEnum != IncidentStatus.RESOLVED && it.statusEnum != IncidentStatus.REJECTED
        }
        val verifiedCount = active.count { it.statusEnum == IncidentStatus.VERIFIED }
        val highCount = active.count { it.severityEnum == HazardSeverity.HIGH }
        val factors = mutableListOf<String>()

        if (active.isEmpty()) {
            factors.add("0 active incidents recorded in $regionName view")
            if (weather != null && weather.isAvailable) {
                factors.add("Current weather: ${weather.conditionLabel} (${String.format("%.1f", weather.rainfallMm ?: 0.0)} mm)")
            } else {
                factors.add("Live weather data unavailable")
            }
            return RiskAssessmentResult(
                level = HazardSeverity.LOW,
                score = 1,
                factors = factors,
                summary = "No active incidents displayed in $regionName. Absence of reports does not guarantee road safety — unmonitored hazards may still exist."
            )
        }

        var score = 2
        if (highCount > 0) {
            score += 4
            factors.add("$highCount high-severity active incident(s)")
        }
        if (verifiedCount > 0) {
            score += 2
            factors.add("$verifiedCount verified active incident(s)")
        }
        factors.add("${active.size} total active incident(s) in current view")

        if (weather != null && weather.isAvailable) {
            val rain = weather.rainfallMm ?: 0.0
            if (rain >= 10.0) {
                score += 3
                factors.add("Heavy rainfall (${String.format("%.1f", rain)} mm) compounding drainage/visibility risks")
            } else if (rain >= 2.0) {
                score += 1
                factors.add("Light-moderate rainfall (${String.format("%.1f", rain)} mm)")
            }
        } else {
            factors.add("Weather context unavailable")
        }

        val level = when {
            score >= 8 -> HazardSeverity.HIGH
            score >= 5 -> HazardSeverity.MODERATE
            else -> HazardSeverity.LOW
        }

        return RiskAssessmentResult(
            level = level,
            score = score,
            factors = factors,
            summary = "${level.label} indicative area risk in $regionName based on ${active.size} active incident(s) and rule-based environmental factors."
        )
    }
}
