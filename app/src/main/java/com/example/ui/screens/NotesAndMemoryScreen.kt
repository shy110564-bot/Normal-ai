package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.MemoryCategory
import com.example.data.local.MemoryEntity
import com.example.data.local.NoteEntity
import com.example.ui.components.HudSectionHeader
import com.example.ui.components.HudStatusBadge
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisArcBlue
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisSurfaceCard
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesAndMemoryScreen(
    notes: List<NoteEntity>,
    memories: List<MemoryEntity>,
    memoryEnabled: Boolean,
    onToggleMemoryEnabled: (Boolean) -> Unit,
    onCreateNote: (title: String, content: String, category: String) -> Unit,
    onUpdateNote: (id: Long, title: String, content: String, category: String) -> Unit,
    onDeleteNote: (id: Long) -> Unit,
    onSaveMemory: (category: String, key: String, value: String, importance: Int) -> Unit,
    onForgetMemory: (id: Long) -> Unit,
    onClearAllMemories: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Notes, 1 = Long-Term Memory
    var searchQuery by remember { mutableStateOf("") }

    // Note Editor Dialog State
    var showNoteDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var noteTitleInput by remember { mutableStateOf("") }
    var noteContentInput by remember { mutableStateOf("") }
    var noteCategoryInput by remember { mutableStateOf("General") }

    // Memory Editor Dialog State
    var showMemoryDialog by remember { mutableStateOf(false) }
    var memCategoryInput by remember { mutableStateOf(MemoryCategory.USER_PREFERENCE.name) }
    var memKeyInput by remember { mutableStateOf("") }
    var memValueInput by remember { mutableStateOf("") }
    var selectedMemoryCategoryFilter by remember { mutableStateOf("ALL") }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian)
            .padding(16.dp)
            .testTag("notes_memory_screen")
    ) {
        HudSectionHeader(
            title = "JARVIS KNOWLEDGE & NOTES VAULT",
            subtitle = "Voice & text notes + persistent long-term memory matrix"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Sub-navigation toggle: Notes vs Long-Term Memory
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { selectedSection = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSection == 0) JarvisCyan else JarvisSurfaceCard,
                    contentColor = if (selectedSection == 0) JarvisObsidian else JarvisTextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_notes_btn")
            ) {
                Icon(Icons.Default.NoteAlt, contentDescription = "Notes", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("NOTES (${notes.size})", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { selectedSection = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSection == 1) JarvisCyan else JarvisSurfaceCard,
                    contentColor = if (selectedSection == 1) JarvisObsidian else JarvisTextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_memory_btn")
            ) {
                Icon(Icons.Default.Memory, contentDescription = "Memory", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("MEMORY (${memories.size})", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar + Action Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (selectedSection == 0) "Search notes..." else "Search memories...",
                        color = JarvisTextMuted
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = JarvisCyan)
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("vault_search_input")
            )

            Button(
                onClick = {
                    if (selectedSection == 0) {
                        editingNote = null
                        noteTitleInput = ""
                        noteContentInput = ""
                        noteCategoryInput = "General"
                        showNoteDialog = true
                    } else {
                        memKeyInput = ""
                        memValueInput = ""
                        showMemoryDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisEmerald,
                    contentColor = JarvisObsidian
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("vault_add_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (selectedSection == 0) "New Note" else "Add Memory", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedSection == 0) {
            // NOTES LIST
            val filteredNotes = remember(notes, searchQuery) {
                if (searchQuery.isBlank()) notes
                else notes.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.content.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredNotes.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                    border = BorderStroke(1.dp, JarvisBorderGlow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.NoteAlt,
                            contentDescription = "No Notes",
                            tint = JarvisCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No saved notes yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Say \"JARVIS, create a note...\" or tap 'New Note' above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteItemCard(
                            note = note,
                            onEdit = {
                                editingNote = note
                                noteTitleInput = note.title
                                noteContentInput = note.content
                                noteCategoryInput = note.category
                                showNoteDialog = true
                            },
                            onDelete = { onDeleteNote(note.id) }
                        )
                    }
                }
            }
        } else {
            // LONG-TERM MEMORY SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
                border = BorderStroke(1.dp, JarvisBorderGlow)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LONG-TERM MEMORY SYSTEM",
                            style = MaterialTheme.typography.labelLarge,
                            color = JarvisCyan
                        )
                        Text(
                            text = if (memoryEnabled) "Active — JARVIS recalls and stores important facts" else "Paused — Memory storage disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                    }
                    Switch(
                        checked = memoryEnabled,
                        onCheckedChange = onToggleMemoryEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisObsidian,
                            checkedTrackColor = JarvisCyan
                        ),
                        modifier = Modifier.testTag("memory_master_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Chips + Clear All button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedMemoryCategoryFilter == "ALL",
                    onClick = { selectedMemoryCategoryFilter = "ALL" },
                    label = { Text("All Categories") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                        selectedLabelColor = JarvisCyan
                    )
                )
                MemoryCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedMemoryCategoryFilter == cat.name,
                        onClick = { selectedMemoryCategoryFilter = cat.name },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                            selectedLabelColor = JarvisCyan
                        )
                    )
                }
                if (memories.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearAllConfirm = true },
                        border = BorderStroke(1.dp, JarvisCrimson),
                        modifier = Modifier.testTag("clear_all_memories_btn")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear All Memory",
                            tint = JarvisCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", color = JarvisCrimson)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val filteredMemories = remember(memories, searchQuery, selectedMemoryCategoryFilter) {
                memories.filter { m ->
                    val matchesCat = selectedMemoryCategoryFilter == "ALL" || m.category == selectedMemoryCategoryFilter
                    val matchesQuery = searchQuery.isBlank() ||
                        m.memoryKey.contains(searchQuery, ignoreCase = true) ||
                        m.memoryValue.contains(searchQuery, ignoreCase = true)
                    matchesCat && matchesQuery
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories, key = { it.id }) { mem ->
                    MemoryItemCard(
                        memory = mem,
                        onForget = { onForgetMemory(mem.id) }
                    )
                }
            }
        }
    }

    // Create / Edit Note Dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            containerColor = JarvisSurfaceElevated,
            title = {
                Text(
                    text = if (editingNote == null) "CREATE JARVIS NOTE" else "EDIT NOTE #${editingNote?.id}",
                    style = MaterialTheme.typography.titleLarge,
                    color = JarvisCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteTitleInput,
                        onValueChange = { noteTitleInput = it },
                        label = { Text("Note Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input")
                    )
                    OutlinedTextField(
                        value = noteCategoryInput,
                        onValueChange = { noteCategoryInput = it },
                        label = { Text("Category (Work, Personal, Idea)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteContentInput,
                        onValueChange = { noteContentInput = it },
                        label = { Text("Note Content") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val existing = editingNote
                        if (existing == null) {
                            onCreateNote(noteTitleInput, noteContentInput, noteCategoryInput)
                        } else {
                            onUpdateNote(existing.id, noteTitleInput, noteContentInput, noteCategoryInput)
                        }
                        showNoteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = JarvisObsidian
                    ),
                    modifier = Modifier.testTag("save_note_dialog_btn")
                ) {
                    Text("Save Note", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }

    // Add Memory Dialog
    if (showMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            containerColor = JarvisSurfaceElevated,
            title = {
                Text(
                    text = "STORE LONG-TERM MEMORY",
                    style = MaterialTheme.typography.titleLarge,
                    color = JarvisCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select Category:",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisTextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = memCategoryInput == cat.name,
                                onClick = { memCategoryInput = cat.name },
                                label = { Text(cat.displayName) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = memKeyInput,
                        onValueChange = { memKeyInput = it },
                        label = { Text("Memory Key (e.g., preferred_language)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_key_input")
                    )
                    OutlinedTextField(
                        value = memValueInput,
                        onValueChange = { memValueInput = it },
                        label = { Text("Information to Remember") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_value_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveMemory(memCategoryInput, memKeyInput, memValueInput, 4)
                        showMemoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = JarvisObsidian
                    ),
                    modifier = Modifier.testTag("save_memory_dialog_btn")
                ) {
                    Text("Store Memory", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }

    // Clear All Memory Confirmation
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            containerColor = JarvisSurfaceElevated,
            title = {
                Text("CLEAR ALL JARVIS MEMORY?", color = JarvisCrimson, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    "This will permanently erase all stored user preferences, facts, and custom commands from JARVIS long-term memory.",
                    color = JarvisTextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllMemories()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCrimson)
                ) {
                    Text("Purge All Memory", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun NoteItemCard(
    note: NoteEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(note.updatedAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(note.updatedAt))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, JarvisBorderGlow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    HudStatusBadge(label = "#${note.id} ${note.category}", color = JarvisCyan)
                    if (note.isVoiceCreated) {
                        HudStatusBadge(label = "VOICE", color = JarvisArcBlue)
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Note", tint = JarvisCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Note", tint = JarvisCrimson, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Updated $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextMuted
            )
        }
    }
}

@Composable
private fun MemoryItemCard(
    memory: MemoryEntity,
    onForget: () -> Unit
) {
    val categoryDisplay = MemoryCategory.entries.firstOrNull { it.name == memory.category }?.displayName ?: memory.category
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = JarvisSurfaceCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, JarvisBorderGlow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HudStatusBadge(label = categoryDisplay.uppercase(), color = JarvisAmber)
                    Text(
                        text = memory.memoryKey,
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memory.memoryValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary
                )
            }
            OutlinedButton(
                onClick = onForget,
                border = BorderStroke(1.dp, JarvisCrimson.copy(alpha = 0.7f)),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Forget", color = JarvisCrimson, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
