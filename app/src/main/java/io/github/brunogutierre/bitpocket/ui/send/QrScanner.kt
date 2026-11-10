package io.github.brunogutierre.bitpocket.ui.send

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.qr.QrCode
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/** Asks for the camera if needed, then reports the first QR code text it reads. */
@Composable
internal fun ScanStage(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            denied = !ok
        }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onCancel)
    PageScaffold(
        title = stringResource(R.string.scan_title),
        onBack = onCancel,
        bottomActions = { SecondaryButton(stringResource(R.string.action_cancel), onClick = onCancel) },
    ) {
        when {
            granted -> {
                Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f, fill = false)) { CameraQrReader(onScanned) }
            }

            denied -> {
                Text(stringResource(R.string.scan_denied), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Back camera preview plus frame analysis: each frame's Y (luminance) plane goes to zxing until a
 * QR code is read. The camera is unbound when this leaves the composition.
 */
@Composable
private fun CameraQrReader(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var done = false
        val analysis =
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                setAnalyzer(executor) { image ->
                    image.use {
                        val plane = it.planes[0]
                        val bytes = ByteArray(plane.buffer.remaining()).also { buffer -> plane.buffer.get(buffer) }
                        val text = QrCode.decode(bytes, it.width, it.height, plane.rowStride)
                        if (text != null && !done) {
                            done = true
                            mainExecutor.execute { onScanned(text) }
                        }
                    }
                }
            }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } finally {
            provider.unbindAll()
            executor.shutdown()
        }
    }
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
}
