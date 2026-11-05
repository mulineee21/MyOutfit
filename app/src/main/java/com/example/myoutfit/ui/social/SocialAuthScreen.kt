package com.example.myoutfit.ui.social

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.myoutfit.ui.legal.PrivacyPolicyDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class AuthScreenStep {
    LOGIN,
    REGISTER,
    FORGOT_EMAIL,
    RESET_CODE
}

@Composable
fun SocialAuthScreen(
    isSubmitting: Boolean,
    resetCodePendingEmail: String?,
    passwordResetCompleted: Boolean,
    onLogin: (identifier: String, password: String) -> Unit,
    onRegister: (
        username: String,
        email: String,
        password: String,
        displayName: String,
        isPrivate: Boolean
    ) -> Unit,
    onGoogleIdToken: (idToken: String) -> Unit,
    onGoogleError: (message: String) -> Unit,
    onRequestPasswordReset: (email: String) -> Unit,
    onConfirmPasswordReset: (email: String, code: String, newPassword: String) -> Unit,
    onCancelPasswordReset: () -> Unit,
    onConsumePasswordResetCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(AuthScreenStep.LOGIN) }
    var isGoogleLaunching by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by rememberSaveable { mutableStateOf(false) }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }

    LaunchedEffect(resetCodePendingEmail) {
        if (!resetCodePendingEmail.isNullOrBlank()) {
            step = AuthScreenStep.RESET_CODE
        }
    }

    LaunchedEffect(passwordResetCompleted) {
        if (passwordResetCompleted) {
            step = AuthScreenStep.LOGIN
            onConsumePasswordResetCompleted()
        }
    }

    fun launchGoogleSignIn() {
        if (isSubmitting || isGoogleLaunching) return
        scope.launch {
            isGoogleLaunching = true
            val result = GoogleAuthHelper.requestGoogleIdToken(context)
            isGoogleLaunching = false
            result.fold(
                onSuccess = { idToken ->
                    if (!idToken.isNullOrBlank()) {
                        onGoogleIdToken(idToken)
                    }
                },
                onFailure = { err ->
                    onGoogleError(err.message ?: "No se pudo iniciar sesión con Google")
                }
            )
        }
    }

    val busy = isSubmitting || isGoogleLaunching

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Identidad visual de MyOutfit Social
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "MyOutfit",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Comparte tu outfit diario durante 24h y guarda tu calendario de estilo todo el año.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        AnimatedContent(
            targetState = step,
            label = "auth_step_transition"
        ) { currentStep ->
            when (currentStep) {
                AuthScreenStep.LOGIN -> LoginCard(
                    busy = busy,
                    onLogin = onLogin,
                    onGoogleClick = { launchGoogleSignIn() },
                    onForgotPasswordClick = { step = AuthScreenStep.FORGOT_EMAIL },
                    onGoToRegister = { step = AuthScreenStep.REGISTER },
                    onOpenPrivacyPolicy = { showPrivacyPolicyDialog = true }
                )

                AuthScreenStep.REGISTER -> RegisterCard(
                    busy = busy,
                    onRegister = onRegister,
                    onGoogleClick = { launchGoogleSignIn() },
                    onBackToLogin = { step = AuthScreenStep.LOGIN },
                    onOpenPrivacyPolicy = { showPrivacyPolicyDialog = true }
                )

                AuthScreenStep.FORGOT_EMAIL -> ForgotPasswordEmailCard(
                    busy = busy,
                    onSendCode = { email -> onRequestPasswordReset(email) },
                    onAlreadyHaveCode = { step = AuthScreenStep.RESET_CODE },
                    onBackToLogin = {
                        onCancelPasswordReset()
                        step = AuthScreenStep.LOGIN
                    }
                )

                AuthScreenStep.RESET_CODE -> ResetPasswordCodeCard(
                    busy = busy,
                    initialEmail = resetCodePendingEmail.orEmpty(),
                    onConfirmReset = onConfirmPasswordReset,
                    onResendCode = { email -> onRequestPasswordReset(email) },
                    onBackToLogin = {
                        onCancelPasswordReset()
                        step = AuthScreenStep.LOGIN
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LoginCard(
    busy: Boolean,
    onLogin: (identifier: String, password: String) -> Unit,
    onGoogleClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onGoToRegister: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GoogleSignInButton(
                text = "Continuar con Google",
                enabled = !busy,
                onClick = onGoogleClick
            )

            OrDivider()

            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("Correo electrónico o @usuario") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(
                    onClick = onForgotPasswordClick,
                    enabled = !busy
                ) {
                    Text("¿Has olvidado tu contraseña?")
                }
            }

            Button(
                onClick = { onLogin(identifier, password) },
                enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text("Iniciar sesión", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "¿No tienes cuenta?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onGoToRegister, enabled = !busy) {
                    Text("Regístrate", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onOpenPrivacyPolicy, enabled = !busy) {
                    Text(
                        text = "Privacidad RGPD (UE) y LOPDGDD (España)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun RegisterCard(
    busy: Boolean,
    onRegister: (
        username: String,
        email: String,
        password: String,
        displayName: String,
        isPrivate: Boolean
    ) -> Unit,
    onGoogleClick: () -> Unit,
    onBackToLogin: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isPrivateAccount by rememberSaveable { mutableStateOf(true) } // Privacidad por defecto (Art. 25 RGPD)
    var acceptedPrivacy by rememberSaveable { mutableStateOf(false) } // Desmarcado por defecto (Art. 7 RGPD)
    var confirmedAge14 by rememberSaveable { mutableStateOf(false) } // Desmarcado por defecto (Art. 7.1 LOPDGDD)

    val cleanUsername = username.trim().removePrefix("@")
    val usernameValid = cleanUsername.length in 3..30 &&
        cleanUsername.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    val emailValid = email.contains("@") && email.contains(".")
    val passwordsMatch = password.length >= 8 && password == confirmPassword

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Crear cuenta",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            GoogleSignInButton(
                text = "Registrarse con Google",
                enabled = !busy,
                onClick = onGoogleClick
            )

            OrDivider()

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Nombre completo") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase() },
                label = { Text("Nombre de usuario") },
                placeholder = { Text("ej. joel_style") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                supportingText = {
                    if (username.isNotBlank() && !usernameValid) {
                        Text("Usa entre 3 y 30 letras, números, puntos o guiones bajos.")
                    }
                },
                isError = username.isNotBlank() && !usernameValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña (mín. 8 caracteres)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirmar contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                isError = confirmPassword.isNotBlank() && password != confirmPassword,
                supportingText = {
                    if (confirmPassword.isNotBlank() && password != confirmPassword) {
                        Text("Las contraseñas no coinciden")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cuenta privada (por defecto)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Solo seguidores que apruebes podrán ver tus outfits de 24h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPrivateAccount,
                    onCheckedChange = { isPrivateAccount = it }
                )
            }

            // Consentimiento expreso e informado RGPD (UE 2016/679)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { acceptedPrivacy = !acceptedPrivacy },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = acceptedPrivacy,
                    onCheckedChange = { acceptedPrivacy = it },
                    enabled = !busy
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Acepto la Política de Privacidad y el tratamiento de mis datos personales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Leer Política RGPD y derechos ARSULIPO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = !busy, onClick = onOpenPrivacyPolicy)
                    )
                }
            }

            // Verificación de edad mínima conforme al Art. 7.1 de la LOPDGDD (España: 14 años)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { confirmedAge14 = !confirmedAge14 },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = confirmedAge14,
                    onCheckedChange = { confirmedAge14 = it },
                    enabled = !busy
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Confirmo que tengo al menos 14 años de edad (Art. 7.1 LOPDGDD en España / Art. 8 RGPD).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    onRegister(
                        cleanUsername,
                        email,
                        password,
                        displayName.ifBlank { cleanUsername },
                        isPrivateAccount
                    )
                },
                enabled = !busy && usernameValid && emailValid && passwordsMatch && acceptedPrivacy && confirmedAge14,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text("Crear cuenta", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "¿Ya tienes cuenta?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onBackToLogin, enabled = !busy) {
                    Text("Inicia sesión", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ForgotPasswordEmailCard(
    busy: Boolean,
    onSendCode: (email: String) -> Unit,
    onAlreadyHaveCode: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    val emailValid = email.contains("@") && email.contains(".")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToLogin, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    text = "Recuperar contraseña",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Text(
                text = "Introduce el correo electrónico asociado a tu cuenta y te enviaremos un código de verificación de 6 dígitos para crear una contraseña nueva.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSendCode(email) },
                enabled = !busy && emailValid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text("Enviar código al correo", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onAlreadyHaveCode,
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Ya tengo un código de 6 dígitos")
            }
        }
    }
}

@Composable
private fun ResetPasswordCodeCard(
    busy: Boolean,
    initialEmail: String,
    onConfirmReset: (email: String, code: String, newPassword: String) -> Unit,
    onResendCode: (email: String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by rememberSaveable(initialEmail) { mutableStateOf(initialEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val codeValid = code.length == 6 && code.all { it.isDigit() }
    val passwordsValid = newPassword.length >= 8 && newPassword == confirmPassword
    val emailValid = email.contains("@") && email.contains(".")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToLogin, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    text = "Nueva contraseña",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Text(
                text = "Revisa tu bandeja de entrada e introduce el código de 6 dígitos junto con tu nueva contraseña.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = code,
                onValueChange = { input ->
                    code = input.filter { it.isDigit() }.take(6)
                },
                label = { Text("Código de 6 dígitos") },
                placeholder = { Text("123456") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Nueva contraseña (mín. 8 caracteres)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Repetir nueva contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                isError = confirmPassword.isNotBlank() && newPassword != confirmPassword,
                supportingText = {
                    if (confirmPassword.isNotBlank() && newPassword != confirmPassword) {
                        Text("Las contraseñas no coinciden")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onConfirmReset(email, code, newPassword) },
                enabled = !busy && emailValid && codeValid && passwordsValid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text("Actualizar contraseña", fontWeight = FontWeight.Bold)
            }

            if (emailValid) {
                TextButton(
                    onClick = { onResendCode(email) },
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Reenviar código")
                }
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "G",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "  o con tu correo  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
