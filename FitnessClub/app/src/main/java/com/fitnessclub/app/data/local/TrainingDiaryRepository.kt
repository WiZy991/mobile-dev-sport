package com.fitnessclub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trainingDiaryPrefs by preferencesDataStore(name = "training_diary_prefs")

data class DiaryExercise(
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Double? = null,
)

data class TrainingDiaryEntry(
    val id: String,
    val userId: String,
    val dateMillis: Long,
    val title: String,
    val durationMinutes: Int?,
    val notes: String,
    val createdAtMillis: Long,
    val workoutType: String? = null,
    val exercises: List<DiaryExercise>? = null,
)

data class NewTrainingDiaryEntry(
    val dateMillis: Long,
    val title: String,
    val durationMinutes: Int?,
    val notes: String,
    val workoutType: String = "custom",
    val exercises: List<DiaryExercise> = emptyList(),
)

@Singleton
class TrainingDiaryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val tokenManager: TokenManager,
) {
    private val entriesKey = stringPreferencesKey("entries_json")

    fun observeEntries(): Flow<List<TrainingDiaryEntry>> {
        return context.trainingDiaryPrefs.data.map { prefs ->
            val all = decode(prefs[entriesKey])
            val userId = runCatching { tokenManager.getUser().first()?.id }.getOrNull().orEmpty()
            if (userId.isBlank()) {
                emptyList()
            } else {
                all.filter { it.userId == userId }
                    .sortedByDescending { it.dateMillis }
            }
        }
    }

    suspend fun addEntry(payload: NewTrainingDiaryEntry) {
        val userId = tokenManager.getUser().first()?.id.orEmpty()
        if (userId.isBlank()) return

        context.trainingDiaryPrefs.edit { prefs ->
            val all = decode(prefs[entriesKey]).toMutableList()
            all += TrainingDiaryEntry(
                id = UUID.randomUUID().toString(),
                userId = userId,
                dateMillis = payload.dateMillis,
                title = payload.title.trim(),
                durationMinutes = payload.durationMinutes,
                notes = payload.notes.trim(),
                createdAtMillis = System.currentTimeMillis(),
                workoutType = payload.workoutType,
                exercises = payload.exercises,
            )
            prefs[entriesKey] = gson.toJson(all)
        }
    }

    suspend fun updateEntry(entryId: String, payload: NewTrainingDiaryEntry) {
        val userId = tokenManager.getUser().first()?.id.orEmpty()
        if (userId.isBlank()) return

        context.trainingDiaryPrefs.edit { prefs ->
            val all = decode(prefs[entriesKey]).toMutableList()
            val index = all.indexOfFirst { it.id == entryId && it.userId == userId }
            if (index < 0) return@edit
            val existing = all[index]
            all[index] = existing.copy(
                title = payload.title.trim(),
                durationMinutes = payload.durationMinutes,
                notes = payload.notes.trim(),
                workoutType = payload.workoutType,
                exercises = payload.exercises,
            )
            prefs[entriesKey] = gson.toJson(all)
        }
    }

    suspend fun deleteEntry(entryId: String) {
        val userId = tokenManager.getUser().first()?.id.orEmpty()
        if (userId.isBlank()) return

        context.trainingDiaryPrefs.edit { prefs ->
            val filtered = decode(prefs[entriesKey]).filterNot {
                it.id == entryId && it.userId == userId
            }
            prefs[entriesKey] = gson.toJson(filtered)
        }
    }

    private fun decode(raw: String?): List<TrainingDiaryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<TrainingDiaryEntry>>() {}.type
        return runCatching { gson.fromJson<List<TrainingDiaryEntry>>(raw, type) }.getOrElse { emptyList() }
    }
}
