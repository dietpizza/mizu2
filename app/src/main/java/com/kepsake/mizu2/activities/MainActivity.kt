package com.kepsake.mizu2.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.kepsake.mizu2.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    fun init() {
        initTopLoader()
    }

    fun initTopLoader() {
        binding.pullToRefresh.setProgressViewOffset(true, 0, 200)
        binding.pullToRefresh.isEnabled = false
        binding.pullToRefresh.isRefreshing = true
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
}
