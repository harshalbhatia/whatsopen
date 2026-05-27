package com.example.whatsopen.ui.clipboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatsopen.R

object ClipboardTags {
    const val TEXT_FIELD = "clipboard_text_field"
    const val SUBMIT = "clipboard_submit"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    onLaunchWhatsApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: ClipboardViewModel = viewModel(factory = ClipboardViewModel.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_clipboard)) },
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
        ) {
            Text(
                text = stringResource(R.string.clipboard_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .testTag(ClipboardTags.TEXT_FIELD),
                label = { Text(stringResource(R.string.clipboard_hint)) },
                supportingText = {
                    val err = state.errorRes
                    if (err != null) {
                        Text(
                            text = stringResource(err),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(stringResource(R.string.clipboard_helper_text))
                    }
                },
                isError = state.errorRes != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = Int.MAX_VALUE,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = vm::onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(ClipboardTags.SUBMIT),
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
