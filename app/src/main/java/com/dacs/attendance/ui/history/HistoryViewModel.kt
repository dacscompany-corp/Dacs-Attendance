package com.dacs.attendance.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dacs.attendance.data.repo.AttendancePhotos
import com.dacs.attendance.data.repo.AttendanceRepository
import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.HistoryDay
import com.dacs.attendance.domain.HistorySpan
import com.dacs.attendance.domain.HistorySummary
import com.dacs.attendance.domain.historyDays
import com.dacs.attendance.domain.historyRange
import com.dacs.attendance.domain.historySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val span: HistorySpan = HistorySpan.WEEK,
    val loading: Boolean = true,
    val days: List<HistoryDay> = emptyList(),
    val summary: HistorySummary = HistorySummary(0, 0),
    val failure: AttendanceFailure? = null
)

/** Screen 10 — the worker's own attendance, week or month. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val attendance: AttendanceRepository,
    private val photos: AttendancePhotos
) : ViewModel() {

    /**
     * A link for one attendance photo, or null.
     *
     * Called from the row as it scrolls into view rather than resolved
     * for the whole month up front: a worker who opens History and
     * closes it should pay for the three days they actually saw.
     */
    suspend fun photoUrl(path: String?): String? = photos.signedUrl(path)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load(HistorySpan.WEEK)
    }

    fun onSpanChange(span: HistorySpan) {
        if (span == _uiState.value.span) return
        load(span)
    }

    fun refresh() = load(_uiState.value.span)

    private fun load(span: HistorySpan) {
        _uiState.update { it.copy(span = span, loading = true, failure = null) }
        viewModelScope.launch {
            val range = historyRange(span)
            attendance.history(range.start.toString(), range.endInclusive.toString()).fold(
                onSuccess = { records ->
                    // The days come from the RANGE, not from the records:
                    // a day with no record has to appear as a gap, and a
                    // list built from records alone cannot show one.
                    val days = historyDays(range, records)
                    _uiState.update {
                        it.copy(loading = false, days = days, summary = historySummary(days))
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(loading = false, failure = AttendanceFailure.of(error))
                    }
                }
            )
        }
    }
}
