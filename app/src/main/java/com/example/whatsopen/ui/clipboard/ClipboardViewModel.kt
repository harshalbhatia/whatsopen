package com.example.whatsopen.ui.clipboard

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.whatsopen.PhoneNumberUtils
import com.example.whatsopen.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClipboardUiState(
    val text: String = "",
    @StringRes val errorRes: Int? = null,
)

class ClipboardViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val textFlow = savedStateHandle.getStateFlow(KEY_TEXT, "")
    private val errorFlow = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ClipboardUiState> = combine(
        textFlow,
        errorFlow,
    ) { text, err ->
        buildState(text, err)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = buildState(textFlow.value, errorFlow.value),
    )

    private val _submitChannel = Channel<String>(Channel.BUFFERED)
    val submitEvents: Flow<String> = _submitChannel.receiveAsFlow()

    fun onTextChanged(newText: String) {
        savedStateHandle[KEY_TEXT] = newText
        if (errorFlow.value != null) {
            errorFlow.value = null
        }
    }

    fun onSubmit() {
        val trimmed = textFlow.value.trim()
        if (trimmed.isEmpty()) {
            errorFlow.value = R.string.error_no_valid_numbers
            return
        }
        val numbers = PhoneNumberUtils.parsePhoneNumbers(trimmed)
        if (numbers.isEmpty()) {
            errorFlow.value = R.string.error_no_valid_numbers
            return
        }
        viewModelScope.launch { _submitChannel.send(numbers.first()) }
    }

    private fun buildState(text: String, @StringRes errorRes: Int?): ClipboardUiState {
        return ClipboardUiState(text = text, errorRes = errorRes)
    }

    companion object {
        private const val KEY_TEXT = "clipboard_text"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val handle = extras.createSavedStateHandle()
                return ClipboardViewModel(handle) as T
            }
        }
    }
}
