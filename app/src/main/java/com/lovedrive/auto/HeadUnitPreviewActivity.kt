package com.lovedrive.auto

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.lovedrive.auto.databinding.ActivityHeadUnitPreviewBinding

class HeadUnitPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHeadUnitPreviewBinding
    private val handler = Handler(Looper.getMainLooper())
    private var photos: List<LovePhoto> = emptyList()
    private var currentIndex = 0
    private var isPlaying = true
    private var elapsedTick = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || photos.isEmpty()) return
            val intervalSec = PhotoRepository.getIntervalSeconds(this@HeadUnitPreviewActivity)
            elapsedTick++
            if (elapsedTick >= intervalSec) {
                elapsedTick = 0
                currentIndex = (currentIndex + 1) % photos.size
                renderPhoto()
            }
            binding.pbCarSeekbar.progress = ((elapsedTick.toFloat() / intervalSec) * 100).toInt()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeadUnitPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photos = PhotoRepository.getPhotos(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnModeSplit.setOnClickListener { setSplitMode(true) }
        binding.btnModeFull.setOnClickListener { setSplitMode(false) }

        binding.btnCarPrev.setOnClickListener {
            if (photos.isEmpty()) return@setOnClickListener
            elapsedTick = 0
            currentIndex = (currentIndex - 1 + photos.size) % photos.size
            renderPhoto()
        }

        binding.btnCarNext.setOnClickListener {
            if (photos.isEmpty()) return@setOnClickListener
            elapsedTick = 0
            currentIndex = (currentIndex + 1) % photos.size
            renderPhoto()
        }

        binding.btnCarPlayPause.setOnClickListener {
            isPlaying = !isPlaying
            binding.btnCarPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            handler.removeCallbacks(tickRunnable)
            if (isPlaying) handler.postDelayed(tickRunnable, 1000L)
        }

        val hideSeekbar = PhotoRepository.getHideSeekbar(this)
        binding.pbCarSeekbar.visibility = if (hideSeekbar) View.GONE else View.VISIBLE

        setSplitMode(true)
        renderPhoto()
        if (photos.isNotEmpty()) {
            handler.postDelayed(tickRunnable, 1000L)
        }
    }

    private fun setSplitMode(isSplit: Boolean) {
        binding.panelMaps.visibility = if (isSplit) View.VISIBLE else View.GONE
        binding.btnModeSplit.alpha = if (isSplit) 1.0f else 0.6f
        binding.btnModeFull.alpha = if (isSplit) 0.6f else 1.0f
    }

    private fun renderPhoto() {
        val stats = PhotoRepository.getAnniversaryStats(this)
        val greeting = PhotoRepository.getTimeGreeting("Kayalvizhi")
        val reservedNote = PhotoRepository.getSubtitle(this)
        val ord = PhotoRepository.formatOrdinal(stats.nextAnniversaryNumber)
        val annivText = if (stats.isAnniversaryToday) {
            "${stats.daysTogether}d • Happy $ord Anniv!"
        } else {
            "${stats.daysTogether}d • ${stats.daysToNextAnniversary}d to $ord Anniv"
        }

        if (photos.isEmpty()) {
            binding.ivCarPhoto.setImageResource(R.drawable.ic_love_drive)
            binding.tvCarTitle.text = greeting
            binding.tvCarSubtitle.text = "$reservedNote • Add photos to preview"
            return
        }

        val photo = photos[currentIndex]
        val bmp = PhotoRepository.loadCarBitmap(
            photo.file,
            800,
            "Reserved for Kayalvizhi ❤️ • ${stats.daysTogether} Days"
        )
        binding.ivCarPhoto.setImageBitmap(bmp)
        binding.tvCarTitle.text = "$greeting • ${photo.index}/${photo.total}"
        binding.tvCarSubtitle.text = if (reservedNote.isNotEmpty()) "$reservedNote • $annivText" else annivText
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }
}
