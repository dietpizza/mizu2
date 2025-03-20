package com.kepsake.mizu2.activities

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.GridLayoutManager
import com.kepsake.mizu2.adapters.MangaCardImageAdapter
import com.kepsake.mizu2.databinding.ActivityMainBinding


class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    val imageUrls = (10..90).map { "http://192.168.0.105:3000/image_0${it}.png" }

    fun init() {
        initTopLoader()
        initGridView()
    }

    fun initTopLoader() {
        binding.pullToRefresh.setProgressViewOffset(true, 0, 200)
        binding.pullToRefresh.isEnabled = false
//        binding.pullToRefresh.isRefreshing = true
    }


    fun initGridView() {
        val adapter = MangaCardImageAdapter(imageUrls)
        val layoutManager = GridLayoutManager(this, 3)

        binding.mangaList.layoutManager = layoutManager
        binding.mangaList.adapter = adapter

        binding.mangaList.setPadding(0, getStatusBarHeight(), 0, 0)
        binding.mangaList.clipToPadding = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()

        binding.root.post {
            init()
        }
    }

    private fun getStatusBarHeight(): Int {
        val rect = Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        val statusBarHeight: Int = rect.top

        return statusBarHeight
    }

}
