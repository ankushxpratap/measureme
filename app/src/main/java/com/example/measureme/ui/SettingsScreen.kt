package com.example.measureme.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.measureme.data.supabase
import com.example.measureme.logic.MeasurementEngine
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null, measurementEngine: MeasurementEngine? = null) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    val sessionStatus by supabase.auth.sessionStatus.collectAsState(SessionStatus.NotAuthenticated())
    val historyCount = measurementEngine?.savedHistory?.size ?: 0
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var showAuthView by remember { mutableStateOf(false) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var isAuthLoading by remember { mutableStateOf(false) }

    var selectedUnit by remember { mutableStateOf(MeasurementEngine.UnitType.M) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(modifier = Modifier.padding(top = 72.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-1).sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                item {
                    SettingsSectionHeader("Units")
                    UnitsContentInline(selectedUnit) { selectedUnit = it }
                }

                item {
                    SettingsSectionHeader("Manual")
                    ManualContentInline()
                }

                item {
                    SettingsSectionHeader("Support")
                    FeedbackContentInline(
                        onRequestFeature = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@measureme.app")
                                putExtra(Intent.EXTRA_SUBJECT, "Feature Request: MeasureMe")
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        onShareApp = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Check out MeasureMe app for AR measurements! https://measureme.app")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                        }
                    )
                }

                item {
                    SettingsSectionHeader("Account")
                    AccountContentInline(
                        sessionStatus = sessionStatus,
                        historyCount = historyCount,
                        onLoginClick = { showAuthView = true },
                        onLogoutClick = {
                            scope.launch { try { supabase.auth.signOut() } catch (e: Exception) {} }
                        },
                        onEditProfileClick = { showProfileEdit = true },
                        uriHandler = uriHandler
                    )
                }
                
                item { Spacer(modifier = Modifier.height(120.dp)) }
            }
        }

        AnimatedVisibility(
            visible = showAuthView,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            AuthView(
                isLogin = isLogin,
                email = email,
                password = password,
                isAuthLoading = isAuthLoading,
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onToggleAuth = { isLogin = !isLogin },
                onClose = { if (!isAuthLoading) showAuthView = false },
                onSubmit = {
                    if (email.isBlank() || password.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Fields cannot be empty") }
                        return@AuthView
                    }
                    if (password.length < 6) {
                        scope.launch { snackbarHostState.showSnackbar("Password must be at least 6 characters") }
                        return@AuthView
                    }
                    isAuthLoading = true
                    scope.launch {
                        try {
                            if (isLogin) {
                                Log.d("Auth", "Attempting login for $email")
                                supabase.auth.signInWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                                Log.d("Auth", "Login success")
                                snackbarHostState.showSnackbar("Welcome back!")
                                showAuthView = false
                            } else {
                                Log.d("Auth", "Attempting signup for $email")
                                supabase.auth.signUpWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                                Log.d("Auth", "Signup request sent")
                                
                                // Check if we are already authenticated (email confirmation might be disabled)
                                if (supabase.auth.sessionStatus.value is SessionStatus.Authenticated) {
                                    snackbarHostState.showSnackbar("Account created and signed in!")
                                } else {
                                    snackbarHostState.showSnackbar("Verification email sent. Please check your inbox.")
                                }
                                showAuthView = false
                            }
                        } catch (e: Exception) {
                            Log.e("Auth", "Auth error: ${e.message}", e)
                            val friendlyError = when {
                                e.message?.contains("Invalid login credentials", true) == true -> "Invalid email or password"
                                e.message?.contains("User already registered", true) == true -> "Email already exists"
                                e.message?.contains("network", true) == true -> "Network error. Check your connection"
                                else -> e.message ?: "Authentication failed"
                            }
                            snackbarHostState.showSnackbar(friendlyError)
                        } finally {
                            isAuthLoading = false
                        }
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showProfileEdit,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ProfileEditView(
                onClose = { showProfileEdit = false },
                onUpdatePassword = { newPassword ->
                    isAuthLoading = true
                    scope.launch {
                        try {
                            supabase.auth.updateUser {
                                password = newPassword
                            }
                            snackbarHostState.showSnackbar("Password updated successfully!")
                            showProfileEdit = false
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Failed to update password: ${e.message}")
                        } finally {
                            isAuthLoading = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
fun UnitsContentInline(selectedUnit: MeasurementEngine.UnitType, onUnitSelected: (MeasurementEngine.UnitType) -> Unit) {
    val unitOptions = listOf(
        MeasurementEngine.UnitType.MM to "Millimeters (mm)",
        MeasurementEngine.UnitType.CM to "Centimeters (cm)",
        MeasurementEngine.UnitType.M to "Meters (m)",
        MeasurementEngine.UnitType.IN to "Inches (in)",
        MeasurementEngine.UnitType.FT to "Feet (ft)",
        MeasurementEngine.UnitType.YD to "Yards (yd)"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        unitOptions.forEach { (unit, label) ->
            Surface(
                onClick = { onUnitSelected(unit) },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = if (unit == selectedUnit) 0.3f else 0.1f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = if (unit == selectedUnit) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = (unit == selectedUnit),
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
fun ManualContentInline() {
    val steps = listOf(
        "Scan surface" to "Point camera at the floor or a flat surface.",
        "Calibrate" to "Move your phone slightly to detect depth.",
        "Start Point" to "Align the center dot and tap the '+' button.",
        "End Point" to "Move to the target and tap the '✓' button.",
        "Save" to "Access your results in the History folder."
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEach { (title, desc) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun FeedbackContentInline(onRequestFeature: () -> Unit, onShareApp: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Love the app?", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Help us build the best AR tools.", color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
        }

        Surface(
            onClick = onRequestFeature,
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Request a Feature", color = Color.White)
            }
        }

        Surface(
            onClick = onShareApp,
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Share with Friends", color = Color.White)
            }
        }
    }
}

@Composable
fun AccountContentInline(
    sessionStatus: SessionStatus,
    historyCount: Int,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sessionStatus is SessionStatus.Authenticated) {
            val user = sessionStatus.session.user
            
            Surface(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(64.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user?.email ?: "User", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("Cloud Sync Active", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                        }
                        IconButton(onClick = onEditProfileClick) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StorageStatItem(
                            label = "Projects",
                            value = historyCount.toString(),
                            icon = Icons.Rounded.Folder,
                            modifier = Modifier.weight(1f)
                        )
                        StorageStatItem(
                            label = "Cloud Storage",
                            value = "Active",
                            icon = Icons.Rounded.CloudDone,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            SettingItemModern(
                title = "Logout",
                icon = Icons.AutoMirrored.Rounded.Logout,
                onClick = onLogoutClick,
                color = Color(0xFFFF5252)
            )
        } else {
            Surface(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = Color.White
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Join MeasureMe", color = Color.Black, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text("Save your history to the cloud.", color = Color.Black.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.Black)
                }
            }
        }

        SettingItemModern(title = "Terms", icon = Icons.Rounded.Description, onClick = { uriHandler.openUri("https://measuremeweb.lovable.app/terms") })
        SettingItemModern(title = "Privacy", icon = Icons.Rounded.PrivacyTip, onClick = { uriHandler.openUri("https://measuremeweb.lovable.app/privacy") })
        SettingItemModern(title = "About", icon = Icons.Rounded.Info, onClick = { uriHandler.openUri("https://measuremeweb.lovable.app/about") })
        SettingItemModern(title = "Version 1.0.2", icon = Icons.Rounded.Fingerprint, onClick = {})
    }
}

@Composable
fun StorageStatItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun SettingItemModern(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color = Color.White
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = color, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AuthView(
    isLogin: Boolean,
    email: String,
    password: String,
    isAuthLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleAuth: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {})
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isLogin) "Welcome Back" else "Create Account",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    enabled = !isAuthLoading
                ) {
                    if (isAuthLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    } else {
                        Text(if (isLogin) "Login" else "Sign Up", fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onToggleAuth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isLogin) "New here? Create an account" else "Already have an account? Login",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileEditView(
    onClose: () -> Unit,
    onUpdatePassword: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {})
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Edit Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Update Password",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { if (newPassword.length >= 6) onUpdatePassword(newPassword) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    enabled = newPassword.length >= 6
                ) {
                    Text("Update Password", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
