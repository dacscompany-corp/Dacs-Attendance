package com.dacs.attendance.ui.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.FailureNotice
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.theme.AttendanceTheme
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenBorder
import com.dacs.attendance.ui.theme.GreenTint
import com.dacs.attendance.ui.theme.TextMuted
import com.dacs.attendance.ui.theme.TextSecondary

/**
 * Screen 02. Shown once, on first log in, and again only if the Terms
 * are re-versioned.
 *
 * The clauses are rendered FROM [AttendanceTerms], which is also what
 * gets hashed and stored as evidence. There is deliberately no second
 * copy of this wording in strings.xml: the text a worker reads and the
 * text we can later produce in a dispute must be the same text.
 */
@Composable
fun TermsScreen(
    worker: WorkerProfile,
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TermsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.accepted) {
        if (state.accepted) onAccepted()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        Text(
            text = stringResource(R.string.terms_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.terms_title_tl),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Text(
            text = stringResource(R.string.terms_once),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenTint, RoundedCornerShape(Dimens.RadiusMedium))
                .border(1.dp, GreenBorder, RoundedCornerShape(Dimens.RadiusMedium))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            AttendanceTerms.clauses.forEachIndexed { index, clause ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${index + 1}. ${clause.heading} ")
                        }
                        append(clause.english)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = clause.tagalog,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Spacer(Modifier.padding(bottom = Dimens.GapSmall))
        }

        state.failure?.let { FailureNotice(it) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderDefault, RoundedCornerShape(Dimens.RadiusMedium))
                // The whole row is the target, not just the 20dp box --
                // gloved hands, in daylight.
                .clickable(enabled = !state.submitting) {
                    viewModel.onAgreedChange(!state.agreed)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Checkbox(
                checked = state.agreed,
                onCheckedChange = { viewModel.onAgreedChange(it) },
                enabled = !state.submitting,
                colors = CheckboxDefaults.colors(checkedColor = Green)
            )
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.terms_agree),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.terms_agree_tl),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        PrimaryActionButton(
            english = stringResource(R.string.action_accept),
            tagalog = stringResource(R.string.action_accept_tl),
            onClick = { viewModel.onAccept(worker) },
            enabled = state.agreed,
            loading = state.submitting
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TermsScreenPreview() {
    AttendanceTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
        ) {
            Text("Terms & Conditions", style = MaterialTheme.typography.headlineMedium)
            AttendanceTerms.clauses.take(2).forEach {
                Text(it.english, style = MaterialTheme.typography.bodyMedium)
                Text(it.tagalog, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            PrimaryActionButton(
                english = "ACCEPT & CONTINUE",
                tagalog = "Tanggapin at magpatuloy",
                onClick = {}
            )
        }
    }
}
