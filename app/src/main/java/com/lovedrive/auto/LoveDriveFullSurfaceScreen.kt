package com.lovedrive.auto

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class LoveDriveFullSurfaceScreen(carContext: CarContext) :
    Screen(carContext),
    SurfaceCallback,
    DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var surfaceContainer: SurfaceContainer? = null
    private var visibleRect: Rect = Rect()
    private var photos: List<LovePhoto> = emptyList()
    private var currentIndex = 0
    private var isPlaying = true
    private var isTickerActive = true
    private var tickerPixelX = 0f
    private var cachedKey: String? = null
    private var cachedFrame: Bitmap? = null
    private var lastAdvanceMs = System.currentTimeMillis()
    private lateinit var voiceHelper: VoiceGreetingHelper
    private var hasGreeted = false

    private val renderRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val intervalMs = PhotoRepository.getIntervalSeconds(carContext) * 1000L
            if (isPlaying && photos.isNotEmpty() && now - lastAdvanceMs >= intervalMs) {
                lastAdvanceMs = now
                currentIndex = (currentIndex + 1) % photos.size
                cachedKey = null
                cachedFrame = null
            }
            drawFrameToSurface()
            handler.postDelayed(this, 50L) // ~20 FPS smooth fullscreen photo + bottom ticker
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        voiceHelper = VoiceGreetingHelper(carContext)
        isTickerActive = PhotoRepository.getCarTickerEnabled(carContext)
        photos = PhotoRepository.getPhotos(carContext)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        photos = PhotoRepository.getPhotos(carContext)
        lastAdvanceMs = System.currentTimeMillis()
        if (!hasGreeted) {
            hasGreeted = true
            voiceHelper.speakStartupGreeting()
        }
        handler.removeCallbacks(renderRunnable)
        handler.post(renderRunnable)
    }

    override fun onStop(owner: LifecycleOwner) {
        handler.removeCallbacks(renderRunnable)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacks(renderRunnable)
        voiceHelper.shutdown()
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surfaceContainer = container
        cachedKey = null
        cachedFrame = null
        drawFrameToSurface()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visibleRect = visibleArea
        drawFrameToSurface()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        if (visibleRect.isEmpty) visibleRect = stableArea
        drawFrameToSurface()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        if (surfaceContainer === container) {
            surfaceContainer = null
        }
    }

    override fun onClick(x: Float, y: Float) {
        nextPhoto()
    }

    private fun nextPhoto() {
        photos = PhotoRepository.getPhotos(carContext)
        if (photos.isEmpty()) return
        currentIndex = (currentIndex + 1) % photos.size
        lastAdvanceMs = System.currentTimeMillis()
        cachedKey = null
        cachedFrame = null
        drawFrameToSurface()
    }

    private fun prevPhoto() {
        photos = PhotoRepository.getPhotos(carContext)
        if (photos.isEmpty()) return
        currentIndex = (currentIndex - 1 + photos.size) % photos.size
        lastAdvanceMs = System.currentTimeMillis()
        cachedKey = null
        cachedFrame = null
        drawFrameToSurface()
    }

    private fun buildTickerText(): String {
        val stats = PhotoRepository.getAnniversaryStats(carContext)
        val ord = PhotoRepository.formatOrdinal(stats.nextAnniversaryNumber)
        val anniv = if (stats.isAnniversaryToday) {
            "${stats.daysTogether} Days Together • Happy $ord Anniversary!"
        } else {
            "${stats.daysTogether} Days Together • ${stats.daysToNextAnniversary}d to $ord Anniv (Jan 21)"
        }
        val bday = PhotoRepository.getNextBirthdayReminder()
        val bdayPart = if (bday.isToday) {
            " • 🎂 Happy Birthday ${bday.name}!"
        } else {
            " • 🎂 Next Bday: ${bday.name} (${bday.dateFormatted} • ${bday.daysUntil}d)"
        }
        val letter = PhotoRepository.getLoveLetter(carContext).trim()
        return if (letter.isNotEmpty()) {
            "💌 $letter   •   $anniv$bdayPart   •   "
        } else {
            "$anniv$bdayPart   •   "
        }
    }

    private fun drawFrameToSurface() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return
        val w = container.width.coerceAtLeast(1)
        val h = container.height.coerceAtLeast(1)

        val canvas = try {
            surface.lockCanvas(null)
        } catch (_: Exception) {
            null
        } ?: return

        try {
            canvas.drawColor(Color.rgb(12, 10, 16))
            if (photos.isEmpty()) {
                photos = PhotoRepository.getPhotos(carContext)
            }

            if (photos.isNotEmpty()) {
                if (currentIndex >= photos.size) currentIndex = 0
                val photo = photos[currentIndex]
                val key = "${photo.id}_${w}x${h}"
                val bmp = if (cachedKey == key && cachedFrame != null) {
                    cachedFrame
                } else {
                    PhotoRepository.loadCarBitmap(photo.file, w, null, h).also {
                        cachedKey = key
                        cachedFrame = it
                    }
                }
                if (bmp != null) {
                    canvas.drawBitmap(bmp, 0f, 0f, null)
                }
            }

            // Top translucent header pill with Greeting + Photo count
            val greeting = PhotoRepository.getTimeGreeting("Kayalvizhi")
            val topTitle = if (photos.isNotEmpty()) {
                "$greeting  •  ${currentIndex + 1}/${photos.size}"
            } else {
                "$greeting  •  Add photos in Love Drive phone app"
            }

            val topBarH = h * 0.095f
            val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(155, 16, 12, 22)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), topBarH, topPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = h * 0.042f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            val fmTop = titlePaint.fontMetrics
            val topTextY = (topBarH / 2f) - (fmTop.ascent + fmTop.descent) / 2f
            canvas.drawText(topTitle, w * 0.035f, topTextY, titlePaint)

            // Bottom continuous news-scroll ticker bar
            if (isTickerActive) {
                val barH = h * 0.105f
                val barTop = h - barH
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(215, 38, 12, 28)
                }
                canvas.drawRect(0f, barTop, w.toFloat(), h.toFloat(), barPaint)

                val tickerStr = buildTickerText()
                val tickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(253, 230, 138)
                    textSize = h * 0.043f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                val textWidth = tickerPaint.measureText(tickerStr).coerceAtLeast(1f)
                tickerPixelX -= (w * 0.0045f)
                if (tickerPixelX <= -textWidth) {
                    tickerPixelX += textWidth
                }

                val fm = tickerPaint.fontMetrics
                val textY = (barTop + barH / 2f) - (fm.ascent + fm.descent) / 2f
                var drawX = tickerPixelX
                while (drawX < w) {
                    canvas.drawText(tickerStr, drawX, textY, tickerPaint)
                    drawX += textWidth
                }
            }
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("⏮")
                    .setOnClickListener { prevPhoto() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (isPlaying) "⏸" else "▶")
                    .setOnClickListener {
                        isPlaying = !isPlaying
                        lastAdvanceMs = System.currentTimeMillis()
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⏭")
                    .setOnClickListener { nextPhoto() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (isTickerActive) "💌 ON" else "💌 OFF")
                    .setOnClickListener {
                        isTickerActive = !isTickerActive
                        PhotoRepository.setCarTickerEnabled(carContext, isTickerActive)
                        invalidate()
                    }
                    .build()
            )
            .build()

        val mapActionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .setMapActionStrip(mapActionStrip)
            .build()
    }
}
