/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.player.DefaultPlayerHelper
import com.example.musicplayer.ui.BlacklistScreen
import com.example.musicplayer.ui.LibraryScreen
import com.example.musicplayer.ui.MusicViewModel
import com.example.musicplayer.ui.PlaylistDetailScreen
import com.example.musicplayer.ui.PlaylistsScreen
import com.example.musicplayer.ui.PlayerScreen
import com.example.musicplayer.ui.Screen
import com.example.musicplayer.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {

    /**
     * 系统「音乐播放器」角色申请弹窗的结果。
     * 必须在 Activity 进入 STARTED 之前注册，所以放在属性初始化里。
     */
    private val musicRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DefaultPlayerHelper.markRoleGranted(this)
            toast("已设为默认音乐播放器")
        } else {
            toast("未获得默认播放器权限，可在系统设置里手动设置")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val vm: MusicViewModel = viewModel()

                    // 关键：必须是 Compose state，否则权限回调赋值后不会重组
                    var audioGranted by remember { mutableStateOf(hasAudioPermission(context)) }

                    val permissionLauncher =
                        androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts
                                .RequestMultiplePermissions()
                        ) {
                            // 以「音频权限」为准，不能用 result 的 any{}
                            audioGranted = hasAudioPermission(context)
                        }

                    // 用户可能去系统设置里手动开启权限，回到应用时要重新检查
                    LifecycleResumeEffect(Unit) {
                        audioGranted = hasAudioPermission(context)
                        onPauseOrDispose { }
                    }

                    LaunchedEffect(Unit) {
                        if (!hasAudioPermission(context)) {
                            permissionLauncher.launch(requiredPermissions())
                        }
                    }

                    // 权限就绪后同步扫描曲库：
                    // ViewModel init 里的 scanLibrary 可能跑在授权前，此时补一次，
                    // 保证「进歌单详情」时 allSongs 已就绪，不必先进曲库。
                    LaunchedEffect(audioGranted) {
                        if (audioGranted) {
                            vm.scanLibrary()
                            // 仅在「确实还不是默认播放器」时才引导一次
                            guideToDefaultPlayerIfNeeded()
                        }
                    }

                    if (!audioGranted) {
                        PermissionHint(
                            onRetry = { permissionLauncher.launch(requiredPermissions()) },
                            onOpenSettings = { context.openAppSettings() }
                        )
                        return@Surface
                    }

                    // ---------- 导航栈 ----------
                    // 关键：必须显式声明元素类型为 Screen，否则会被推断成 List<Screen.Player>
                    val navStack = remember { mutableStateListOf<Screen>(Screen.Player) }
                    val navigate: (Screen) -> Unit = { navStack.add(it) }
                    val popBack: () -> Unit = {
                        if (navStack.size > 1) navStack.removeAt(navStack.lastIndex)
                    }
                    val navigateToPlayer: () -> Unit = {
                        navStack.clear()
                        navStack.add(Screen.Player)
                    }

                    when (val screen = navStack.last()) {
                        Screen.Player -> PlayerScreen(
                            viewModel = vm,
                            onOpenLibrary = { navigate(Screen.Library) },
                            onOpenBlacklist = { navigate(Screen.Blacklist) },
                            onOpenPlaylists = { navigate(Screen.Playlists) }
                        )

                        Screen.Library -> LibraryScreen(
                            viewModel = vm,
                            onBack = popBack
                        )

                        Screen.Blacklist -> BlacklistScreen(
                            viewModel = vm,
                            onBack = popBack
                        )

                        Screen.Playlists -> PlaylistsScreen(
                            viewModel = vm,
                            onOpenPlaylist = { playlist ->
                                navigate(Screen.PlaylistDetail(playlist.id, playlist.name))
                            },
                            onBack = popBack
                        )

                        is Screen.PlaylistDetail -> PlaylistDetailScreen(
                            viewModel = vm,
                            playlistId = screen.id,
                            playlistName = screen.name,
                            onBack = popBack,
                            onNavigateToPlayer = navigateToPlayer
                        )
                    }
                }
            }
        }
    }

    /**
     * 默认播放器引导：
     *  - 先问系统「本应用是不是默认音乐播放器」，已经是就直接跳过，不打扰用户；
     *  - 系统提供「音乐播放器」角色时，弹系统的角色申请框；
     *  - 没有该角色时（AOSP 就是如此），不再自动跳转系统「默认应用」页，
     *    只提示用户可手动去设置，避免每次启动都被拽去设置。
     */
    private fun guideToDefaultPlayerIfNeeded() {
        if (DefaultPlayerHelper.isDefaultPlayer(this)) return

        val role = DefaultPlayerHelper.availableMusicRole(this)
        if (role != null) {
            val intent = DefaultPlayerHelper.createRoleRequestIntent(this, role)
            if (intent != null) {
                DefaultPlayerHelper.markPrompted(this)
                val launched = runCatching { musicRoleLauncher.launch(intent) }.isSuccess
                if (!launched) {
                    toast("请在系统「默认应用 → 音乐播放器」中手动选择本应用")
                }
                return
            }
        }

        DefaultPlayerHelper.markPrompted(this)
        toast("请在系统「默认应用 → 音乐播放器」中手动选择本应用")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun requiredPermissions(): Array<String> = buildList {
        add(audioPermission())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun hasAudioPermission(context: android.content.Context): Boolean =
        context.checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun android.content.Context.openAppSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

@Composable
private fun PermissionHint(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("需要读取音频权限才能扫描歌曲", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "如果点了没反应，说明已被标记为「不再询问」，请到系统设置里手动开启",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            ) { Text("去授权") }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("打开应用设置") }
        }
    }
}
