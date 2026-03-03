package com.eutalix.safbridge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.eutalix.safbridge.data.PreferencesManager
import com.eutalix.safbridge.ui.components.RenameDialog

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefsManager: PreferencesManager,
    onNavigateSettings: () -> Unit
) {
    val context = LocalContext.current
    // State for the list of folders
    var accounts by remember { mutableStateOf(prefsManager.getAccounts()) }
    
    // State for multi-selection mode
    val selectedUris = remember { mutableStateListOf<String>() }
    val isSelectionMode by remember { derivedStateOf { selectedUris.isNotEmpty() } }
    
    // State for rename dialog
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Launcher to pick a folder from the system
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                // Persist permission across reboots
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
                
                val name = DocumentFile.fromTreeUri(context, it)?.name ?: "New Folder"
                prefsManager.saveAccount(it, name)
                accounts = prefsManager.getAccounts() // Refresh list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) Text("${selectedUris.size} selected", fontWeight = FontWeight.Bold)
                    else Text("SAF Plugin", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSelectionMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
                ),
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedUris.forEach { prefsManager.removeAccount(it) }
                            selectedUris.clear()
                            accounts = prefsManager.getAccounts()
                        }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                        IconButton(onClick = { selectedUris.clear() }) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    } else {
                        IconButton(onClick = onNavigateSettings) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { launcher.launch(null) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Instructional Card (Only show when not selecting)
            if (!isSelectionMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("How to connect?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1. Add folders here using the (+) button.\n" +
                            "2. Open ZArchiver.\n" +
                            "3. Tap the storage dropdown menu (top bar) to access them.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Folder List
            if (accounts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No folders mapped", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(accounts, key = { it.first }) { (uri, name) ->
                        val isSelected = selectedUris.contains(uri)
                        
                        ListItem(
                            headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(Uri.decode(uri), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) },
                            leadingContent = {
                                Icon(
                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.Folder,
                                    null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            },
                            modifier = Modifier
                                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedUris.remove(uri) else selectedUris.add(uri)
                                        } else {
                                            renameTarget = Pair(uri, name)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) selectedUris.add(uri)
                                    }
                                )
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    // Show Rename Dialog if needed
    renameTarget?.let { (uri, oldName) ->
        RenameDialog(
            currentName = oldName,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                prefsManager.renameAccount(uri, newName)
                accounts = prefsManager.getAccounts()
                renameTarget = null
            }
        )
    }
}