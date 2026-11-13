package com.lovedrive.auto

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lovedrive.auto.databinding.ActivityMainBinding
import com.lovedrive.auto.databinding.ItemPhotoBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PhotoAdapter

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            PhotoRepository.importUris(this, uris)
            refreshList()
            notifyServiceReload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PhotoAdapter { photo ->
            PhotoRepository.deletePhoto(photo)
            refreshList()
            notifyServiceReload()
        }

        binding.rvPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvPhotos.adapter = adapter

        binding.etSubtitle.setText(PhotoRepository.getSubtitle(this))
        binding.etSubtitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                PhotoRepository.setSubtitle(this@MainActivity, s?.toString().orEmpty())
                notifyServiceReload()
            }
        })

        val savedInterval = PhotoRepository.getIntervalSeconds(this)
        binding.sliderInterval.value = savedInterval.toFloat()
        binding.tvIntervalLabel.text = "Slide Interval: ${savedInterval}s"
        binding.sliderInterval.addOnChangeListener { _, value, _ ->
            val sec = value.toInt()
            PhotoRepository.setIntervalSeconds(this, sec)
            binding.tvIntervalLabel.text = "Slide Interval: ${sec}s"
        }

        binding.btnAddPhotos.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnTogglePlay.setOnClickListener {
            startService(Intent(this, LoveDriveMediaService::class.java).apply {
                action = LoveDriveMediaService.ACTION_TOGGLE
            })
        }

        refreshList()
    }

    private fun refreshList() {
        val photos = PhotoRepository.getPhotos(this)
        adapter.submit(photos)
        binding.tvCount.text = "${photos.size} photo(s) ready for Android Auto"
    }

    private fun notifyServiceReload() {
        startService(Intent(this, LoveDriveMediaService::class.java).apply {
            action = LoveDriveMediaService.ACTION_RELOAD
        })
    }

    private class PhotoAdapter(
        private val onRemove: (LovePhoto) -> Unit
    ) : RecyclerView.Adapter<PhotoAdapter.VH>() {

        private val items = mutableListOf<LovePhoto>()

        fun submit(newItems: List<LovePhoto>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        class VH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val bmp = PhotoRepository.loadCarBitmap(item.file, 300)
            holder.binding.ivThumb.setImageBitmap(bmp)
            holder.binding.btnRemove.setOnClickListener { onRemove(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
