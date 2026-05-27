package com.example.whatsopen.ui.bynumber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatsopen.R

object ByNumberTags {
    const val COUNTRY_CODE = "by_number_country_code"
    const val PHONE = "by_number_phone"
    const val SUBMIT = "by_number_submit"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByNumberScreen(
    onLaunchWhatsApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: ByNumberViewModel = viewModel(factory = ByNumberViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_by_number)) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.subtitle),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = state.countryCode,
                onValueChange = vm::onCountryCodeChanged,
                label = { Text(stringResource(R.string.country_code_hint)) },
                prefix = { Text("+") },
                supportingText = supportingTextFor(state),
                isError = state.countryCodeError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ByNumberTags.COUNTRY_CODE),
            )
            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = vm::onPhoneChanged,
                label = { Text(stringResource(R.string.phone_number_hint)) },
                supportingText = {
                    val phoneErr = state.phoneError
                    if (phoneErr != null) {
                        Text(stringResource(phoneErr))
                    } else {
                        Text(stringResource(R.string.phone_helper_text))
                    }
                },
                isError = state.phoneError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { vm.onSubmit() }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ByNumberTags.PHONE),
            )
            Button(
                onClick = vm::onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ByNumberTags.SUBMIT),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_whatsapp),
                    contentDescription = null,
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.open_in_whatsapp))
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    // One-shot event collection scoped to STARTED so emissions are dropped if screen is backgrounded.
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.submitEvents.collect(onLaunchWhatsApp)
        }
    }
}

private fun supportingTextFor(state: ByNumberUiState): @Composable (() -> Unit)? {
    val err = state.countryCodeError
    val name = state.countryName
    val flag = state.countryFlag
    return when {
        err != null -> {
            { Text(stringResource(err)) }
        }
        name != null && flag != null -> {
            { Text("$flag $name") }
        }
        else -> null
    }
}
