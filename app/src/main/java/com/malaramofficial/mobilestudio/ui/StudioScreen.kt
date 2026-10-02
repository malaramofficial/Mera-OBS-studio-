package com.malaramofficial.mobilestudio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malaramofficial.mobilestudio.domain.engine.LayerOrderAction
import com.malaramofficial.mobilestudio.domain.model.audio.AudioChannelState
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform
import com.malaramofficial.mobilestudio.domain.model.state.StudioAppState
import com.malaramofficial.mobilestudio.engine.camera.CameraState
import com.malaramofficial.mobilestudio.engine.gpu.StudioRenderPipeline
import com.malaramofficial.mobilestudio.ui.components.StudioGlMonitorView
import com.malaramofficial.mobilestudio.ui.theme.StudioAmberWarn
import com.malaramofficial.mobilestudio.ui.theme.StudioBorder
import com.malaramofficial.mobilestudio.ui.theme.StudioCyan
import com.malaramofficial.mobilestudio.ui.theme.StudioGreenLive
import com.malaramofficial.mobilestudio.ui.theme.StudioObsidian
import com.malaramofficial.mobilestudio.ui.theme.StudioRed
import com.malaramofficial.mobilestudio.ui.theme.StudioSurface
import com.malaramofficial.mobilestudio.ui.theme.StudioSurfaceElevated
import com.malaramofficial.mobilestudio.ui.theme.StudioTextDisabled
import com.malaramofficial.mobilestudio.ui.theme.StudioTextPrimary
import com.malaramofficial.mobilestudio.ui.theme.StudioTextSecondary

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onStartScreenShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.appState.collectAsStateWithLifecycle()
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Dialog States
    var showCreateSceneDialog by remember { mutableStateOf(false) }
    var sceneToRename by remember { mutableStateOf<Scene?>(null) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var sourceToConfigure by remember { mutableStateOf<Pair<String, Source>?>(null) }
    var showLiveDialog by remember { mutableStateOf(false) }
    var showLiveQualityDialog by remember { mutableStateOf(false) }
    val liveBitrateKbps by viewModel.liveBitrateKbps.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        StudioTopBar(state = state)

        // Error Banner if present
        state.activeError?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(StudioRed.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .border(1.dp, StudioRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = err.userFriendlyMessage,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.dismissError() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Error",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Program and Preview Canvas Monitors
        StudioMonitorDeck(
            previewScene = state.previewScene,
            programScene = state.programScene,
            renderPipeline = viewModel.renderPipeline,
            cameraState = cameraState,
            onSwitchCamera = { viewModel.switchCameraLens(lifecycleOwner) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Middle Control Tabs: Scenes | Sources | Audio Mixer
        val tabs = listOf("Scenes", "Sources", "Audio Mixer")
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = StudioSurface,
            contentColor = StudioCyan,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier.testTag("tab_$title"),
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Active Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (selectedTabIndex) {
                0 -> ScenesListPanel(
                    scenes = state.scenes,
                    previewSceneId = state.previewSceneId,
                    programSceneId = state.programSceneId,
                    onSelectPreview = { viewModel.selectPreviewScene(it) },
                    onSelectProgram = { viewModel.selectProgramScene(it) },
                    onCreateSceneClick = { showCreateSceneDialog = true },
                    onRenameSceneClick = { sceneToRename = it },
                    onDuplicateScene = { viewModel.duplicateScene(it) },
                    onDeleteScene = { viewModel.deleteScene(it) }
                )
                1 -> SourcesListPanel(
                    activeScene = state.previewScene,
                    cameraState = cameraState,
                    onAddSourceClick = { showAddSourceDialog = true },
                    onToggleVisibility = { sceneId, sourceId ->
                        viewModel.toggleSourceVisibility(sceneId, sourceId)
                    },
                    onToggleLock = { sceneId, sourceId ->
                        viewModel.toggleSourceLock(sceneId, sourceId)
                    },
                    onReorderSource = { sceneId, sourceId, action ->
                        viewModel.reorderSource(sceneId, sourceId, action)
                    },
                    onDuplicateSource = { sceneId, sourceId ->
                        viewModel.duplicateSource(sceneId, sourceId)
                    },
                    onDeleteSource = { sceneId, sourceId ->
                        viewModel.removeSource(sceneId, sourceId)
                    },
                    onConfigureSource = { sceneId, source ->
                        sourceToConfigure = Pair(sceneId, source)
                    }
                )
                2 -> AudioMixerPanel(
                    channels = state.audioState.channels
                )
            }
        }

        // Bottom Action Control Deck: CUT | TRANSITION | REC | LIVE
        StudioBottomControlDeck(
            onCut = { viewModel.executeCut() },
            onTransition = { viewModel.executeTransition() },
            onStartScreenShare = {
                viewModel.prepareScreenShare()
                onStartScreenShare()
            },
            isLive = state.isBroadcastingLive,
            onLive = { showLiveDialog = true },
            onStopLive = { viewModel.stopLive() },
            liveBitrateKbps = liveBitrateKbps,
            onQualityClick = { showLiveQualityDialog = true },
            isRecording = state.isRecordingToFile,
            onRecord = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecording() },
            onPauseRecording = { viewModel.pauseRecording() },
            onResumeRecording = { viewModel.resumeRecording() }
        )
    }

    if (showLiveDialog) {
        LiveStreamKeyDialog(
            savedStreamKey = viewModel.getSavedStreamKey(),
            onDismiss = { showLiveDialog = false },
            onStart = { key, bitrateKbps ->
                showLiveDialog = false
                viewModel.startLive(key, bitrateKbps)
            },
            onClear = { viewModel.clearSavedStreamKey() }
        )
    }

    if (showLiveQualityDialog) {
        LiveQualityDialog(
            currentBitrateKbps = liveBitrateKbps,
            isLive = state.isBroadcastingLive,
            onDismiss = { showLiveQualityDialog = false },
            onApply = { bitrateKbps ->
                viewModel.setLiveBitrateKbps(bitrateKbps)
                showLiveQualityDialog = false
            }
        )
    }

    // Dialogs
    if (showCreateSceneDialog) {
        CreateSceneDialog(
            onDismiss = { showCreateSceneDialog = false },
            onConfirm = { name, sourceType ->
                viewModel.createScene(name, sourceType)
                showCreateSceneDialog = false
            }
        )
    }

    sceneToRename?.let { scene ->
        RenameSceneDialog(
            currentName = scene.name,
            onDismiss = { sceneToRename = null },
            onConfirm = { newName ->
                viewModel.renameScene(scene.id, newName)
                sceneToRename = null
            }
        )
    }

    if (showAddSourceDialog && state.previewScene != null) {
        AddSourceDialog(
            sceneId = state.previewScene!!.id,
            onDismiss = { showAddSourceDialog = false },
            onAdd = { sceneId, name, type, config ->
                viewModel.addSource(sceneId = sceneId, name = name, type = type, config = config)
                showAddSourceDialog = false
            }
        )
    }

    sourceToConfigure?.let { (sceneId, source) ->
        SourceTransformDialog(
            source = source,
            onDismiss = { sourceToConfigure = null },
            onApply = { newTransform, newCrop, newOpacity ->
                viewModel.updateTransform(sceneId, source.id, newTransform)
                viewModel.updateCrop(sceneId, source.id, newCrop)
                viewModel.updateOpacity(sceneId, source.id, newOpacity)
                sourceToConfigure = null
            }
        )
    }
}

@Composable
private fun StudioTopBar(state: StudioAppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Malaram Mobile Studio",
                style = MaterialTheme.typography.titleMedium,
                color = StudioTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Vertical Live • 9:16 Output",
                style = MaterialTheme.typography.bodySmall,
                color = StudioCyan,
                fontSize = 11.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusBadge(
                label = "REC",
                isActive = state.isRecordingToFile,
                activeColor = StudioRed,
                inactiveColor = StudioTextDisabled
            )
            StatusBadge(
                label = "LIVE",
                isActive = state.isBroadcastingLive,
                activeColor = StudioGreenLive,
                inactiveColor = StudioTextDisabled
            )
            Text(
                text = "9:16 • 1080×1920",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = StudioTextSecondary,
                modifier = Modifier
                    .background(StudioSurfaceElevated, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color
) {
    Row(
        modifier = Modifier
            .background(
                if (isActive) activeColor.copy(alpha = 0.2f) else StudioSurfaceElevated,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (isActive) activeColor else StudioBorder,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (isActive) activeColor else inactiveColor, CircleShape)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) activeColor else inactiveColor
        )
    }
}

@Composable
private fun StudioMonitorDeck(
    previewScene: Scene?,
    programScene: Scene?,
    renderPipeline: StudioRenderPipeline,
    cameraState: CameraState,
    onSwitchCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .testTag("program_canvas"),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioRed))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Real OpenGL Hardware Compositor SurfaceView
                StudioGlMonitorView(
                    renderPipeline = renderPipeline,
                    modifier = Modifier.fillMaxSize()
                )

                // Live Program Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(StudioRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROGRAM (LIVE)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Switch Camera Lens Button (shown when camera is active)
                if (cameraState.isActive) {
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(StudioSurface.copy(alpha = 0.75f), CircleShape)
                            .size(36.dp)
                            .testTag("btn_switch_camera")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch Camera Lens",
                            tint = StudioCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Scene Name Overlay (only if empty or unobtrusive)
                if (programScene == null || programScene.sources.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = programScene?.name ?: "No Program Scene",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StudioTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Empty Canvas",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(StudioGreenLive, CircleShape)
                )
                Text(
                    text = "PREVIEW STAGE:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioGreenLive
                )
                Text(
                    text = previewScene?.name ?: "None Selected",
                    fontSize = 12.sp,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${previewScene?.sources?.size ?: 0} Sources",
                fontSize = 11.sp,
                color = StudioTextSecondary
            )
        }
    }
}

@Composable
private fun ScenesListPanel(
    scenes: List<Scene>,
    previewSceneId: String?,
    programSceneId: String?,
    onSelectPreview: (String) -> Unit,
    onSelectProgram: (String) -> Unit,
    onCreateSceneClick: () -> Unit,
    onRenameSceneClick: (Scene) -> Unit,
    onDuplicateScene: (String) -> Unit,
    onDeleteScene: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scenes (${scenes.size})",
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary
            )
            Button(
                onClick = onCreateSceneClick,
                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, StudioCyan),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_create_scene")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Scene", fontSize = 12.sp, color = StudioCyan)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(scenes, key = { it.id }) { scene ->
                val isPreview = scene.id == previewSceneId
                val isProgram = scene.id == programSceneId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPreview(scene.id) }
                        .testTag("scene_item_${scene.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isProgram -> StudioRed.copy(alpha = 0.15f)
                            isPreview -> StudioSurfaceElevated
                            else -> StudioSurface
                        }
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            when {
                                isProgram -> StudioRed
                                isPreview -> StudioGreenLive
                                else -> StudioBorder
                            }
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = scene.name,
                                fontWeight = FontWeight.SemiBold,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = "${scene.sources.size} layer(s)",
                                fontSize = 11.sp,
                                color = StudioTextSecondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isProgram) {
                                Text(
                                    text = "PROGRAM",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioRed,
                                    modifier = Modifier
                                        .background(StudioRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            if (isPreview) {
                                Text(
                                    text = "PREVIEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioGreenLive,
                                    modifier = Modifier
                                        .background(StudioGreenLive.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            // Duplicate Button
                            IconButton(
                                onClick = { onDuplicateScene(scene.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate Scene",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Rename Button
                            IconButton(
                                onClick = { onRenameSceneClick(scene) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename Scene",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Delete Button
                            IconButton(
                                onClick = { onDeleteScene(scene.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Scene",
                                    tint = StudioAmberWarn,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesListPanel(
    activeScene: Scene?,
    cameraState: CameraState,
    onAddSourceClick: () -> Unit,
    onToggleVisibility: (String, String) -> Unit,
    onToggleLock: (String, String) -> Unit,
    onReorderSource: (String, String, LayerOrderAction) -> Unit,
    onDuplicateSource: (String, String) -> Unit,
    onDeleteSource: (String, String) -> Unit,
    onConfigureSource: (String, Source) -> Unit
) {
    if (activeScene == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No active scene selected.", color = StudioTextSecondary)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Layers in ${activeScene.name} (${activeScene.sources.size})",
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary
            )
            Button(
                onClick = onAddSourceClick,
                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, StudioCyan),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_add_source")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Layer", fontSize = 12.sp, color = StudioCyan)
            }
        }

        if (activeScene.sources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No layers in this scene. Tap '+ Add Layer' above.",
                    color = StudioTextSecondary,
                    fontSize = 13.sp
                )
            }
            return
        }

        // Show layers top-to-bottom (highest Z at the top)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(activeScene.sortedSources.reversed(), key = { it.id }) { source ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = getSourceIcon(source.type),
                                    contentDescription = null,
                                    tint = StudioCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = source.name,
                                        fontWeight = FontWeight.Medium,
                                        color = if (source.visible) StudioTextPrimary else StudioTextDisabled
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${source.type.name} • Z:${source.zIndex}",
                                            fontSize = 11.sp,
                                            color = StudioTextSecondary
                                        )
                                        if (source.type == SourceType.CAMERA) {
                                            val camText = when (cameraState) {
                                                is CameraState.Active -> "Live (GL Ingest • ${cameraState.lensFacing.name})"
                                                is CameraState.Starting -> "Starting Camera..."
                                                else -> "Camera Engine Ready"
                                            }
                                            Text(
                                                text = camText,
                                                fontSize = 9.sp,
                                                color = if (cameraState.isActive) StudioGreenLive else StudioCyan
                                            )
                                        } else if (source.type == SourceType.SCREEN) {
                                            Text(
                                                text = "Screen capture ready",
                                                fontSize = 9.sp,
                                                color = StudioAmberWarn
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Visibility Toggle
                                IconButton(
                                    onClick = { onToggleVisibility(activeScene.id, source.id) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (source.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Visibility",
                                        tint = if (source.visible) StudioCyan else StudioTextDisabled,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Lock Toggle
                                IconButton(
                                    onClick = { onToggleLock(activeScene.id, source.id) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (source.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Toggle Lock",
                                        tint = if (source.locked) StudioAmberWarn else StudioTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Transform & Crop Configure
                                IconButton(
                                    onClick = { onConfigureSource(activeScene.id, source) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Configure Transform",
                                        tint = StudioCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Layer Reordering Controls Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onReorderSource(activeScene.id, source.id, LayerOrderAction.MOVE_UP) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Layer Up",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onReorderSource(activeScene.id, source.id, LayerOrderAction.MOVE_DOWN) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Layer Down",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onReorderSource(activeScene.id, source.id, LayerOrderAction.BRING_TO_FRONT) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignTop,
                                    contentDescription = "Bring To Top",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onReorderSource(activeScene.id, source.id, LayerOrderAction.SEND_TO_BACK) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignBottom,
                                    contentDescription = "Send To Bottom",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDuplicateSource(activeScene.id, source.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate Layer",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSource(activeScene.id, source.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Layer",
                                    tint = StudioAmberWarn,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getSourceIcon(type: SourceType): ImageVector = when (type) {
    SourceType.CAMERA -> Icons.Default.CameraAlt
    SourceType.SCREEN -> Icons.AutoMirrored.Filled.ScreenShare
    SourceType.IMAGE -> Icons.Default.Image
    SourceType.TEXT -> Icons.Default.TextFields
    SourceType.MEDIA -> Icons.Default.Movie
    SourceType.BROWSER -> Icons.Default.Language
}

@Composable
private fun AudioMixerPanel(
    channels: List<AudioChannelState>
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Master Audio Mixer (Standby - Phase 7)",
            style = MaterialTheme.typography.titleSmall,
            color = StudioTextPrimary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(channels, key = { it.id }) { channel ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (channel.type) {
                                        com.malaramofficial.mobilestudio.domain.model.audio.AudioSourceType.MICROPHONE -> Icons.Default.Mic
                                        else -> Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = null,
                                    tint = StudioCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = channel.name,
                                    fontWeight = FontWeight.Medium,
                                    color = StudioTextPrimary
                                )
                            }

                            Text(
                                text = "0.0 dBFS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = StudioTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = channel.volumeGain,
                            onValueChange = {},
                            valueRange = 0f..2f,
                            enabled = false,
                            colors = SliderDefaults.colors(
                                disabledThumbColor = StudioCyan,
                                disabledActiveTrackColor = StudioBorder,
                                disabledInactiveTrackColor = StudioBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioBottomControlDeck(
    onCut: () -> Unit,
    onTransition: () -> Unit,
    onStartScreenShare: () -> Unit,
    isLive: Boolean,
    onLive: () -> Unit,
    onStopLive: () -> Unit,
    liveBitrateKbps: Int,
    onQualityClick: () -> Unit,
    isRecording: Boolean,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StudioBorder)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("STUDIO CONTROLS", color = StudioTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onStartScreenShare, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioSurface, contentColor = StudioCyan), border = BorderStroke(1.dp, StudioCyan)) {
                    Icon(Icons.AutoMirrored.Filled.ScreenShare, contentDescription = "Screen Share")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SCREEN", fontWeight = FontWeight.Bold)
                }
                Button(onClick = onCut, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioRed)) {
                    Text("CUT", fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(onClick = onTransition, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioSurface, contentColor = StudioCyan), border = BorderStroke(1.dp, StudioCyan)) {
                    Text("TRANSITION", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Text("RECORDING", color = StudioTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRecord, enabled = !isRecording, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioGreenLive, contentColor = Color.Black)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Recording")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("START", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onPauseRecording, enabled = isRecording, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, StudioCyan), colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan)) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause Recording")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PAUSE", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onStopRecording, enabled = isRecording, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, StudioRed), colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioRed)) {
                    Icon(Icons.Default.Stop, contentDescription = "End Recording")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("END", fontWeight = FontWeight.Bold)
                }
            }

            Text("LIVE STREAM", color = StudioTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLive, enabled = !isLive, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioGreenLive, contentColor = Color.Black)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Live")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("START LIVE", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onStopLive, enabled = isLive, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, StudioRed), colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioRed)) {
                    Icon(Icons.Default.Stop, contentDescription = "End Live")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("END LIVE", fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VIDEO: ${liveBitrateKbps} kbps",
                    color = if (isLive) StudioGreenLive else StudioTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onQualityClick,
                    modifier = Modifier.height(42.dp),
                    border = BorderStroke(1.dp, StudioCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyan)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Stream Quality", modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isLive) "ADJUST LIVE" else "QUALITY")
                }
            }
        }
    }
}

@Composable
private fun LiveStreamKeyDialog(
    savedStreamKey: String,
    onDismiss: () -> Unit,
    onStart: (String, Int) -> Unit,
    onClear: () -> Unit
) {
    var streamKey by remember { mutableStateOf(savedStreamKey) }
    var editing by remember { mutableStateOf(savedStreamKey.isBlank()) }
    var selectedBitrate by remember { mutableIntStateOf(6000) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start YouTube Vertical Live", color = StudioTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Output: 1080×1920 • 9:16 • 30 FPS",
                    fontSize = 12.sp,
                    color = StudioCyan
                )
                Text("Initial video bitrate", fontSize = 12.sp, color = StudioTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2500, 4000, 6000, 8000).forEach { bitrate ->
                        FilterChipLike(
                            label = "${bitrate / 1000f}M",
                            selected = selectedBitrate == bitrate,
                            onClick = { selectedBitrate = bitrate }
                        )
                    }
                }
                Text(
                    if (savedStreamKey.isBlank()) "Enter your stream key once. It will be saved securely on this device." else "Saved stream key found. Use it directly or edit it.",
                    fontSize = 12.sp,
                    color = StudioTextSecondary
                )
                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("YouTube Stream Key") },
                    singleLine = true,
                    enabled = editing,
                    visualTransformation = if (editing) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = true }, enabled = !editing && streamKey.isNotBlank()) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EDIT")
                    }
                    OutlinedButton(onClick = { streamKey = ""; editing = true; onClear() }, enabled = streamKey.isNotBlank()) { Text("CLEAR") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (streamKey.isNotBlank()) onStart(streamKey, selectedBitrate) },
                enabled = streamKey.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
            ) {
                Text(if (savedStreamKey.isBlank() || editing) "SAVE & GO LIVE" else "GO LIVE", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioTextSecondary)
            }
        },
        containerColor = StudioSurfaceElevated
    )
}

// Dialog Implementations

@Composable
private fun CreateSceneDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, SourceType) -> Unit
) {
    var sceneName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SourceType.CAMERA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Scene", color = StudioTextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Name the scene and choose what it should display.", fontSize = 13.sp, color = StudioTextSecondary)
                OutlinedTextField(
                    value = sceneName,
                    onValueChange = { sceneName = it },
                    placeholder = { Text("e.g. Gameplay + Facecam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_scene_name")
                )
                Text("Display / Source Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StudioCyan)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(SourceType.entries) { type ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedType = type },
                            colors = CardDefaults.cardColors(containerColor = if (selectedType == type) StudioCyan.copy(alpha = 0.18f) else StudioSurface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (selectedType == type) StudioCyan else StudioBorder)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(getSourceIcon(type), contentDescription = null, tint = if (selectedType == type) StudioCyan else StudioTextSecondary, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(type.name, fontWeight = FontWeight.SemiBold, color = if (selectedType == type) StudioCyan else StudioTextPrimary)
                                    Text("This source will be added to the new scene.", fontSize = 10.sp, color = StudioTextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (sceneName.isNotBlank()) onConfirm(sceneName.trim(), selectedType) }, enabled = sceneName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = StudioCyan), modifier = Modifier.testTag("btn_confirm_create_scene")) {
                Text("Create Scene", color = Color.Black)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = StudioTextSecondary) } },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
private fun RenameSceneDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Scene", color = StudioTextPrimary) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.isNotBlank()) onConfirm(newName) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioTextSecondary)
            }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
private fun AddSourceDialog(
    sceneId: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, SourceType, com.malaramofficial.mobilestudio.domain.model.source.SourceConfig?) -> Unit
) {
    var sourceName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SourceType.CAMERA) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var textValue by remember { mutableStateOf("Live Studio Stream") }
    var browserUrl by remember { mutableStateOf("https://example.com") }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not expose persistable permissions; the current session still works.
            }
        }
        selectedUri = uri
    }

    fun defaultName(type: SourceType) = when (type) {
        SourceType.CAMERA -> "Camera Layer"
        SourceType.SCREEN -> "Screen Capture"
        SourceType.IMAGE -> "Logo / Image"
        SourceType.TEXT -> "Text Overlay"
        SourceType.MEDIA -> "Video / Media"
        SourceType.BROWSER -> "Browser Overlay"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Layer to Scene", color = StudioTextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = { sourceName = it },
                    placeholder = { Text("Layer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_source_name")
                )
                Text("Select what this layer displays:", fontSize = 12.sp, color = StudioTextSecondary)

                SourceType.entries.forEach { type ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedType = type
                            if (sourceName.isBlank()) sourceName = defaultName(type)
                            selectedUri = null
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedType == type) StudioCyan.copy(alpha = 0.2f) else StudioSurface
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (selectedType == type) StudioCyan else StudioBorder
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                getSourceIcon(type),
                                contentDescription = null,
                                tint = if (selectedType == type) StudioCyan else StudioTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (type) {
                                        SourceType.CAMERA -> "Camera"
                                        SourceType.SCREEN -> "Screen"
                                        SourceType.IMAGE -> "Image"
                                        SourceType.TEXT -> "Text"
                                        SourceType.MEDIA -> "Video / Media"
                                        SourceType.BROWSER -> "Browser"
                                    },
                                    fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedType == type) StudioCyan else StudioTextPrimary
                                )
                                Text(
                                    text = when (type) {
                                        SourceType.CAMERA -> "Live phone camera"
                                        SourceType.SCREEN -> "Phone screen capture"
                                        SourceType.IMAGE -> "Gallery image or logo"
                                        SourceType.TEXT -> "Rendered text overlay"
                                        SourceType.MEDIA -> "Video from gallery"
                                        SourceType.BROWSER -> "Web URL overlay"
                                    },
                                    fontSize = 10.sp,
                                    color = StudioTextSecondary
                                )
                            }
                        }
                    }
                }

                when (selectedType) {
                    SourceType.IMAGE -> {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, StudioCyan)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = StudioCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedUri == null) "CHOOSE IMAGE FROM GALLERY" else "CHANGE IMAGE",
                                color = StudioCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (selectedUri != null) Text("Image selected — ready to add", fontSize = 11.sp, color = StudioCyan)
                    }
                    SourceType.MEDIA -> {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth().testTag("btn_pick_media"),
                            border = BorderStroke(1.dp, StudioCyan)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = StudioCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedUri == null) "CHOOSE VIDEO FROM GALLERY" else "CHANGE VIDEO",
                                color = StudioCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (selectedUri != null) Text("Video selected — ready to add", fontSize = 11.sp, color = StudioCyan)
                    }
                    SourceType.TEXT -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("Text to display") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    SourceType.BROWSER -> {
                        OutlinedTextField(
                            value = browserUrl,
                            onValueChange = { browserUrl = it },
                            label = { Text("Web URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            val canAdd = when (selectedType) {
                SourceType.IMAGE, SourceType.MEDIA -> selectedUri != null
                SourceType.TEXT -> textValue.isNotBlank()
                SourceType.BROWSER -> browserUrl.isNotBlank()
                else -> true
            }
            Button(
                onClick = {
                    val name = sourceName.ifBlank { defaultName(selectedType) }
                    val config = when (selectedType) {
                        SourceType.IMAGE -> selectedUri?.let { com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Image(uri = it.toString()) }
                        SourceType.MEDIA -> selectedUri?.let { com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Media(uri = it.toString()) }
                        SourceType.TEXT -> com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Text(text = textValue)
                        SourceType.BROWSER -> com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Browser(url = browserUrl.trim())
                        SourceType.CAMERA, SourceType.SCREEN -> null
                    }
                    onAdd(sceneId, name, selectedType, config)
                },
                enabled = canAdd,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                modifier = Modifier.testTag("btn_confirm_add_source")
            ) {
                Text("Add Layer", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = StudioTextSecondary) }
        },
        containerColor = StudioSurfaceElevated
    )
}

@Composable
private fun SourceTransformDialog(
    source: Source,
    onDismiss: () -> Unit,
    onApply: (Transform, Crop, Float) -> Unit
) {
    var posX by remember { mutableStateOf(source.transform.x.toString()) }
    var posY by remember { mutableStateOf(source.transform.y.toString()) }
    var width by remember { mutableStateOf(source.transform.width.toString()) }
    var height by remember { mutableStateOf(source.transform.height.toString()) }
    var rotation by remember { mutableStateOf(source.transform.rotation.toString()) }
    var opacity by remember { mutableStateOf(source.opacity) }
    var cropLeft by remember { mutableStateOf(source.crop.left.toString()) }
    var cropTop by remember { mutableStateOf(source.crop.top.toString()) }
    var cropRight by remember { mutableStateOf(source.crop.right.toString()) }
    var cropBottom by remember { mutableStateOf(source.crop.bottom.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transform: ${source.name}", color = StudioTextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Position & Dimensions", fontSize = 12.sp, color = StudioCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = posX,
                        onValueChange = { posX = it },
                        label = { Text("X") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = posY,
                        onValueChange = { posY = it },
                        label = { Text("Y") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("Width") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rotation,
                        onValueChange = { rotation = it },
                        label = { Text("Rotation (°)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Opacity: ${(opacity * 100).toInt()}%", fontSize = 12.sp, color = StudioCyan)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan)
                )

                Text("Crop Offsets (px)", fontSize = 12.sp, color = StudioCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cropLeft,
                        onValueChange = { cropLeft = it },
                        label = { Text("Left") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cropRight,
                        onValueChange = { cropRight = it },
                        label = { Text("Right") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cropTop,
                        onValueChange = { cropTop = it },
                        label = { Text("Top") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cropBottom,
                        onValueChange = { cropBottom = it },
                        label = { Text("Bottom") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newX = posX.toFloatOrNull() ?: source.transform.x
                    val newY = posY.toFloatOrNull() ?: source.transform.y
                    val newW = (width.toFloatOrNull() ?: source.transform.width).coerceAtLeast(10f)
                    val newH = (height.toFloatOrNull() ?: source.transform.height).coerceAtLeast(10f)
                    val newRot = rotation.toFloatOrNull() ?: source.transform.rotation

                    val newCrop = Crop(
                        left = (cropLeft.toFloatOrNull() ?: 0f).coerceAtLeast(0f),
                        top = (cropTop.toFloatOrNull() ?: 0f).coerceAtLeast(0f),
                        right = (cropRight.toFloatOrNull() ?: 0f).coerceAtLeast(0f),
                        bottom = (cropBottom.toFloatOrNull() ?: 0f).coerceAtLeast(0f)
                    )

                    val newTransform = source.transform.copy(
                        x = newX,
                        y = newY,
                        width = newW,
                        height = newH,
                        rotation = newRot
                    )

                    onApply(newTransform, newCrop, opacity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
            ) {
                Text("Apply", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioTextSecondary)
            }
        },
        containerColor = StudioSurfaceElevated
    )
}
