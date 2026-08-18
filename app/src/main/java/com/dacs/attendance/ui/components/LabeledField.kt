package com.dacs.attendance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Field
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.TextMuted

/**
 * A text field with its English label and Tagalog hint ABOVE it, as the
 * design draws them -- not a floating placeholder. A placeholder that
 * disappears once you type is a label a worker cannot re-read.
 */
@Composable
fun LabeledField(
    label: String,
    tagalogHint: String,
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
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = tagalogHint,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = placeholder?.let { { Text(it, color = TextMuted) } },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.FieldHeight),
            shape = RoundedCornerShape(Dimens.RadiusMedium),
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
                {
                    IconButton(onClick = onTogglePasswordVisible) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            // Bilingual everywhere, including what a screen
                            // reader says out loud.
                            contentDescription = if (passwordVisible) {
                                "Hide password / Itago ang password"
                            } else {
                                "Show password / Ipakita ang password"
                            },
                            tint = TextMuted
                        )
                    }
                }
            } else {
                null
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Field,
                unfocusedContainerColor = Field,
                focusedBorderColor = Green,
                unfocusedBorderColor = BorderDefault
            )
        )
    }
}
