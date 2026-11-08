package com.example.droidsample.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.droidsample.camera.CameraIntentsHelper

enum class CameraDashboardTab(val label: String, val icon: ImageVector) {
    CAMERAX("CameraX", Icons.Default.CameraAlt),
    CAMERA2("Camera2 Pro", Icons.Default.Tune),
    NDK("NDK Camera", Icons.Default.DeveloperBoard),
    CAMERA1("Camera1", Icons.Default.History),
    DIAGNOSTICS("Diagnostics", Icons.Default.Analytics),
    INTENTS("System Intents", Icons.Default.PhotoLibrary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(CameraDashboardTab.CAMERAX) }

    // Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Camera & Imaging Suite") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                CameraDashboardTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasCameraPermission && selectedTab != CameraDashboardTab.DIAGNOSTICS) {
                // Permission Request Screen
                CameraPermissionRationale(
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    }
                )
            } else {
                when (selectedTab) {
                    CameraDashboardTab.CAMERAX -> CameraXView()
                    CameraDashboardTab.CAMERA2 -> Camera2View()
                    CameraDashboardTab.NDK -> CameraNdkView()
                    CameraDashboardTab.CAMERA1 -> Camera1View()
                    CameraDashboardTab.DIAGNOSTICS -> CameraMetadataInspector()
                    CameraDashboardTab.INTENTS -> CameraIntentsView()
                }
            }
        }
    }
}

@Composable
fun CameraPermissionRationale(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To access CameraX, Camera2, and legacy Camera1 streaming, camera and microphone permissions are required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}

@Composable
fun CameraIntentsView() {
    val context = LocalContext.current
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var tempCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // System Image Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            lastCapturedUri = tempCaptureUri
            Toast.makeText(context, "System camera photo saved!", Toast.LENGTH_SHORT).show()
        }
    }

    // System Video Capture Launcher
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            lastCapturedUri = tempCaptureUri
            Toast.makeText(context, "System camera video saved!", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Picker Launcher (Single)
    val pickSingleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUris = listOf(uri)
            Toast.makeText(context, "Selected 1 item from Photo Picker", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Picker Launcher (Multiple)
    val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        selectedMediaUris = uris
        Toast.makeText(context, "Selected ${uris.size} items from Photo Picker", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "System Camera Intents & Photo Picker",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "System Camera App Intents", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Delegates capture to default system camera app via FileProvider content:// URI.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        try {
                            val file = CameraIntentsHelper.createImageFile(context)
                            val uri = CameraIntentsHelper.getUriForFile(context, file)
                            tempCaptureUri = uri
                            takePictureLauncher.launch(uri)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to launch camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("Capture Photo")
                    }

                    Button(onClick = {
                        try {
                            val file = CameraIntentsHelper.createVideoFile(context)
                            val uri = CameraIntentsHelper.getUriForFile(context, file)
                            tempCaptureUri = uri
                            captureVideoLauncher.launch(uri)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to launch video recorder: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("Record Video")
                    }
                }
                lastCapturedUri?.let {
                    Text("Last output: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Modern Android Photo Picker", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Standard Android 13+ Photo Picker (with backported Google Play Services support). Requires NO runtime storage permissions.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        pickSingleMediaLauncher.launch(CameraIntentsHelper.buildVisualMediaPickRequest())
                    }) {
                        Text("Pick Single Media")
                    }

                    Button(onClick = {
                        pickMultipleMediaLauncher.launch(CameraIntentsHelper.buildVisualMediaPickRequest())
                    }) {
                        Text("Pick Up to 5")
                    }
                }
                if (selectedMediaUris.isNotEmpty()) {
                    Text("Picked ${selectedMediaUris.size} URIs:", style = MaterialTheme.typography.labelMedium)
                    selectedMediaUris.forEach { uri ->
                        Text("• $uri", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
