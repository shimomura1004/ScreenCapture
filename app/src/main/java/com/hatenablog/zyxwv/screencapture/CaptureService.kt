package com.hatenablog.zyxwv.screencapture

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Surface
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

class CaptureService : Service() {
    val codecs = arrayOf(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        MediaFormat.MIMETYPE_VIDEO_VP8,
        MediaFormat.MIMETYPE_VIDEO_VP9)

    lateinit var mediaProjectionManager : MediaProjectionManager
    lateinit var mediaProjection : MediaProjection
    lateinit var inputSurface : Surface
    lateinit var mediaCodec : MediaCodec
    lateinit var virtualDisplay : VirtualDisplay

    lateinit var senderHandler : Handler
    lateinit var inputStream : InputStream
    lateinit var outputStream : OutputStream

    @SuppressLint("WrongConstant")
    private fun prepareVirtualDisplay(width: Int, height: Int, density: Int) {
        virtualDisplay =
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
        val codecInfo = mediaCodec.codecInfo
        Log.d("MediaCodecInfo", "hardware accelerated: ${codecInfo.isHardwareAccelerated}")
        Log.d("MediaCodecInfo", "software only: ${codecInfo.isSoftwareOnly}")
        Log.d("MediaCodecInfo", "vendor: ${codecInfo.isVendor}")
        Log.d("MediaCodecInfo", "encoder: ${codecInfo.isEncoder}")
        Log.d("MediaCodecInfo", "name: ${codecInfo.name}")

        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Log.d("MediaCodec", "onInputBufferAvailable : ${codec.codecInfo}")
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                Log.d("MediaCodec", "onOutputBufferAvailable : $info")
                val buffer = codec.getOutputBuffer(index)

                if (buffer != null) {
                    val array = ByteArray(buffer.limit())
                    buffer.get(array)
                    send(array)
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

                inputStream = clientSocket.getInputStream()
                outputStream = clientSocket.getOutputStream()

                mediaCodec.start()
                prepareVirtualDisplay(width, height, density)
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

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "default"
        val title = "CaptureService"

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel =
            NotificationChannel(channelId, title, NotificationManager.IMPORTANCE_DEFAULT)

        notificationManager.createNotificationChannel(notificationChannel)

        val notification = Notification.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText("CaptureServer")
            .build()

        startForeground(1, notification)

        val resultCode = intent?.getIntExtra("resultCode", 0)
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != null && data != null) {
            startProjection(resultCode, data)
        }

        return START_NOT_STICKY
    }

    var width = 0
    var height = 0
    var density = 0
    private fun startProjection(resultCode: Int, data: Intent) {
        val scale = 0.5

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        val displayMetrics = resources.displayMetrics
        width = (displayMetrics.widthPixels * scale).toInt()
        height = (displayMetrics.heightPixels * scale).toInt()
        density = displayMetrics.densityDpi

        prepareEncoder(width, height, codecs[0], 5000000, 60, 10)

        Log.d("ScreenCapture", "Start media streaming")

        startServer()
    }

    override fun onDestroy() {
        mediaCodec.stop()
        mediaCodec.release()
        virtualDisplay.release()
        mediaProjection.stop()

        super.onDestroy()
    }
}
