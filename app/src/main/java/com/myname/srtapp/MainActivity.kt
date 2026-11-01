package com.myname.srtapp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var selectedVideoUri: Uri? = null
    private var generatedSrtContent: String = ""

    // ၁။ Video ရွေးချယ်မည့်စနစ်
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            statusText.text = "🎬 Video ရွေးချယ်ပြီးပါပြီ။\nအောက်ပါခလုတ်ကိုနှိပ်၍ SRT ထုတ်ပါ။"
            statusText.setTextColor(Color.parseColor("#00E676")) // Vibrant Green
        }
    }

    // ၂။ SRT သိမ်းဆည်းမည့်စနစ်
    private val saveSrtLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri: Uri? ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(generatedSrtContent.toByteArray())
            }
            statusText.text = "✅ အောင်မြင်ပါသည်။ SRT ဖိုင်ကို သိမ်းဆည်းပြီးပါပြီ။"
            statusText.setTextColor(Color.parseColor("#00C6FF")) // Platinum Blue
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark Premium Background
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0C10")) // Deep space dark
            setPadding(60, 80, 60, 80)
        }

        val titleText = TextView(this).apply {
            text = "✨ Auto SRT Gen ✨\n(Platinum Edition)"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E5E4E2")) // Platinum/Silver
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }

        val apiKeyInput = EditText(this).apply {
            hint = "🔑 Groq API Key ထည့်ပါ"
            setHintTextColor(Color.parseColor("#787878"))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2833"))
                cornerRadius = 40f
                setStroke(3, Color.parseColor("#C5C6C7")) // Platinum border
            }
        }

        // ကာစိုစို (Glossy) Button ပြုလုပ်ပေးမည့် Helper Function
        fun createGlossyButton(btnText: String, colorStart: String, colorEnd: String, textColorStr: String): Button {
            return Button(this).apply {
                text = btnText
                isAllCaps = false
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(textColorStr))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor(colorStart), Color.parseColor(colorEnd))
                ).apply {
                    cornerRadius = 50f
                }
                elevation = 15f
            }
        }

        // Platinum/Silver ရောင်ပြေး ခလုတ်
        val pickVideoButton = createGlossyButton("📁 Video ဖိုင်ရွေးမည်", "#E0EAFC", "#CFDEF3", "#121212")
        pickVideoButton.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        statusText = TextView(this).apply {
            text = "Video ကို ရွေးချယ်ရန် အပေါ်ခလုတ်ကို နှိပ်ပါ"
            setTextColor(Color.parseColor("#C5C6C7"))
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 50)
        }

        // ကာစိုစို ရောင်လဲ့လဲ့ (Vibrant Blue/Cyan) ခလုတ်
        val generateButton = createGlossyButton("🚀 SRT စာတန်းထိုး ဖန်တီးမည်", "#00c6ff", "#0072ff", "#FFFFFF")
        
        generateButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty()) {
                statusText.text = "⚠️ API Key ထည့်ပေးပါ!"
                statusText.setTextColor(Color.parseColor("#FF5252"))
                return@setOnClickListener
            }
            if (selectedVideoUri == null) {
                statusText.text = "⚠️ Video အရင်ရွေးပေးပါ!"
                statusText.setTextColor(Color.parseColor("#FF5252"))
                return@setOnClickListener
            }

            generateButton.isEnabled = false
            statusText.text = "⏳ ၁။ Video မှ အသံဆွဲထုတ်နေပါသည်..."
            statusText.setTextColor(Color.parseColor("#FFD700")) // Gold

            // နောက်ကွယ်တွင် အလုပ်လုပ်ရန် Coroutine စတင်ခြင်း
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // အသံဖိုင်ကို ဖုန်း၏ Cache ထဲတွင် ယာယီသိမ်းမည်
                    val audioFile = File(cacheDir, "extracted_audio.m4a")
                    val extractSuccess = extractAudioFromVideo(selectedVideoUri!!, audioFile.absolutePath)

                    if (!extractSuccess || !audioFile.exists()) {
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ အသံဆွဲထုတ်ခြင်း မအောင်မြင်ပါ။ Video ဖိုင်အမျိုးအစား မှားယွင်းနေနိုင်ပါသည်။"
                            statusText.setTextColor(Color.parseColor("#FF5252"))
                            generateButton.isEnabled = true
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        statusText.text = "🌐 ၂။ AI သို့ ပို့ဆောင်နေပါသည် (အင်တာနက်လိုအပ်သည်)..."
                    }

                    // OkHttp ဖြင့် Groq API သို့ ပို့ခြင်း
                    val client = OkHttpClient.Builder()
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()

                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("model", "whisper-large-v3")
                        .addFormDataPart("response_format", "srt")
                        .addFormDataPart(
                            "file", audioFile.name,
                            audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                        )
                        .build()

                    val request = Request.Builder()
                        .url("https://api.groq.com/openai/v1/audio/transcriptions")
                        .header("Authorization", "Bearer $apiKey")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string()

                    if (response.isSuccessful && responseStr != null) {
                        generatedSrtContent = responseStr
                        withContext(Dispatchers.Main) {
                            statusText.text = "✅ ပြီးစီးပါပြီ! ဖိုင်သိမ်းမည့်နေရာ ရွေးပါ။"
                            saveSrtLauncher.launch("subtitle.srt")
                        }
                    } else {
                        // Error ဖြစ်လျှင် JSON ထဲမှ အကြောင်းရင်းကို ထုတ်ပြမည်
                        val errorMsg = try {
                            JSONObject(responseStr ?: "").getJSONObject("error").getString("message")
                        } catch (e: Exception) {
                            "Network Error"
                        }
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ API Error: $errorMsg"
                            statusText.setTextColor(Color.parseColor("#FF5252"))
                        }
                    }
                    
                    audioFile.delete() // ယာယီဖိုင် ဖျက်ပစ်မည်

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "❌ Error: ${e.message}"
                        statusText.setTextColor(Color.parseColor("#FF5252"))
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        generateButton.isEnabled = true
                    }
                }
            }
        }

        // UI များကို မျက်နှာပြင်ပေါ်သို့ စီစဉ်ထည့်သွင်းခြင်း
        layout.addView(titleText)
        layout.addView(apiKeyInput)
        layout.addView(TextView(this).apply { height = 60 })
        layout.addView(pickVideoButton)
        layout.addView(statusText)
        layout.addView(generateButton)

        setContentView(layout)
    }

    // Android ၏ မူရင်းစနစ်ဖြင့် Video မှ အသံကို စက္ကန့်ပိုင်းအတွင်း ဆွဲထုတ်မည့် Function (FFmpeg ထက် မြန်သည်)
    private fun extractAudioFromVideo(videoUri: Uri, outputPath: String): Boolean {
        val extractor = MediaExtractor()
        try {
            contentResolver.openFileDescriptor(videoUri, "r")?.fileDescriptor?.let {
                extractor.setDataSource(it)
            } ?: return false

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) return false

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            val bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).takeIf { it > 0 } ?: (1024 * 1024)
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.flags = extractor.sampleFlags
                bufferInfo.presentationTimeUs = extractor.sampleTime
                
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            
            muxer.stop()
            muxer.release()
            extractor.release()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
