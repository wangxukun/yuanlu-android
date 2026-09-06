package com.wxkzd.yuanlu.core.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/**
 * 16kHz / mono / PCM16 录音器，停止时拼 44 字节 WAV 头返回完整 WAV 字节，
 * 与 Web 端 AudioContext(16000) + encodeWAV 的上传格式逐字段一致
 * （有道 ISE 要求 rate=16000、channel=1、format=wav）。
 * amplitude 回调（0..100 归一化）驱动录音音量动效。
 */
class WavRecorder(
    private val sampleRate: Int = 16000,
    private val onAmplitude: (Int) -> Unit = {}
) {
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var recording = false

    private val pcm = ByteArrayOutputStream()

    val isRecording: Boolean get() = recording

    /** 启动录音；调用方须先取得 RECORD_AUDIO 权限。失败返回 false。 */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
        } catch (_: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        pcm.reset()
        audioRecord = record
        recording = true
        record.startRecording()
        thread = Thread {
            val buffer = ShortArray(1024)
            while (recording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    // short → little-endian bytes
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    synchronized(pcm) { pcm.write(bytes) }
                    // 本块峰值归一化到 0..100（32767 满幅）
                    var peak = 0
                    for (i in 0 until read) {
                        val v = kotlin.math.abs(buffer[i].toInt())
                        if (v > peak) peak = v
                    }
                    onAmplitude((peak * 100 / 32767).coerceIn(0, 100))
                }
            }
        }.apply { start() }
        return true
    }

    /** 停止录音并返回完整 WAV 字节；未在录音或无数据返回 null。 */
    fun stop(): ByteArray? {
        if (!recording) return null
        recording = false
        try {
            thread?.join(1000)
        } catch (_: InterruptedException) {
        }
        thread = null
        audioRecord?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        audioRecord = null
        val pcmBytes = synchronized(pcm) { pcm.toByteArray() }
        if (pcmBytes.isEmpty()) return null
        return wavHeader(pcmBytes.size) + pcmBytes
    }

    fun release() {
        recording = false
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
        }
        thread = null
        audioRecord?.let { runCatching { it.release() } }
        audioRecord = null
    }

    /** 44 字节 PCM WAV 头（RIFF/fmt/data，对齐 Web encodeWAV） */
    private fun wavHeader(dataSize: Int): ByteArray {
        val byteRate = sampleRate * 2          // 16bit * 1ch
        val blockAlign = 2
        val header = ByteArray(44)
        fun putInt(offset: Int, value: Int, bytes: Int) {
            for (i in 0 until bytes) {
                header[offset + i] = (value shr (8 * i) and 0xFF).toByte()
            }
        }
        fun putAscii(offset: Int, text: String) {
            for (i in text.indices) header[offset + i] = text[i].code.toByte()
        }
        putAscii(0, "RIFF")
        putInt(4, 36 + dataSize, 4)
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putInt(16, 16, 4)                       // PCM chunk size
        putInt(20, 1, 2)                        // PCM format
        putInt(22, 1, 2)                        // mono
        putInt(24, sampleRate, 4)
        putInt(28, byteRate, 4)
        putInt(32, blockAlign, 2)
        putInt(34, 16, 2)                       // bits per sample
        putAscii(36, "data")
        putInt(40, dataSize, 4)
        return header
    }
}
