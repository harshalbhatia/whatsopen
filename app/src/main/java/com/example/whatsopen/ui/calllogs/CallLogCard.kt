package com.example.whatsopen.ui.calllogs

import android.provider.CallLog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.whatsopen.PhoneNumberUtils
import com.example.whatsopen.R
import com.example.whatsopen.data.CallLogItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallLogCard(
    item: CallLogItem,
    onCardClick: () -> Unit,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (countryCode, phoneNumber) = remember(item.number) {
        PhoneNumberUtils.splitPhoneNumber(item.number.trim())
    }
    val dateFormat = remember {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    }
    val formattedDate = remember(item.date) { dateFormat.format(Date(item.date)) }

    val (iconRes, descriptionRes) = when (item.callType) {
        CallLog.Calls.INCOMING_TYPE -> R.drawable.ic_call_incoming to R.string.call_type_incoming
        CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing to R.string.call_type_outgoing
        CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed to R.string.call_type_missed
        else -> R.drawable.ic_call_incoming to R.string.call_type_incoming
    }
    val typeDescription = stringResource(descriptionRes)
    val composite = "$typeDescription from $countryCode $phoneNumber, $formattedDate"

    OutlinedCard(
        onClick = onCardClick,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .testTag(CallLogsTags.CALL_LOG_ROW)
            .semantics(mergeDescendants = true) {
                contentDescription = composite
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (countryCode.isNotEmpty()) {
                        Text(
                            text = countryCode,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = phoneNumber,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onChatClick,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.testTag(CallLogsTags.CHAT_BUTTON),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_whatsapp),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.chat))
            }
        }
    }
}
