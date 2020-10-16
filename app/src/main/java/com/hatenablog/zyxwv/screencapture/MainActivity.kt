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

    val codecs = arrayOf(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            MediaFormat.MIMETYPE_VIDEO_VP8,
            MediaFormat.MIMETYPE_VIDEO_VP9)

    lateinit var mediaProjectionManager : MediaProjectionManager
    lateinit var mediaProjection : MediaProjection
    lateinit var inputSurface : Surface
    lateinit var mediaCodec : MediaCodec

    lateinit var senderHandler : Handler

    lateinit var outputStream : OutputStream

    private fun prepareVirtualDisplay(width: Int, height: Int, density: Int) {
        val virtualDisplay =
                mediaProjection.createVirtualDisplay(
                        "Capturing Display",
                        width, height, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        inputSurface,
                        null, null)
    }

    private fun prepareEncoder(width: Int, height: Int, mimeType: String, bitRate: Int, fps: Int, iframeInterval: Int) {
        val mediaFormat = MediaFormat.createVideoFormat(mimeType, width, height)
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, fps)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval)

        mediaCodec = MediaCodec.createEncoderByType(mimeType)
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.d("MediaCodec", "onInputBufferAvailable : ${codec.codecInfo}")
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                Log.d("MediaCodec", "onOutputBufferAvailable : $info")
                val buffer = codec.getOutputBuffer(index)

                if (buffer != null) {
                    send(buffer.array())
                }

                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.d("MediaCodec", "onError : ${e.message}")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.d("MediaCodec", "onOutputFormatChanged : ${format.getString(MediaFormat.KEY_MIME)}");
            }
        })

        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = mediaCodec.createInputSurface()
    }

    private fun startServer() {
        val senderThread = HandlerThread("senderThread")
        senderThread.start()
        senderHandler = Handler(senderThread.looper)

        val serverThread = Thread(object : Runnable {
            override fun run() {
                val listener = ServerSocket()
                listener.reuseAddress = true
                listener.bind(InetSocketAddress(8080))
                Log.d("ScreenCapture", "Start server on 8080...")

                val clientSocket = listener.accept()
                val inputStream = clientSocket.getInputStream()
                outputStream = clientSocket.getOutputStream()

                mediaCodec.start()
            }
        })
        serverThread.start()
    }

    private fun send(array: ByteArray) {
        senderHandler.post(object : Runnable {
            override fun run() {
                outputStream.write(array)
            }
        })
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE) {
            return
        }

        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
            return
        }

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        prepareEncoder(width, height, codecs[0], 5000000, 30, 10)
        prepareVirtualDisplay(width, height, density)

        Log.d("ScreenCapture", "Start media streaming")
        startServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.start_button)
        startButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val intent = Intent(application, CaptureService::class.java)
                startForegroundService(intent)
//                // start!
//                mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
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