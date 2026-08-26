package com.dacs.attendance.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Danger
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary

/**
 * Screen 11 — "My profile".
 *
 * Everything here is READ-ONLY except logging out. A worker cannot edit
 * their own name, position or worker number: those are snapshotted onto
 * every attendance record, and letting the subject of a record rewrite
 * the identity on it would undermine the evidence. Corrections go
 * through the admin, in Users → Navigator.
 */
@Composable
fun ProfileScreen(
    worker: WorkerProfile,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTerms by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(GreenTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialsOf(worker),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Green
                )
            }
            Column {
                Text(
                    text = worker.displayName ?: worker.firstName,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = worker.position ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        if ((worker.status ?: "active") == "active") {
            Text(
                text = stringResource(R.string.profile_active),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Green,
                modifier = Modifier
                    .background(GreenTint, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
        ) {
            InfoRow(stringResource(R.string.label_email), worker.email ?: "—")
            Divider()
            InfoRow(stringResource(R.string.profile_position), worker.position ?: "—")
            Divider()
            InfoRow(stringResource(R.string.profile_worker_id), worker.workerIdLabel, mono = true)
        }

        // The Terms are readable after acceptance, on purpose: a worker
        // who agreed to something should be able to go back and read it
        // without asking anyone.
        RowButton(
            title = stringResource(R.string.terms_title),
            subtitle = stringResource(R.string.profile_terms_sub),
            onClick = { showTerms = true }
        )

        Text(
            text = stringResource(R.string.profile_password_note),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        PrimaryActionButton(
            english = stringResource(R.string.action_log_out),
            tagalog = stringResource(R.string.action_log_out_tl),
            onClick = onSignOut,
            container = Danger
        )
    }

    if (showTerms) {
        TermsReader(onClose = { showTerms = false })
    }
}

@Composable
private fun TermsReader(onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(Dimens.RadiusLarge),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(Dimens.GapMedium)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall)
            ) {
                Text(
                    text = AttendanceTerms.TITLE,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    // The version is shown because acceptance is recorded
                    // against it: a worker can check WHICH wording they
                    // agreed to, not just that they agreed.
                    text = AttendanceTerms.VERSION,
                    fontFamily = MonoFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                AttendanceTerms.clauses.forEachIndexed { index, clause ->
                    Text(
                        text = "${index + 1}. ${clause.heading} ${clause.english}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = clause.tagalog,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                PrimaryActionButton(
                    english = stringResource(R.string.action_close),
                    tagalog = stringResource(R.string.action_close_tl),
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.SecondaryRow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) MonoFamily else null
        )
    }
}

@Composable
private fun Divider() {
    // height(), not size(): size() constrains the WIDTH to 1dp as well,
    // which draws a one-pixel dot instead of a rule.
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
private fun RowButton(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusLarge))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Dimens.SecondaryRow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** "JD" for Juan dela Cruz — first letters of the first two words. */
private fun initialsOf(worker: WorkerProfile): String {
    val parts = (worker.displayName ?: worker.firstName).trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
