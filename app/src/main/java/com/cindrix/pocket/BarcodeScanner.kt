package com.cindrix.pocket

import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** Reads barcodes from camera frames. Everything happens on the phone. */
class BarcodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()
    private val main = Handler(Looper.getMainLooper())
    private var lastText = ""
    private var lastTime = 0L

    init {
        val hints = mapOf<DecodeHintType, Any>(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
            ),
            DecodeHintType.TRY_HARDER to true,
        )
        reader.setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        try {
            val w = image.width
            val h = image.height
            val plane = image.planes[0]
            val buf = plane.buffer
            val stride = plane.rowStride

            // Copy the brightness plane, dropping any padding at the end of each row.
            val src = ByteArray(w * h)
            for (y in 0 until h) {
                buf.position(y * stride)
                buf.get(src, y * w, w)
            }

            val turned = rotate(src, w, h, image.imageInfo.rotationDegrees)
            val data = turned.first
            val dw = turned.second
            val dh = turned.third

            val source = PlanarYUVLuminanceSource(data, dw, dh, 0, 0, dw, dh, false)
            val text: String? = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            } catch (e: ReaderException) {
                null
            } finally {
                reader.reset()
            }

            if (text != null) {
                val now = System.currentTimeMillis()
                if (text != lastText || now - lastTime > 2500) {
                    lastText = text
                    lastTime = now
                    main.post { onCode(text) }
                }
            }
        } catch (e: Exception) {
            // A bad frame is not worth crashing for. The next one will be tried.
        } finally {
            image.close()
        }
    }

    private fun rotate(src: ByteArray, w: Int, h: Int, degrees: Int): Triple<ByteArray, Int, Int> {
        return when (degrees) {
            90 -> {
                val dst = ByteArray(src.size)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        dst[x * h + (h - 1 - y)] = src[y * w + x]
                    }
                }
                Triple(dst, h, w)
            }
            180 -> {
                val dst = ByteArray(src.size)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        dst[(h - 1 - y) * w + (w - 1 - x)] = src[y * w + x]
                    }
                }
                Triple(dst, w, h)
            }
            270 -> {
                val dst = ByteArray(src.size)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        dst[(w - 1 - x) * h + y] = src[y * w + x]
                    }
                }
                Triple(dst, h, w)
            }
            else -> Triple(src, w, h)
        }
    }
}

/** Live camera preview that reports each barcode it reads. */
@Composable
fun CameraView(
    torchOn: Boolean,
    onCode: (String) -> Unit,
    onTorchSupport: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnTorchSupport by rememberUpdatedState(onTorchSupport)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val selector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, BarcodeAnalyzer { code -> currentOnCode(code) })

                provider.unbindAll()
                val bound = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                camera = bound
                currentOnTorchSupport(bound.cameraInfo.hasFlashUnit())
            } catch (e: Exception) {
                currentOnTorchSupport(false)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (e: Exception) {
                // Nothing to release.
            }
            executor.shutdown()
        }
    }

    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
