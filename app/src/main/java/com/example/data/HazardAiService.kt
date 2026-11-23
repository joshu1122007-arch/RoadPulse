package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.HazardCategoryCatalog
import com.example.model.HazardSeverity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface AiClassificationState {
    data object Idle : AiClassificationState
    data object Analyzing : AiClassificationState
    data class Success(val result: AiClassificationResult) : AiClassificationState
    data class Unavailable(val reason: String) : AiClassificationState
}

data class AiClassificationResult(
    val categoryId: String,
    val confidence: Double,
    val description: String,
    val suggestedSeverity: HazardSeverity,
    val requiresHumanReview: Boolean,
    val modelName: String = "gemini-3.8-flash"
)

class HazardAiRepository {

    private val allowedCategoryIds = HazardCategoryCatalog.categories.map { it.id }

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "category" to Schema.enumeration(
                values = allowedCategoryIds,
                description = "Best matching road hazard or disaster category ID"
            ),
            "confidence" to Schema.double(
                description = "Confidence score between 0.0 and 1.0"
            ),
            "description" to Schema.string(
                description = "Brief factual 1-2 sentence description of the visible road hazard in the photo"
            ),
            "suggestedSeverity" to Schema.enumeration(
                values = listOf("low", "moderate", "high"),
                description = "Suggested severity level"
            ),
            "requiresHumanReview" to Schema.boolean(
                description = "Always true for citizen hazard submissions requiring verification"
            )
        )
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = this@HazardAiRepository.responseSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun classifyHazardPhoto(bitmap: Bitmap): AiClassificationState {
        return try {
            val promptText = """
                You are an AI-assisted road safety and disaster hazard image triage assistant for RoadPulse.
                Inspect the uploaded photograph carefully.
                1. Choose the single closest hazard category ID from: ${allowedCategoryIds.joinToString(", ")}.
                2. Provide an honest confidence score from 0.0 to 1.0. If the photo is blurry, unclear, or does not clearly show a road hazard, lower the confidence accordingly and choose the closest 'other_urban' or 'other_disaster' category.
                3. Provide a concise factual description of what is visible.
                4. Suggest a severity ('low', 'moderate', or 'high').
                5. Always set requiresHumanReview to true because AI classifications never auto-verify incidents.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }

            val jsonText = response.text
                ?: return AiClassificationState.Unavailable("AI service returned an empty response. You can still select a category manually.")

            val json = JSONObject(jsonText)
            val rawCat = json.optString("category", "other_urban")
            val validCat = if (rawCat in allowedCategoryIds) rawCat else "other_urban"
            val confidence = json.optDouble("confidence", 0.5).coerceIn(0.0, 1.0)
            val description = json.optString("description", "").take(380)
            val severity = HazardSeverity.fromRaw(json.optString("suggestedSeverity", "moderate"))
            val requiresReview = json.optBoolean("requiresHumanReview", true)

            AiClassificationState.Success(
                AiClassificationResult(
                    categoryId = validCat,
                    confidence = confidence,
                    description = description,
                    suggestedSeverity = severity,
                    requiresHumanReview = requiresReview
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI hazard classification timed out", e)
            AiClassificationState.Unavailable("AI classification timed out after 60s. Select a category manually to continue.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI hazard classification failed", e)
            AiClassificationState.Unavailable(
                "AI classification unavailable (${e.localizedMessage ?: "service error"}). You can still select a hazard category manually."
            )
        }
    }

    private companion object {
        const val TAG = "HazardAiRepo"
    }
}
