package com.gemalto.jp2.test

import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.gemalto.jp2.test.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.image.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.image.viewTreeObserver.removeOnGlobalLayoutListener(this)

                viewModel.decodeJp2Image(
                    applicationContext,
                    binding.image.width,
                    binding.image.height
                )
            }
        })

        viewModel.decodedBitmap.observe(this) { bitmap ->
            if (bitmap != null) {
                binding.image.setImageBitmap(bitmap)
            }
        }

        viewModel.logMessages.observe(this) { message ->
            Log.d(TAG, message)
        }
    }
}
