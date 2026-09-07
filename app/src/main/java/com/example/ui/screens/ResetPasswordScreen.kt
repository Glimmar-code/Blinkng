package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BlinkCream
import com.example.ui.theme.BlinkBlack
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkTextSecondary

@Composable
fun ResetPasswordScreen(
    onSubmit: (String, (Boolean, String) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val strong = password.length >= 8 &&
        password.any(Char::isLowerCase) &&
        password.any(Char::isUpperCase) &&
        password.any(Char::isDigit) &&
        password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    val matches = password.isNotBlank() && password == confirmation

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create a new password",
                color = BlinkCream,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Use at least 8 characters with uppercase, lowercase, a number and a symbol.",
                color = DarkTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; message = null },
                modifier = Modifier.fillMaxWidth().testTag("reset_password_field"),
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors()
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it; message = null },
                modifier = Modifier.fillMaxWidth().testTag("reset_password_confirm_field"),
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors()
            )

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    when {
                        !strong -> message = "Use at least 8 characters with uppercase, lowercase, a number and a symbol."
                        !matches -> message = "The passwords do not match."
                        else -> {
                            isSubmitting = true
                            onSubmit(password) { success, resultMessage ->
                                isSubmitting = false
                                if (!success) message = resultMessage
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlinkCream,
                    contentColor = BlinkBlack,
                    disabledContainerColor = DarkSurfaceElevated,
                    disabledContentColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("reset_password_submit")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Update password", fontWeight = FontWeight.Bold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onCancel) {
                    Text("Back to sign in", color = DarkTextSecondary)
                }
            }
        }
    }
}
