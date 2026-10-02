package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Legacy forwarding Activity ensuring backward compatibility.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, com.malaramofficial.mobilestudio.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
        }
        startActivity(intent)
        finish()
    }
}

