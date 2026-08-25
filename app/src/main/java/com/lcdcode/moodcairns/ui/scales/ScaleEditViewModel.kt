package com.lcdcode.moodcairns.ui.scales

import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScaleEditUiState(
    val id: Long = 0,
    val name: String = "",
    val minValue: String = "1",
    val maxValue: String = "10",
    val step: String = "1",
    val colorArgb: Int = PALETTE.first(),
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val inverted: Boolean = false,
    val invertDataPrompt: InvertDataPrompt? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val affectedEntryCount: Int? = null,
    val error: String? = null,
) {
    companion object {
        val PALETTE: List<Int> = listOf(
            "#F6C453", "#7D99D1", "#D17D7D", "#9AA39A", "#B5651D",
            "#6BAA75", "#A46CBF", "#4DB6AC", "#E57373", "#5C6BC0",
        ).map(Color::parseColor)
    }
}

/** Asks whether flipping a scale's direction should also remap its logged values. */
data class InvertDataPrompt(val entryCount: Int)

@HiltViewModel
class ScaleEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ScaleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScaleEditUiState())
    val state: StateFlow<ScaleEditUiState> = _state.asStateFlow()

    /** The scale as loaded from the DB; the data-remap reflection uses its range. */
    private var persisted: Scale? = null

    init {
        val id = savedState.get<Long>(ARG_SCALE_ID)?.takeIf { it > 0L }
        if (id == null) {
            _state.update { it.copy(loaded = true) }
        } else {
            viewModelScope.launch {
                val existing = repo.byId(id)
                if (existing != null) {
                    persisted = existing
                    _state.update {
                        it.copy(
                            id = existing.id,
                            name = existing.name,
                            minValue = existing.minValue.toString(),
                            maxValue = existing.maxValue.toString(),
                            step = formatStep(existing.step),
                            colorArgb = existing.colorArgb,
                            isBuiltIn = existing.isBuiltIn,
                            sortOrder = existing.sortOrder,
                            inverted = existing.inverted,
                            loaded = true,
                        )
                    }
                } else {
                    _state.update { it.copy(loaded = true, error = "Scale not found") }
                }
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setMin(v: String) = _state.update { it.copy(minValue = sanitizeSignedInt(v, maxDigits = 4), error = null) }
    fun setMax(v: String) = _state.update { it.copy(maxValue = sanitizeSignedInt(v, maxDigits = 4), error = null) }
    fun setStep(v: String) = _state.update { it.copy(step = sanitizeDecimal(v, maxLen = 5), error = null) }
    fun setColor(argb: Int) = _state.update { it.copy(colorArgb = argb) }
    fun setInverted(v: Boolean) = _state.update { it.copy(inverted = v, error = null) }

    fun save() {
        val cur = _state.value
        val name = cur.name.trim()
        val min = cur.minValue.toIntOrNull()
        val max = cur.maxValue.toIntOrNull()
        val step = cur.step.toFloatOrNull()

        val err = when {
            name.isEmpty() -> "Name required"
            min == null || max == null || step == null -> "Enter numeric min, max, and step"
            min >= max -> "Min must be less than max"
            step <= 0f -> "Step must be greater than zero"
            !isMultipleOfStep(max - min, step) -> "Range (${max - min}) must be a multiple of step (${formatStep(step)})"
            else -> null
        }
        if (err != null) {
            _state.update { it.copy(error = err) }
            return
        }

        val base = persisted
        if (base != null && cur.inverted != base.inverted) {
            viewModelScope.launch {
                val count = repo.countEntriesUsing(cur.id)
                if (count > 0) {
                    _state.update { it.copy(invertDataPrompt = InvertDataPrompt(count)) }
                } else {
                    persist(remapData = false)
                }
            }
            return
        }
        persist(remapData = false)
    }

    /** Confirms the invert-data prompt: save, remapping logged values if asked. */
    fun confirmSave(remapData: Boolean) {
        _state.update { it.copy(invertDataPrompt = null) }
        persist(remapData)
    }

    /** Cancels the invert-data prompt without saving anything. */
    fun dismissInvertDataPrompt() = _state.update { it.copy(invertDataPrompt = null) }

    private fun persist(remapData: Boolean) {
        val cur = _state.value
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val sortOrder = if (cur.id == 0L) repo.nextSortOrder() else cur.sortOrder
            val scale = Scale(
                id = cur.id,
                name = cur.name.trim(),
                minValue = cur.minValue.toInt(),
                maxValue = cur.maxValue.toInt(),
                step = cur.step.toFloat(),
                colorArgb = cur.colorArgb,
                isBuiltIn = cur.isBuiltIn,
                archived = false,
                sortOrder = sortOrder,
                inverted = cur.inverted,
            )
            try {
                val base = persisted
                if (remapData && base != null) {
                    // Reflect across the range the data was recorded under.
                    repo.updateInvertingData(scale, (base.minValue + base.maxValue).toFloat())
                } else {
                    repo.upsert(scale)
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(saving = false, error = t.message ?: "Save failed") }
            }
        }
    }

    /** Load how many entries have data on this scale, for the delete warning. */
    fun loadAffectedEntryCount() {
        val id = _state.value.id
        if (id == 0L) return
        viewModelScope.launch {
            val count = repo.countEntriesUsing(id)
            _state.update { it.copy(affectedEntryCount = count) }
        }
    }

    fun delete() {
        val id = _state.value.id
        if (id == 0L || _state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            try {
                repo.delete(id)
                _state.update { it.copy(deleting = false, deleted = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(deleting = false, error = t.message ?: "Delete failed") }
            }
        }
    }

    companion object { const val ARG_SCALE_ID = "scaleId" }
}

/** Keeps an optional leading minus and up to [maxDigits] digits: "5-6" -> "56", "--5" -> "-5". */
internal fun sanitizeSignedInt(raw: String, maxDigits: Int): String {
    val sign = if (raw.startsWith("-")) "-" else ""
    return sign + raw.filter(Char::isDigit).take(maxDigits)
}

/** Keeps only digits and at most one decimal point; caps total length. */
internal fun sanitizeDecimal(raw: String, maxLen: Int): String {
    val sb = StringBuilder()
    var seenDot = false
    for (c in raw) {
        when {
            c.isDigit() -> sb.append(c)
            c == '.' && !seenDot -> { sb.append(c); seenDot = true }
            else -> { /* drop */ }
        }
        if (sb.length >= maxLen) break
    }
    return sb.toString()
}

/** True when [range] is an integer multiple of [step], within float tolerance. */
internal fun isMultipleOfStep(range: Int, step: Float): Boolean {
    if (step <= 0f) return false
    val quotient = range / step
    val rounded = kotlin.math.round(quotient)
    return kotlin.math.abs(quotient - rounded) < 1e-4f
}

/** Renders step without a trailing ".0" when it's whole. */
internal fun formatStep(step: Float): String {
    val rounded = kotlin.math.round(step)
    return if (kotlin.math.abs(step - rounded) < 1e-4f) rounded.toInt().toString()
    else step.toString().trimEnd('0').trimEnd('.')
}
