package com.dacs.attendance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dacs.attendance.R
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Field
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.Surface
import com.dacs.attendance.ui.theme.TextLabel
import com.dacs.attendance.ui.theme.TextMuted

/**
 * A text field with its label ABOVE it, as the design draws it -- not a
 * floating placeholder. A placeholder that disappears once you type is a
 * label a worker cannot re-read.
 *
 * The label is set in caps at 13sp: small enough to stay out of the way,
 * shouted enough to survive being glanced at rather than read.
 *
 * v2 fills the field [Field] when it is idle and white when it has
 * focus, so the box you are typing in is the lit one on the screen.
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisible: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.04.em,
            color = TextLabel
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = placeholder?.let {
                { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextMuted) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.FieldHeight),
            shape = RoundedCornerShape(Dimens.RadiusField),
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            visualTransformation = when {
                !isPassword || passwordVisible -> VisualTransformation.None
                else -> PasswordVisualTransformation()
            },
            trailingIcon = if (isPassword && onTogglePasswordVisible != null) {
                { RevealToggle(passwordVisible, onTogglePasswordVisible) }
            } else {
                null
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Field,
                disabledContainerColor = Field,
                focusedBorderColor = Green,
                unfocusedBorderColor = BorderDefault
            )
        )
    }
}

/**
 * "Show" / "Hide", worded rather than left as a bare eye.
 *
 * The design puts the word next to the icon, and it is the right call:
 * a crossed-out eye is ambiguous about which state it is announcing, and
 * this is the control a worker reaches for when they are already unsure
 * whether they typed their password correctly.
 */
@Composable
private fun RevealToggle(visible: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = stringResource(
                if (visible) R.string.action_hide_password else R.string.action_show_password
            ),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Green
        )
    }
}
