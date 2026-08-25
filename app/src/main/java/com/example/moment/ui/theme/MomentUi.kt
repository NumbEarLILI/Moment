package com.example.moment.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 无底、弱指示线的输入框，避免一层层描边框。 */
@Composable
fun momentTransparentTextFieldColors(): TextFieldColors =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        disabledIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary
    )
