package com.bamah.reelstudio

import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class RecordingActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var front = true
    private var previewView: PreviewView? = null
    private var words: List<WordItem> = emptyList()
    private var config = ChallengeConfig()
    private var onStatus: ((String) -> Unit)? = null
    private var onRecordingStarted: ((Long) -> Unit)? = null
    private var onRecordingStopped: (() -> Unit)? = null
    private var exporting = false
    private var activeTransformer: Transformer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this, this)
        try {
            val raw = intent.getStringExtra("config") ?: "{}"
            val (c, w) = ChallengeJson.decode(raw)
            config = c ?: ChallengeConfig()
            words = w
        } catch (_: Exception) { finish(); return }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (4 or 1024 or 4096 or 512)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFEFA17E), background = Color.Black)) {
                RecordingScreen()
            }
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.US
    }

    private fun bindCamera(view: PreviewView) {
        previewView = view
        // PreviewView already presents the front camera as a mirror. Leave its
        // transform untouched so the preview shows the same orientation as the export.
        view.scaleX = 1f
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }
                val quality = QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                val recorder = Recorder.Builder().setQualitySelector(quality).build()
                // Mirror only front-camera recordings. This is applied to the captured
                // video frames before the MP4 export is rendered, while the card overlay
                // remains readable and is not mirrored.
                videoCapture = VideoCapture.Builder(recorder)
                    .setMirrorMode(
                        if (front) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY
                        else MirrorMode.MIRROR_MODE_OFF
                    )
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    this as LifecycleOwner,
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )
                onStatus?.invoke("Camera ready · portrait 9:16 export")
            } catch (e: Exception) {
                onStatus?.invoke("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        front = !front
        previewView?.let { bindCamera(it) }
    }

    private fun startRecording() {
        if (exporting) return
        val capture = videoCapture ?: run {
            onStatus?.invoke("Camera is starting. Try again.")
            onRecordingStopped?.invoke()
            return
        }
        val source = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "source-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(source).build()
        try {
            recording = capture.output.prepareRecording(this, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            onStatus?.invoke("Recording challenge…")
                            onRecordingStarted?.invoke(SystemClock.elapsedRealtime())
                        }
                        is VideoRecordEvent.Finalize -> {
                            recording = null
                            onRecordingStopped?.invoke()
                            if (event.hasError()) {
                                source.delete()
                                onStatus?.invoke("Recording failed: ${event.error}")
                            } else {
                                renderInstagramExport(source)
                            }
                        }
                        is VideoRecordEvent.Status -> Unit
                        is VideoRecordEvent.Pause -> Unit
                        is VideoRecordEvent.Resume -> Unit
                    }
                }
        } catch (e: Exception) {
            recording = null
            source.delete()
            onRecordingStopped?.invoke()
            onStatus?.invoke("Could not start recording: ${e.message}")
        }
    }

    private fun stopRecording() {
        val current = recording
        recording = null
        current?.stop()
    }

    private fun say(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-${System.currentTimeMillis()}")
        }
    }

    /**
     * Re-encodes to H.264/AAC, center-crops to 9:16 at 720x1280, and composites
     * a timestamp-driven CanvasOverlay. The challenge card is therefore pixels
     * in the final MP4, not just a UI element over the camera preview.
     */
    private fun renderInstagramExport(source: File) {
        if (exporting) return
        exporting = true
        onStatus?.invoke("Rendering 9:16 Reel · embedding challenge cards…")
        val output = File(cacheDir, "BaMah-Reel-${System.currentTimeMillis()}.mp4")
        val cardOverlay = ChallengeCanvasOverlay(config, words)
        val videoEffects = listOf(
            Presentation.createForWidthAndHeight(
                720, 1280, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            ),
            OverlayEffect(listOf(cardOverlay))
        )
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(source.toURI().toString()))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        try {
            val transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: androidx.media3.transformer.ExportResult
                    ) {
                        // Gallery copy can be large; keep it off the UI thread on the Note10+.
                        cameraExecutor.execute {
                            val saved = saveToMovies(output)
                            source.delete()
                            output.delete()
                            runOnUiThread {
                                exporting = false
                                onStatus?.invoke(
                                    if (saved) "Ready · saved to Movies/BaMah Reel Studio · MP4 · 9:16"
                                    else "Render finished, but saving to Gallery failed."
                                )
                            }
                        }
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException: androidx.media3.transformer.ExportException
                    ) {
                        exporting = false
                        source.delete()
                        output.delete()
                        onStatus?.invoke("Reel rendering failed: ${exportException.message}")
                    }
                })
                .build()
            activeTransformer = transformer
            transformer.start(edited, output.absolutePath)
        } catch (e: Exception) {
            exporting = false
            source.delete()
            output.delete()
            onStatus?.invoke("Could not render Reel: ${e.message}")
        }
    }

    private fun saveToMovies(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            val name = "BaMah-Reel-${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BaMah Reel Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not open Gallery destination")
                val completed = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, completed, null, null)
                true
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        } catch (_: Exception) {
            false
        }
    }

    @Composable
    private fun RecordingScreen() {
        val ctx = LocalContext.current
        var index by remember { mutableIntStateOf(0) }
        var phase by remember { mutableStateOf("ready") }
        var remaining by remember { mutableIntStateOf(0) }
        var questionProgress by remember { mutableFloatStateOf(0f) }
        var isRecording by remember { mutableStateOf(false) }
        var isStarting by remember { mutableStateOf(false) }
        var recordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
        var status by remember { mutableStateOf("Hold your phone vertically. Your exported video will be 9:16.") }
        var stopRequested by remember { mutableStateOf(false) }
        onStatus = { status = it }
        onRecordingStarted = { startedAt ->
            isStarting = false
            recordingStartedAtMs = startedAt
            isRecording = true
        }
        onRecordingStopped = {
            isStarting = false
            isRecording = false
            recordingStartedAtMs = null
        }

        DisposableEffect(Unit) {
            onDispose {
                stopRecording()
                activeTransformer?.cancel()
            }
        }

        LaunchedEffect(recordingStartedAtMs) {
            val startedAt = recordingStartedAtMs ?: return@LaunchedEffect
            stopRequested = false
            val totalChallengeMs = words.size * (config.answerSeconds + config.revealSeconds) * 1000L
            for (i in words.indices) {
                if (stopRequested || recordingStartedAtMs != startedAt) break
                index = i
                phase = "question"
                remaining = config.answerSeconds
                questionProgress = if (config.answerSeconds == 0) 1f else 0f
                val qEnd = startedAt + i * (config.answerSeconds + config.revealSeconds) * 1000L +
                    config.answerSeconds * 1000L
                while (SystemClock.elapsedRealtime() < qEnd && !stopRequested) {
                    val now = SystemClock.elapsedRealtime()
                    remaining = ((qEnd - now + 999) / 1000).toInt()
                    questionProgress = ((now - (qEnd - config.answerSeconds * 1000L)).toFloat() /
                        (config.answerSeconds.coerceAtLeast(1) * 1000L)).coerceIn(0f, 1f)
                    delay(50)
                }
                if (stopRequested) break
                phase = "answer"
                remaining = config.revealSeconds
                if (config.revealSeconds > 0 && (config.audioMode == "both" || config.audioMode == "auto")) say(words[i].word)
                val revealEnd = startedAt + (i + 1) * (config.answerSeconds + config.revealSeconds) * 1000L
                while (SystemClock.elapsedRealtime() < revealEnd && !stopRequested) {
                    remaining = ((revealEnd - SystemClock.elapsedRealtime() + 999) / 1000).toInt()
                    delay(50)
                }
            }
            if (!stopRequested && recordingStartedAtMs == startedAt) {
                phase = "done"
                delay(ChallengeTimeline.COMPLETE_HOLD_MS)
            }
            if (recordingStartedAtMs == startedAt) stopRecording()
            phase = "ready"
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = {
                    FrameLayout(it).apply {
                        addView(
                            PreviewView(it).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                previewView = this
                                bindCamera(this)
                            },
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (phase != "ready" && phase != "done" && words.getOrNull(index) != null) {
                val w = words[index]
                val answerPhase = phase == "answer"
                ChallengeCardPreview(
                    config = config,
                    word = w,
                    index = index,
                    total = words.size,
                    answerPhase = answerPhase,
                    questionProgress = questionProgress,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp)
                )
            }
            if (phase == "done") {
                Text("CHALLENGE COMPLETE", Modifier.align(Alignment.Center), color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xAA000000)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(status, color = Color.White, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { if (!isRecording && !exporting) switchCamera() },
                        enabled = !isRecording && !exporting) { Text("Switch camera") }
                    Button(
                        onClick = {
                            if (isRecording) {
                                stopRequested = true
                                stopRecording()
                                phase = "ready"
                            } else if (!exporting && !isStarting) {
                                isStarting = true
                                startRecording()
                            }
                        },
                        enabled = !exporting && (!isStarting || isRecording),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFB94A48) else Color(0xFFEFA17E),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            when { exporting -> "RENDERING…"; isStarting -> "STARTING…"; isRecording -> "STOP"; else -> "● RECORD CHALLENGE" },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(onClick = { if (!isRecording && !exporting) finish() },
                        enabled = !isRecording && !exporting) { Text("Exit") }
                }
            }
        }
    }

    override fun onDestroy() {
        stopRecording()
        activeTransformer?.cancel()
        tts?.stop()
        tts?.shutdown()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroy()
    }
}

private fun composeCardColor(hex: String, fallback: Color): Color =
    runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(fallback)

/** Draws the same two-state reference card into every exported video frame. */
@OptIn(UnstableApi::class)
private class ChallengeCanvasOverlay(
    private val config: ChallengeConfig,
    private val words: List<WordItem>
) : CanvasOverlay(true) {
    private val mainColor = runCatching { AndroidColor.parseColor(config.mainColorHex) }.getOrDefault(AndroidColor.rgb(25, 24, 23))
    private val accentColor = runCatching { AndroidColor.parseColor(config.accentColorHex) }.getOrDefault(AndroidColor.rgb(233, 161, 123))
    private val textColor = runCatching { AndroidColor.parseColor(config.cardTextHex) }.getOrDefault(AndroidColor.rgb(242, 240, 237))
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mainColor }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; alpha = 195 }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; alpha = 35 }
    private val transparentAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (accentColor and 0x00FFFFFF) or ((config.labelBackgroundOpacity.coerceIn(0, 40) * 255 / 100) shl 24)
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val frame = ChallengeTimeline.at(presentationTimeUs / 1000L, words.size, config.answerSeconds, config.revealSeconds) ?: return
        val width = canvas.width.toFloat()
        val sx = width / 720f
        val height = canvas.height.toFloat()
        if (frame.complete) {
            val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
            canvas.drawRect(0f, 0f, width, height, shade)
            val done = textPaint(inkPaint, width * 0.072f, true, Paint.Align.CENTER)
            canvas.drawText("CHALLENGE COMPLETE", width / 2f, height / 2f, done)
            return
        }
        val w = words.getOrNull(frame.index) ?: return
        // Match the accent to this card's emoji; blank emoji retains the configured accent.
        val wordAccentPaint = Paint(this@ChallengeCanvasOverlay.accentPaint).apply { color = emojiAccentColor(w.emoji, config.accentColorHex) }
        val wordAccentColor = wordAccentPaint.color
        val wordTransparentAccentPaint = Paint(this@ChallengeCanvasOverlay.transparentAccentPaint).apply {
            color = (wordAccentColor and 0x00FFFFFF) or
                ((config.labelBackgroundOpacity.coerceIn(0, 40) * 255 / 100) shl 24)
        }
        val x = 28f * sx
        val y = 54f * sx
        val cardW = width - 56f * sx
        val cardH = 292f * sx
        val left = x + 32f * sx
        val right = x + cardW - 32f * sx
        canvas.drawRoundRect(RectF(x, y, x + cardW, y + cardH), 23f * sx, 23f * sx, panelPaint)

        // Small peach marker attached to the top edge.
        if (config.showTopTab) {
            val tabW = cardW * 0.085f
            val tabX = x + cardW * 0.29f
            canvas.drawRoundRect(RectF(tabX, y - 2f * sx, tabX + tabW, y + 3f * sx),
                3f * sx, 3f * sx, wordAccentPaint)
        }

        val headerBaseline = y + 42f * sx
        val countText = "${(frame.index + 1).toString().padStart(2, '0')} / ${words.size.toString().padStart(2, '0')}"
        val countPaint = textPaint(wordAccentPaint, 12f * sx, true, Paint.Align.RIGHT)
        val countWidth = countPaint.measureText(countText)
        val headerRight = right
        val availableHeader = (headerRight - left - countWidth - 20f * sx).coerceAtLeast(30f * sx)
        var cursor = left

        if (config.showChallengeLabel && config.title.isNotBlank()) {
            val labelPaint = textPaint(wordAccentPaint, 12f * sx, true, Paint.Align.LEFT)
            val labelText = fitText(config.title.uppercase(Locale.US), labelPaint, availableHeader * 0.62f)
            val labelW = labelPaint.measureText(labelText) + 18f * sx
            val labelRect = RectF(cursor, y + 23f * sx, cursor + labelW, y + 49f * sx)
            canvas.drawRoundRect(labelRect, 6f * sx, 6f * sx, wordTransparentAccentPaint)
            canvas.drawRoundRect(labelRect, 6f * sx, 6f * sx,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wordAccentColor; style = Paint.Style.STROKE; strokeWidth = 1f * sx })
            canvas.drawText(labelText, cursor + 9f * sx, headerBaseline, labelPaint)
            cursor += labelW + 9f * sx
        }
        if (config.showCta && config.ctaText.isNotBlank() && cursor < left + availableHeader) {
            val ctaPaint = textPaint(mutedPaint, 10f * sx, true, Paint.Align.LEFT)
            val ctaMax = (left + availableHeader - cursor).coerceAtLeast(0f)
            canvas.drawText(fitText(config.ctaText.uppercase(Locale.US), ctaPaint, ctaMax), cursor, headerBaseline, ctaPaint)
        }
        canvas.drawText(countText, headerRight, headerBaseline, countPaint)

        val wordColorPaint = if (frame.answerPhase) wordAccentPaint else inkPaint
        val wordPaint = textPaint(wordColorPaint, 65f * sx, true, Paint.Align.LEFT)
        val iconSpace = if (config.showIcon && w.emoji.isNotBlank()) 82f * sx else 0f
        wordPaint.textSize = fitSize(w.word, wordPaint, cardW - 64f * sx - iconSpace, 65f * sx, 35f * sx)
        val wordBaseline = y + 132f * sx
        if (w.word.isNotBlank()) canvas.drawText(w.word, left, wordBaseline, wordPaint)

        if (config.showIcon && w.emoji.isNotBlank()) {
            val iconBox = RectF(right - 48f * sx, y + 82f * sx, right, y + 130f * sx)
            canvas.drawRoundRect(iconBox, 9f * sx, 9f * sx, trackPaint)
            val emojiPaint = textPaint(inkPaint, 34f * sx, false, Paint.Align.CENTER)
            canvas.drawText(w.emoji, iconBox.centerX(), y + 117f * sx, emojiPaint)
        }

        if (w.definition.isNotBlank()) {
            val definitionPaint = textPaint(mutedPaint, 22f * sx, false, Paint.Align.LEFT)
            drawWrapped(canvas, w.definition, left, y + 174f * sx, cardW - 64f * sx, 27f * sx, 2, definitionPaint)
        }

        val barX = left
        val barY = y + 211f * sx
        val barW = cardW - 64f * sx
        if (config.showTimer) {
            canvas.drawRoundRect(RectF(barX, barY, barX + barW, barY + 4f * sx), 2f * sx, 2f * sx, trackPaint)
            val progress = ChallengeTimeline.timerProgress(frame)
            if (progress > 0f) canvas.drawRoundRect(RectF(barX, barY, barX + barW * progress, barY + 4f * sx),
                2f * sx, 2f * sx, wordAccentPaint)
        }

        val lowerBaseline = y + 250f * sx
        if (frame.answerPhase) {
            if (w.pronunciation.isNotBlank()) drawPronunciation(canvas, w.pronunciation, left, lowerBaseline, 22f * sx, wordAccentPaint)
        } else {
            val turnPaint = textPaint(inkPaint, 15f * sx, true, Paint.Align.LEFT)
            canvas.drawText("YOUR TURN", left, lowerBaseline, turnPaint)
        }
        val lowerCount = textPaint(mutedPaint, 11f * sx, true, Paint.Align.RIGHT)
        canvas.drawText("${(frame.index + 1).toString().padStart(2, '0')} OF ${words.size.toString().padStart(2, '0')}",
            right, lowerBaseline, lowerCount)

        if (config.showBottomProgress) {
            val segY = y + 271f * sx
            val gap = 4f * sx
            val segments = words.size.coerceAtMost(30).coerceAtLeast(1)
            val segW = (barW - gap * (segments - 1)) / segments
            for (i in 0 until segments) {
                val paint = if (i <= frame.index) wordAccentPaint else trackPaint
                val segX = barX + i * (segW + gap)
                canvas.drawRoundRect(RectF(segX, segY, segX + segW, segY + 4f * sx),
                    2f * sx, 2f * sx, paint)
            }
        }
    }

    private fun drawPronunciation(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, accent: Paint) {
        val base = textPaint(inkPaint, size, true, Paint.Align.LEFT)
        var cursor = x
        text.forEach { ch ->
            val p = if (ch.isUpperCase()) textPaint(accent, size, true, Paint.Align.LEFT) else base
            val value = ch.toString()
            canvas.drawText(value, cursor, baseline, p)
            cursor += p.measureText(value)
        }
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return if (maxWidth <= 0f) "" else text
        var out = text
        while (out.isNotEmpty() && paint.measureText("$out…") > maxWidth) out = out.dropLast(1)
        return if (out.isEmpty()) "" else "$out…"
    }

    private fun fitSize(text: String, paint: Paint, maxWidth: Float, preferred: Float, minimum: Float): Float {
        var size = preferred
        while (size > minimum && paint.apply { textSize = size }.measureText(text) > maxWidth) size -= 2f
        return size.coerceAtLeast(minimum)
    }

    private fun textPaint(base: Paint, size: Float, bold: Boolean, align: Paint.Align): Paint =
        Paint(base).apply {
            textSize = size
            textAlign = align
            typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

    private fun drawWrapped(canvas: Canvas, text: String, x: Float, firstBaseline: Float, maxWidth: Float,
        lineHeight: Float, maxLines: Int, paint: Paint) {
        val parts = text.split(Regex("\\s+"))
        var line = ""
        var lineNo = 0
        for (part in parts) {
            val candidate = if (line.isEmpty()) part else "$line $part"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, firstBaseline + lineNo * lineHeight, paint)
                lineNo++
                if (lineNo >= maxLines - 1) { line = candidate; break }
                line = part
            } else line = candidate
        }
        if (line.isNotEmpty() && lineNo < maxLines) {
            var fitted = line
            while (paint.measureText(fitted) > maxWidth && fitted.isNotEmpty()) fitted = fitted.dropLast(1)
            canvas.drawText(fitted, x, firstBaseline + lineNo * lineHeight, paint)
        }
    }
}
