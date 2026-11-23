package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.RegionCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.roadPulseDataStore by preferencesDataStore(name = "roadpulse_local_prefs")

class LocalPreferencesDataStore(private val context: Context) {

    private val keyShowDemo = booleanPreferencesKey("show_demo_data")
    private val keyRegionId = stringPreferencesKey("selected_region_id")

    val showDemoData: Flow<Boolean> = context.roadPulseDataStore.data.map { prefs ->
        prefs[keyShowDemo] ?: true
    }

    val selectedRegionId: Flow<String> = context.roadPulseDataStore.data.map { prefs ->
        prefs[keyRegionId] ?: RegionCatalog.defaultRegion.id
    }

    suspend fun setShowDemoData(enabled: Boolean) {
        context.roadPulseDataStore.edit { prefs ->
            prefs[keyShowDemo] = enabled
        }
    }

    suspend fun setSelectedRegionId(regionId: String) {
        context.roadPulseDataStore.edit { prefs ->
            prefs[keyRegionId] = regionId
        }
    }
}
