/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.data.db.PlaylistEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: MusicViewModel,
    onOpenPlaylist: (PlaylistEntity) -> Unit,
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val songCounts by viewModel.playlistSongCounts.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var menuPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameTarget by remember { mutableStateOf<PlaylistEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text("我的歌单", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "共 ${playlists.size} 个",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建歌单")
                    }
                }
            )
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有歌单，点右上角 + 新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlaylist(playlist) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = playlist.name,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${songCounts[playlist.id] ?: 0} 首",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                onClick = {
                                    renameTarget = playlist
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除歌单") },
                                onClick = {
                                    viewModel.deletePlaylist(playlist.id)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = "新建歌单",
            initialName = "",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renameTarget?.let { playlist ->
        NameDialog(
            title = "重命名歌单",
            initialName = playlist.name,
            onConfirm = { name ->
                viewModel.renamePlaylist(playlist.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("输入名称") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
