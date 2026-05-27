package com.example.whatsopen.ui.calllogs

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatsopen.R
import com.example.whatsopen.data.DefaultCallLogRepository

object CallLogsTags {
    const val CHIP_INCOMING = "call_logs_chip_incoming"
    const val CHIP_MISSED = "call_logs_chip_missed"
    const val CHIP_NON_CONTACTS = "call_logs_chip_non_contacts"
    const val GRANT_PERMISSION_BUTTON = "call_logs_grant_permission_button"
    const val EMPTY_STATE = "call_logs_empty_state"
    const val FILTERED_EMPTY_STATE = "call_logs_filtered_empty_state"
    const val PERMISSION_DENIED_STATE = "call_logs_permission_denied_state"
    const val CALL_LOG_LIST = "call_logs_list"
    const val CALL_LOG_ROW = "call_logs_row"
    const val CHAT_BUTTON = "call_logs_chat_button"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogsScreen(
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: CallLogsViewModel = viewModel(factory = rememberCallLogsViewModelFactory()),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val callLog = results[Manifest.permission.READ_CALL_LOG] == true
        val contacts = results[Manifest.permission.READ_CONTACTS] == true
        vm.onCallLogPermissionResult(callLog, contacts)
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        vm.onContactsPermissionResult(granted)
    }

    // Sync initial permission state from system on first composition.
    LaunchedEffect(Unit) {
        val hasCallLog = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCallLog) {
            vm.onCallLogPermissionResult(callLogGranted = true, contactsGranted = hasContacts)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_call_logs)) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.call_logs_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.permissionsState == PermissionsState.Granted) {
                FilterChipsRow(
                    state = state,
                    onIncoming = vm::toggleIncoming,
                    onMissed = vm::toggleMissed,
                    onNonContacts = { checked ->
                        if (checked && state.contactsPermission != ContactsPermission.Granted) {
                            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            vm.toggleNonContacts(checked)
                        }
                    },
                )
                Spacer(Modifier.size(8.dp))
            }

            when {
                state.permissionsState == PermissionsState.NeedsRequest ||
                    state.permissionsState == PermissionsState.Denied -> {
                    val denied = state.permissionsState == PermissionsState.Denied
                    PermissionCta(
                        denied = denied,
                        onPrimaryAction = {
                            if (denied) {
                                // After a denial the system may not re-prompt; deep-link to settings.
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                context.startActivity(intent)
                            } else {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALL_LOG,
                                        Manifest.permission.READ_CONTACTS,
                                    ),
                                )
                            }
                        },
                    )
                }
                state.allLogs.isEmpty() -> {
                    EmptyState(
                        titleRes = R.string.empty_call_logs_title,
                        descriptionRes = R.string.empty_call_logs_description,
                        tag = CallLogsTags.EMPTY_STATE,
                    )
                }
                state.filteredLogs.isEmpty() -> {
                    EmptyState(
                        titleRes = R.string.empty_filtered_call_logs_title,
                        descriptionRes = R.string.empty_filtered_call_logs_description,
                        tag = CallLogsTags.FILTERED_EMPTY_STATE,
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(CallLogsTags.CALL_LOG_LIST),
                    ) {
                        items(
                            items = state.filteredLogs,
                            key = { it.id },
                        ) { item ->
                            CallLogCard(
                                item = item,
                                onCardClick = { vm.onLogClicked(item.number) },
                                onChatClick = { vm.onLogClicked(item.number) },
                            )
                        }
                    }
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.openChatEvents.collect(onOpenChat)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    state: CallLogsUiState,
    onIncoming: (Boolean) -> Unit,
    onMissed: (Boolean) -> Unit,
    onNonContacts: (Boolean) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(
                selected = state.filterIncoming,
                onClick = { onIncoming(!state.filterIncoming) },
                label = { Text(stringResource(R.string.filter_incoming)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_call_incoming),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.testTag(CallLogsTags.CHIP_INCOMING),
            )
        }
        item {
            FilterChip(
                selected = state.filterMissed,
                onClick = { onMissed(!state.filterMissed) },
                label = { Text(stringResource(R.string.filter_missed)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_call_missed),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.testTag(CallLogsTags.CHIP_MISSED),
            )
        }
        item {
            FilterChip(
                selected = state.filterNonContactsOnly,
                onClick = { onNonContacts(!state.filterNonContactsOnly) },
                label = { Text(stringResource(R.string.filter_non_contacts)) },
                modifier = Modifier.testTag(CallLogsTags.CHIP_NON_CONTACTS),
            )
        }
    }
}

@Composable
private fun PermissionCta(
    denied: Boolean,
    onPrimaryAction: () -> Unit,
) {
    val stateTag = if (denied) {
        CallLogsTags.PERMISSION_DENIED_STATE
    } else {
        CallLogsTags.EMPTY_STATE
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag(stateTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_call_log),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .alpha(0.5f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.empty_call_logs_permission_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.empty_call_logs_permission_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onPrimaryAction,
                modifier = Modifier.testTag(CallLogsTags.GRANT_PERMISSION_BUTTON),
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}

@Composable
private fun EmptyState(
    titleRes: Int,
    descriptionRes: Int,
    tag: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_call_log),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .alpha(0.5f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun rememberCallLogsViewModelFactory(
    contentResolver: ContentResolver = LocalContext.current.contentResolver,
): ViewModelProvider.Factory {
    return remember(contentResolver) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val handle = extras.createSavedStateHandle()
                return CallLogsViewModel(
                    savedStateHandle = handle,
                    repository = DefaultCallLogRepository(contentResolver),
                ) as T
            }
        }
    }
}
