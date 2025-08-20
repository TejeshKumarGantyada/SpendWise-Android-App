package com.tejesh.spendwise.Screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tejesh.spendwise.Screens.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onNavigateUp: () -> Unit
) {
    val user by authViewModel.user.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val userProfile by authViewModel.userProfile.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditPhoneDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            authViewModel.updateProfilePicture(uri)
        }
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Profile Header ---
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (user?.photoUrl != null) {
                    AsyncImage(
                        model = user?.photoUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Default Avatar",
                        modifier = Modifier.size(70.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (authState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tap to change picture", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(32.dp))

            // --- User Info Card ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text("Name") },
                        supportingContent = { Text(user?.displayName ?: "Not set") },
                        trailingContent = {
                            IconButton(onClick = { showEditNameDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name")
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Email") },
                        supportingContent = { Text(user?.email ?: "Not available") }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Phone Number") },
                        supportingContent = { Text(userProfile.phone.ifEmpty { "Not set" }) },
                        trailingContent = {
                            IconButton(onClick = { showEditPhoneDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Phone Number")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes buttons to the bottom

            // --- Action Buttons ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { authViewModel.signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Sign Out")
                }
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Delete Account")
                }
            }
        }

        if (showDeleteConfirmDialog) {
            DeleteConfirmDialog(
                onDismiss = { showDeleteConfirmDialog = false },
                onConfirm = {
                    authViewModel.deleteAccount()
                    showDeleteConfirmDialog = false
                }
            )
        }

        if (showEditNameDialog) {
            val currentName = user?.displayName ?: ""
            val (firstName, lastName) = if (currentName.contains(" ")) {
                currentName.split(" ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            } else {
                currentName to ""
            }
            EditNameDialog(
                firstName = firstName,
                lastName = lastName,
                onDismiss = { showEditNameDialog = false },
                onConfirm = { newFirst, newLast ->
                    authViewModel.updateProfile(newFirst, newLast)
                    showEditNameDialog = false
                }
            )
        }

        if (showEditPhoneDialog) {
            EditPhoneDialog(
                currentPhone = userProfile.phone,
                onDismiss = { showEditPhoneDialog = false },
                onConfirm = { newPhone ->
                    authViewModel.updatePhoneNumber(newPhone)
                    showEditPhoneDialog = false
                }
            )
        }
    }
}

@Composable
private fun EditNameDialog(
    firstName: String,
    lastName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var newFirstName by remember { mutableStateOf(firstName) }
    var newLastName by remember { mutableStateOf(lastName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Your Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newFirstName,
                    onValueChange = { newFirstName = it },
                    label = { Text("First Name") }
                )
                OutlinedTextField(
                    value = newLastName,
                    onValueChange = { newLastName = it },
                    label = { Text("Last Name") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newFirstName, newLastName) },
                enabled = newFirstName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditPhoneDialog(
    currentPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPhone by remember { mutableStateOf(if (currentPhone == "Not set") "" else currentPhone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Phone Number") },
        text = {
            OutlinedTextField(
                value = newPhone,
                onValueChange = { newPhone = it },
                label = { Text("Phone Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newPhone) },
                enabled = newPhone.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Account?") },
        text = { Text("This action is permanent and cannot be undone. All of your data will be permanently deleted.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
