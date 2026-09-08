package com.dacs.attendance.ui.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacs.attendance.R
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.TermsClause
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.components.FailureNotice
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.theme.AttendanceTheme
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.GreenSubtle
import com.dacs.attendance.ui.theme.Hairline
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextMuted

/**
 * Shown once, on first log in, and again only if the Terms are
 * re-versioned.
 *
 * The clauses are rendered FROM [AttendanceTerms], which is also what
 * gets hashed and stored as evidence. The design paraphrases them into
 * one short line each; this deliberately does not follow it there. The
 * text a worker reads and the text we can later produce in a dispute
 * must be the same text, so the design's LAYOUT is applied to the real
 * wording rather than the wording being cut to fit the layout.
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

    TermsContent(
        agreed = state.agreed,
        submitting = state.submitting,
        failure = state.failure,
        onAgreedChange = viewModel::onAgreedChange,
        onAccept = { viewModel.onAccept(worker) },
        modifier = modifier
    )
}

@Composable
private fun TermsContent(
    agreed: Boolean,
    submitting: Boolean,
    failure: com.dacs.attendance.domain.LoginFailure?,
    onAgreedChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(Surface)) {
        Column(
            modifier = Modifier.padding(
                start = Dimens.SheetPadding,
                end = Dimens.SheetPadding,
                top = 14.dp,
                bottom = 16.dp
            )
        ) {
            Text(
                text = stringResource(R.string.terms_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.terms_once),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AttendanceTerms.clauses.forEachIndexed { index, clause ->
                ClauseRow(clause, iconFor(index))
            }
            failure?.let { FailureNotice(it) }
            Spacer(Modifier.height(Dimens.GapSmall))
        }

        // The rule above the footer is what makes the clauses read as a
        // document and the checkbox as a signature line under it.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        Column(
            modifier = Modifier.padding(
                start = Dimens.SheetPadding,
                end = Dimens.SheetPadding,
                top = 16.dp,
                bottom = Dimens.BottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AgreeRow(
                agreed = agreed,
                enabled = !submitting,
                onToggle = { onAgreedChange(!agreed) }
            )
            PrimaryActionButton(
                label = stringResource(R.string.action_accept),
                onClick = onAccept,
                enabled = agreed,
                loading = submitting,
                height = Dimens.ActionCompact
            )
        }
    }
}

/**
 * One clause: an icon, a heading, and the binding sentence under it.
 *
 * The icon is not decoration. Five paragraphs of legal text is exactly
 * the shape a worker skips, and the glyph is what lets them find the one
 * about photos again later without re-reading the other four.
 */
@Composable
private fun ClauseRow(clause: TermsClause, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(21.dp)
        )
        Column {
            Text(
                text = clause.heading.trimEnd('.'),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = clause.english,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

/** The glyph for clause n, in the order [AttendanceTerms] declares them. */
private fun iconFor(index: Int): ImageVector = when (index) {
    0 -> Icons.Filled.Schedule        // recording attendance
    1 -> Icons.Filled.Apartment       // project selection
    2 -> Icons.Filled.PhotoCamera     // photo documentation
    3 -> Icons.Filled.VerifiedUser    // honest use
    else -> Icons.Filled.Visibility   // who can see your records
}

/**
 * The signature line.
 *
 * The whole row is the target, not just the 24dp box -- gloved hands, in
 * daylight -- and it tints green once ticked so the state is visible
 * from the same distance as the button below it.
 */
@Composable
private fun AgreeRow(agreed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(Dimens.RadiusField)
    val border = if (agreed) Green else BorderDefault

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (agreed) GreenSubtle else Surface, shape)
            .border(1.5.dp, border, shape)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (agreed) Green else Surface,
                    RoundedCornerShape(Dimens.RadiusCheck)
                )
                .border(1.5.dp, border, RoundedCornerShape(Dimens.RadiusCheck)),
            contentAlignment = Alignment.Center
        ) {
            // The tick is always drawn, in the border colour when unticked.
            // An empty box and a box with a faint tick read the same at a
            // glance, and this one animates to white the moment it counts.
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (agreed) Color.White else BorderDefault,
                modifier = Modifier.size(17.dp)
            )
        }
        Column {
            Text(
                text = stringResource(R.string.terms_agree),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = stringResource(R.string.terms_agree_sub),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TermsScreenPreview() {
    AttendanceTheme {
        TermsContent(
            agreed = true,
            submitting = false,
            failure = null,
            onAgreedChange = {},
            onAccept = {}
        )
    }
}
