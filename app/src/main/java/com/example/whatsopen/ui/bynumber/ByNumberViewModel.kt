package com.example.whatsopen.ui.bynumber

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.whatsopen.CountryLookup
import com.example.whatsopen.DefaultCountryLookup
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

data class ByNumberUiState(
    val countryCode: String = "",
    val phoneNumber: String = "",
    val countryName: String? = null,
    val countryFlag: String? = null,
    @StringRes val countryCodeError: Int? = null,
    @StringRes val phoneError: Int? = null,
)

class ByNumberViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val lookup: CountryLookup = DefaultCountryLookup,
) : ViewModel() {

    private val countryCodeFlow = savedStateHandle.getStateFlow(KEY_COUNTRY, "")
    private val phoneNumberFlow = savedStateHandle.getStateFlow(KEY_PHONE, "")
    private val countryCodeErrorFlow = MutableStateFlow<Int?>(null)
    private val phoneErrorFlow = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ByNumberUiState> = combine(
        countryCodeFlow,
        phoneNumberFlow,
        countryCodeErrorFlow,
        phoneErrorFlow,
    ) { country, phone, countryErr, phoneErr ->
        buildState(country, phone, countryErr, phoneErr)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = buildState(
            countryCodeFlow.value,
            phoneNumberFlow.value,
            countryCodeErrorFlow.value,
            phoneErrorFlow.value,
        ),
    )

    private val _submitChannel = Channel<String>(Channel.BUFFERED)
    val submitEvents: Flow<String> = _submitChannel.receiveAsFlow()

    fun onCountryCodeChanged(input: String) {
        val cleaned = input.removePrefix("+").filter { it.isDigit() }
        countryCodeErrorFlow.value = null
        savedStateHandle[KEY_COUNTRY] = cleaned
    }

    fun onPhoneChanged(input: String) {
        val cleaned = input.filter { it.isDigit() }
        phoneErrorFlow.value = null
        savedStateHandle[KEY_PHONE] = cleaned
    }

    fun onSubmit() {
        val country = countryCodeFlow.value
        val phone = phoneNumberFlow.value
        var hasError = false
        if (country.isEmpty()) {
            countryCodeErrorFlow.value = R.string.error_country_code_required
            hasError = true
        }
        if (phone.isEmpty()) {
            phoneErrorFlow.value = R.string.error_phone_required
            hasError = true
        }
        if (!hasError) {
            viewModelScope.launch { _submitChannel.send(country + phone) }
        }
    }

    private fun buildState(
        countryCode: String,
        phoneNumber: String,
        @StringRes countryCodeError: Int?,
        @StringRes phoneError: Int?,
    ): ByNumberUiState {
        val info = if (countryCode.isNotEmpty()) lookup.lookup(countryCode) else null
        return ByNumberUiState(
            countryCode = countryCode,
            phoneNumber = phoneNumber,
            countryName = info?.name,
            countryFlag = info?.flag,
            countryCodeError = countryCodeError,
            phoneError = phoneError,
        )
    }

    companion object {
        private const val KEY_COUNTRY = "by_number_country"
        private const val KEY_PHONE = "by_number_phone"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val handle = extras.createSavedStateHandle()
                return ByNumberViewModel(handle) as T
            }
        }
    }
}
