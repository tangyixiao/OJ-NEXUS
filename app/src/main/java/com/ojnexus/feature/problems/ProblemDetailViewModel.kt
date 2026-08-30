package com.ojnexus.feature.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.KnowledgeRepository
import com.ojnexus.core.model.FailureCategory
import com.ojnexus.core.model.ProblemDetail
import com.ojnexus.core.model.ProblemNotes
import com.ojnexus.core.model.KnowledgeArea
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.model.Verdict
import com.ojnexus.core.ui.Loadable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable notes draft; persisted through [saveDraft] on debounce or dispose. */
data class NotesDraft(
    val problemId: Long,
    val keyInsight: String,
    val implementationNotes: String,
    val complexity: String,
    val general: String,
) {
    fun toNotes(): ProblemNotes = ProblemNotes(
        problemId = problemId,
        keyInsight = keyInsight,
        implementationNotes = implementationNotes,
        complexity = complexity,
        general = general,
        updatedAt = 0,
    )
}

data class ProblemDetailUiState(
    val detail: ProblemDetail,
    val notesDraft: NotesDraft?,
    val notesSaving: Boolean,
    val knowledge: Set<KnowledgeArea> = emptySet(),
)

class ProblemDetailViewModel(
    private val problemId: Long,
    private val problemRepository: ProblemRepository,
    private val reviewRepository: ReviewRepository,
    private val knowledgeRepository: KnowledgeRepository,
) : ViewModel() {

    private val notesDraft = MutableStateFlow<NotesDraft?>(null)
    private val notesSaving = MutableStateFlow(false)
    private var notesSaveJob: Job? = null

    val state: StateFlow<Loadable<ProblemDetailUiState>> = kotlinx.coroutines.flow.combine(
        problemRepository.observeDetail(problemId),
        knowledgeRepository.observeRelations(problemId),
    ) { detail, knowledge ->
            if (detail == null) {
                Loadable.Failed(NOT_FOUND)
            } else {
                // Seed the draft from storage once; later edits stay user-owned.
                if (notesDraft.value == null) {
                    notesDraft.value = NotesDraft(
                        problemId = problemId,
                        keyInsight = detail.notes?.keyInsight ?: "",
                        implementationNotes = detail.notes?.implementationNotes ?: "",
                        complexity = detail.notes?.complexity ?: "",
                        general = detail.notes?.general ?: "",
                    )
                }
                Loadable.Ready(
                    ProblemDetailUiState(
                        detail = detail,
                        notesDraft = notesDraft.value,
                        notesSaving = notesSaving.value,
                        knowledge = knowledge,
                    ),
                )
            }
        }
        .catch { emit(Loadable.Failed(it.message ?: "Load failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun toggleFavorite(current: Boolean) {
        viewModelScope.launch { problemRepository.setFavorite(problemId, !current) }
    }

    fun setKnowledge(area: KnowledgeArea, selected: Boolean) {
        viewModelScope.launch { knowledgeRepository.setRelation(problemId, area, selected) }
    }

    fun deleteProblem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            problemRepository.deleteProblem(problemId)
            onDeleted()
        }
    }

    fun addAttempt(
        verdict: Verdict,
        durationMinutes: Int?,
        language: String?,
        note: String?,
    ) {
        viewModelScope.launch {
            problemRepository.addAttempt(
                problemId = problemId,
                verdict = verdict,
                durationMinutes = durationMinutes,
                language = language?.takeIf { it.isNotBlank() },
                note = note?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun addFailure(category: FailureCategory, description: String, attemptId: Long? = null) {
        if (description.isBlank()) return
        viewModelScope.launch {
            problemRepository.addFailureEntry(problemId, attemptId, category, description.trim())
        }
    }

    fun deleteFailure(entryId: Long) {
        viewModelScope.launch { problemRepository.deleteFailureEntry(entryId) }
    }

    fun scheduleReview() {
        viewModelScope.launch { reviewRepository.scheduleReview(problemId) }
    }

    fun cancelReview() {
        viewModelScope.launch { reviewRepository.cancelReview(problemId) }
    }

    fun completeReview(result: ReviewResult) {
        viewModelScope.launch { reviewRepository.completeReview(problemId, result) }
    }

    // --- Notes (debounced auto-save) ---

    fun setNotesField(update: (NotesDraft) -> NotesDraft) {
        notesDraft.update { it?.let(update) }
        scheduleNotesSave()
    }

    private fun scheduleNotesSave() {
        notesSaveJob?.cancel()
        notesSaveJob = viewModelScope.launch {
            delay(NOTES_DEBOUNCE_MS)
            val draft = notesDraft.value ?: return@launch
            saveDraft(draft)
        }
    }

    /** Writes the pending draft immediately (also used on dispose). */
    fun flushNotes() {
        notesSaveJob?.cancel()
        val draft = notesDraft.value ?: return
        // NonCancellable: onCleared() cancels the scope; the last save must still land.
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) { saveDraft(draft) }
    }

    private suspend fun saveDraft(draft: NotesDraft) {
        notesSaving.value = true
        problemRepository.saveNotes(draft.toNotes())
        notesSaving.value = false
    }

    override fun onCleared() {
        super.onCleared()
        flushNotes()
    }

    private companion object {
        const val NOTES_DEBOUNCE_MS = 600L
        const val NOT_FOUND = "problem not found"
    }
}
