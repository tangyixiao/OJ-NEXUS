package com.ojnexus.feature.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.DataError
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.Problem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form validation failures; every one maps to an inline error string resource. */
sealed interface FormError {
    data object IdRequired : FormError
    data object TitleRequired : FormError
    data class Duplicate(val key: String) : FormError
    data object DifficultyInvalid : FormError
    data object UrlInvalid : FormError
    data object SaveFailed : FormError
}

data class ProblemFormState(
    val judge: JudgeId = JudgeId.LOCAL,
    val externalId: String = "",
    val title: String = "",
    val difficulty: String = "",
    val tags: String = "",
    val sourceUrl: String = "",
    val error: FormError? = null,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = !saving && error == null
}

/**
 * One form for both modes: [mode] = null means "add", a problem id means "edit that problem".
 * Save is guarded by the [ProblemFormState.saving] flag so recomposition cannot double-submit.
 */
class ProblemFormViewModel(
    private val repository: ProblemRepository,
    private val editProblemId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(ProblemFormState())
    val state: StateFlow<ProblemFormState> = _state.asStateFlow()

    /** Whether an initial load for edit mode is pending. */
    private var loaded = editProblemId == null

    fun isLoaded(): Boolean = loaded

    fun loadForEdit(onLoaded: (Problem) -> Unit) {
        val id = editProblemId ?: return
        if (loaded) return
        viewModelScope.launch {
            val problem = repository.findProblem(id)
            if (problem != null) {
                loaded = true
                _state.update {
                    ProblemFormState(
                        judge = problem.key.judge,
                        externalId = problem.key.externalId,
                        title = problem.title,
                        difficulty = problem.difficulty?.toString() ?: "",
                        tags = problem.tags.joinToString(" "),
                        sourceUrl = problem.sourceUrl ?: "",
                    )
                }
                onLoaded(problem)
            }
        }
    }

    fun setJudge(judge: JudgeId) = _state.update { it.copy(judge = judge, error = null) }
    fun setExternalId(value: String) = _state.update { it.copy(externalId = value, error = null) }
    fun setTitle(value: String) = _state.update { it.copy(title = value, error = null) }
    fun setDifficulty(value: String) = _state.update { it.copy(difficulty = value, error = null) }
    fun setTags(value: String) = _state.update { it.copy(tags = value, error = null) }
    fun setSourceUrl(value: String) = _state.update { it.copy(sourceUrl = value, error = null) }

    fun save(onDone: () -> Unit) {
        val current = _state.value
        if (current.saving) return

        val validationError = validate(current)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val externalId = when {
                current.judge != JudgeId.LOCAL -> current.externalId.trim()
                editProblemId != null -> current.externalId.trim() // keep the generated id on edit
                else -> repository.newLocalExternalId()
            }
            val input = ProblemRepository.ProblemInput(
                key = ProblemKey(current.judge, externalId),
                title = current.title.trim(),
                difficulty = current.difficulty.trim().toIntOrNull(),
                tags = current.tags.split(' ', ',').mapNotNull { ProblemRepository.normalizeTag(it) }.distinct(),
                sourceUrl = current.sourceUrl.trim().takeIf { it.isNotEmpty() },
            )
            val result = if (editProblemId == null) {
                repository.addProblem(input).mapUnit()
            } else {
                repository.updateProblem(editProblemId, input)
            }
            when (result) {
                is DataResult.Success -> onDone()
                is DataResult.Failure -> _state.update {
                    it.copy(
                        saving = false,
                        error = when (val error = result.error) {
                            is DataError.DuplicateProblem -> FormError.Duplicate(error.key)
                            else -> FormError.SaveFailed
                        },
                    )
                }
            }
        }
    }

    private fun validate(state: ProblemFormState): FormError? {
        // LOCAL problems carry an app-side id; users never type one.
        if (state.judge != JudgeId.LOCAL && state.externalId.isBlank()) return FormError.IdRequired
        if (state.title.isBlank()) return FormError.TitleRequired
        val difficulty = state.difficulty.trim()
        if (difficulty.isNotEmpty() && difficulty.toIntOrNull() == null) return FormError.DifficultyInvalid
        val url = state.sourceUrl.trim()
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            return FormError.UrlInvalid
        }
        return null
    }
}

private fun DataResult<Long>.mapUnit(): DataResult<Unit> = when (this) {
    is DataResult.Success -> DataResult.Success(Unit)
    is DataResult.Failure -> DataResult.Failure(error)
}
