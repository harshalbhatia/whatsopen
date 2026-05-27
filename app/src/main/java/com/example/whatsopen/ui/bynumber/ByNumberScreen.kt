package com.example.whatsopen.ui.bynumber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatsopen.R

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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
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
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::onSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_whatsapp),
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_in_whatsapp))
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(vm) {
        vm.submitEvents.collect { number -> onLaunchWhatsApp(number) }
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
            { Text("$flag  $name") }
        }
        else -> null
    }
}
