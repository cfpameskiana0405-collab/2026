package com.ayzen.scan.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ayzen.scan.MainViewModel
import com.ayzen.scan.R
import com.ayzen.scan.data.HistoryItem
import com.ayzen.scan.data.Source
import com.ayzen.scan.logic.Actions
import com.ayzen.scan.logic.ContentParser
import com.ayzen.scan.logic.Feedback
import com.ayzen.scan.logic.barcodeFormatName
import com.ayzen.scan.ui.components.AppBackground
import com.ayzen.scan.ui.components.Brand
import com.ayzen.scan.ui.components.GradientButton
import com.ayzen.scan.ui.components.GradientText
import com.ayzen.scan.ui.components.SoftCard
import com.ayzen.scan.ui.result.ResultSheet
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by vm.settings.collectAsStateWithLifecycleCompat(vm)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    var result by remember { mutableStateOf<HistoryItem?>(null) }
    var torch by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf(false) }
    var batchCount by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val enabled = remember { AtomicBoolean(true) }
    SideEffect { enabled.set(result == null) }

    var lastRaw by remember { mutableStateOf("") }
    var lastTime by remember { mutableStateOf(0L) }

    val scanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val onCode by rememberUpdatedState<(String, Int, Boolean) -> Unit> { raw, format, force ->
        val now = SystemClock.elapsedRealtime()
        if (force || raw != lastRaw || now - lastTime > 2500) {
            lastRaw = raw
            lastTime = now
            val type = ContentParser.detect(raw, format)
            Feedback.notify(context, settings.vibrate, settings.beep)
            if (settings.copyOnScan) Actions.copyQuiet(context, raw)
            val item = vm.record(raw, type, barcodeFormatName(format), Source.SCANNED)
            if (batch) {
                batchCount++
            } else {
                result = item
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    // Camera binding
    DisposableEffect(hasPermission, lifecycleOwner) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    analyzeFrame(proxy, scanner, enabled) { raw, format -> onCode(raw, format, false) }
                }
                p.unbindAll()
                camera = p.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Camera error", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            camera = null
        }
    }

    LaunchedEffect(torch, camera) {
        camera?.cameraControl?.enableTorch(torch)
    }

    // Scan from an image in the gallery
    val noCodeText = stringResource(R.string.scan_no_code_in_image)
    val imageErrorText = stringResource(R.string.scan_image_error)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                scanner.process(image)
                    .addOnSuccessListener { list ->
                        val b = list.firstOrNull { !(it.rawValue ?: it.displayValue).isNullOrEmpty() }
                        val raw = b?.let { it.rawValue ?: it.displayValue }
                        if (b == null || raw == null) {
                            Toast.makeText(context, noCodeText, Toast.LENGTH_SHORT).show()
                        } else {
                            onCode(raw, b.format, true)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, imageErrorText, Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, imageErrorText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val side = maxWidth * 0.72f

        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Dark scrim with a cut-out
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                drawRect(Color.Black.copy(alpha = 0.55f))
                val s = side.toPx()
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset((size.width - s) / 2f, (size.height - s) / 2f),
                    size = Size(s, s),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }

            Viewfinder(Modifier.align(Alignment.Center).size(side))

            Text(
                text = stringResource(R.string.scan_hint),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = side / 2 + 36.dp)
                    .padding(horizontal = 32.dp)
            )
        } else {
            AppBackground {
                PermissionCard(
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    },
                    onGallery = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GradientText("AYZEN SCAN", MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp))
            if (batch) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Violet.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        stringResource(R.string.scan_batch_count, batchCount),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Bottom controls
        if (hasPermission) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top
            ) {
                GlassButton(
                    icon = if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    label = stringResource(R.string.scan_torch),
                    active = torch,
                    enabled = camera?.cameraInfo?.hasFlashUnit() == true
                ) { torch = !torch }
                GlassButton(
                    icon = Icons.Rounded.PhotoLibrary,
                    label = stringResource(R.string.scan_gallery),
                    active = false
                ) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                GlassButton(
                    icon = Icons.Rounded.Layers,
                    label = stringResource(R.string.scan_batch),
                    active = batch
                ) {
                    batch = !batch
                    if (batch) batchCount = 0
                }
            }
        }
    }

    result?.let { item ->
        ResultSheet(item = item, vm = vm, onDismiss = { result = null })
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    proxy: ImageProxy,
    scanner: BarcodeScanner,
    enabled: AtomicBoolean,
    onFound: (String, Int) -> Unit
) {
    val media = proxy.image
    if (media == null || !enabled.get()) {
        proxy.close()
        return
    }
    try {
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val b = codes.firstOrNull { !(it.rawValue ?: it.displayValue).isNullOrEmpty() }
                val raw = b?.let { it.rawValue ?: it.displayValue }
                if (b != null && raw != null) onFound(raw, b.format)
            }
            .addOnCompleteListener { proxy.close() }
    } catch (e: Exception) {
        proxy.close()
    }
}

@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "line"
    )
    Box(
        modifier.drawBehind {
            val len = size.width * 0.16f
            val sw = 5.dp.toPx()
            val w = size.width
            val h = size.height
            val c = Brand.Cyan
            fun line(a: Offset, b: Offset) = drawLine(c, a, b, strokeWidth = sw, cap = StrokeCap.Round)
            // corners
            line(Offset(0f, 0f), Offset(len, 0f)); line(Offset(0f, 0f), Offset(0f, len))
            line(Offset(w, 0f), Offset(w - len, 0f)); line(Offset(w, 0f), Offset(w, len))
            line(Offset(0f, h), Offset(len, h)); line(Offset(0f, h), Offset(0f, h - len))
            line(Offset(w, h), Offset(w - len, h)); line(Offset(w, h), Offset(w, h - len))
            // animated scan line
            val y = h * progress
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Brand.Violet, Brand.Cyan, Brand.Violet, Color.Transparent),
                    startX = 0f,
                    endX = w
                ),
                start = Offset(12.dp.toPx(), y),
                end = Offset(w - 12.dp.toPx(), y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    )
}

@Composable
private fun GlassButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (active) Brand.Violet else Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(26.dp)
            )
        }
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit, onOpenSettings: () -> Unit, onGallery: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    stringResource(R.string.perm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.perm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                GradientButton(stringResource(R.string.perm_grant), Modifier.fillMaxWidth(), onClick = onGrant)
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(stringResource(R.string.perm_open_settings)) }
                OutlinedButton(
                    onClick = onGallery,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.scan_gallery))
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
