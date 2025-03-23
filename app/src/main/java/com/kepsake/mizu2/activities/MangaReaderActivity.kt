package com.kepsake.mizu2.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.kepsake.mizu2.adapters.MangaPanelAdapter
import com.kepsake.mizu2.data.models.MangaPanel
import com.kepsake.mizu2.databinding.ActivityMainBinding
import io.objectbox.Box

class MangaReaderActivity : ComponentActivity() {
    private lateinit var mangaBox: Box<MangaPanel>
    private lateinit var mangaPanelAdapter: MangaPanelAdapter
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
}