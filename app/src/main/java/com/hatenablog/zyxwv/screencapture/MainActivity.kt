package com.hatenablog.zyxwv.screencapture

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

class MainActivity : AppCompatActivity() {
    val REQUEST_CODE = 1

    lateinit var mediaProjectionManager : MediaProjectionManager

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE) {
            return
        }

        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(application, CaptureService::class.java)
        intent.putExtra("resultCode", resultCode)
        intent.putExtra("data", data)
        startForegroundService(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.start_button)
        startButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
            }
        })

        val stopButton = findViewById<Button>(R.id.stop_button)
        stopButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val intent = Intent(applicationContext, CaptureService::class.java)
                stopService(intent)
            }
        })
    }
}