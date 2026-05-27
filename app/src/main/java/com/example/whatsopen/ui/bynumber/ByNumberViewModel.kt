package com.example.whatsopen.ui.bynumber

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.whatsopen.CountryLookup
import com.example.whatsopen.DefaultCountryLookup
import com.example.whatsopen.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope

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
        val info = if (country.isNotEmpty()) lookup.lookup(country) else null
        ByNumberUiState(
            countryCode = country,
            phoneNumber = phone,
            countryName = info?.name,
            countryFlag = info?.flag,
            countryCodeError = countryErr,
            phoneError = phoneErr,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = run {
            val country = countryCodeFlow.value
            val phone = phoneNumberFlow.value
            val info = if (country.isNotEmpty()) lookup.lookup(country) else null
            ByNumberUiState(
                countryCode = country,
                phoneNumber = phone,
                countryName = info?.name,
                countryFlag = info?.flag,
            )
        },
    )

    private val _submitEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val submitEvents: SharedFlow<String> = _submitEvents.asSharedFlow()

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
            _submitEvents.tryEmit(country + phone)
        }
    }

    companion object {
        private const val KEY_COUNTRY = "by_number_country"
        private const val KEY_PHONE = "by_number_phone"

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
