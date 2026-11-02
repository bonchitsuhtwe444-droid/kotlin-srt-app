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
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var selectedVideoUri: Uri? = null
    private var generatedSrtContent: String = ""

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            statusText.text = "🎬 Video ရွေးချယ်ပြီးပါပြီ။\nအောက်ပါခလုတ်ကိုနှိပ်၍ SRT ထုတ်ပါ။"
            statusText.setTextColor(Color.parseColor("#00E676"))
        }
    }

    private val saveSrtLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri: Uri? ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(generatedSrtContent.toByteArray())
            }
            statusText.text = "✅ အောင်မြင်ပါသည်။ မြန်မာ SRT ဖိုင်ကို သိမ်းဆည်းပြီးပါပြီ။"
            statusText.setTextColor(Color.parseColor("#00C6FF"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0C10"))
            setPadding(60, 80, 60, 80)
        }

        // Logo များကို ဖယ်ရှားပြီးဖြစ်၍ ခေါင်းစဉ်ကို သေသပ်စွာ တင်ထားသည်
        val titleText = TextView(this).apply {
            text = "Auto SRT Gen\n✨ (Gemini AI Edition)"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E5E4E2"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#00c6ff"))
        }

        val apiKeyInput = EditText(this).apply {
            hint = "🔑 Gemini API Key ထည့်ပါ"
            setHintTextColor(Color.parseColor("#787878"))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2833"))
                cornerRadius = 40f
                setStroke(3, Color.parseColor("#C5C6C7"))
            }
        }

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

        val generateButton = createGlossyButton("🚀 မြန်မာ SRT ဖန်တီးမည်", "#00c6ff", "#0072ff", "#FFFFFF")
        
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
            statusText.setTextColor(Color.parseColor("#FFD700"))

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val audioFile = File(cacheDir, "extracted_audio.m4a")
                    val extractSuccess = extractAudioFromVideo(selectedVideoUri!!, audioFile.absolutePath)

                    if (!extractSuccess || !audioFile.exists()) {
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ အသံဆွဲထုတ်ခြင်း မအောင်မြင်ပါ။"
                            statusText.setTextColor(Color.parseColor("#FF5252"))
                            generateButton.isEnabled = true
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        statusText.text = "🌐 ၂။ Gemini ဖြင့် SRT နှင့် ဘာသာပြန်ခြင်းကို လုပ်ဆောင်နေပါသည်..."
                    }

                    val bytes = audioFile.readBytes()
                    val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    val client = OkHttpClient.Builder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .build()

                    val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val geminiJson = JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", "You are an expert subtitler. Listen to this audio and generate a perfectly timed SRT file. Translate the spoken English directly into Myanmar (Burmese) language. Return ONLY the raw SRT format text starting with block 1. Do NOT include markdown blocks like ```srt.")
                                    })
                                    put(JSONObject().apply {
                                        put("inline_data", JSONObject().apply {
                                            put("mime_type", "audio/mp4")
                                            put("data", base64Audio)
                                        })
                                    })
                                })
                            })
                        })
                    }

                    // URL တွင် Space များ ပါမသွားစေရန် trim() လုပ်ထားပါသည်
                    val cleanedApiKey = apiKey.trim()
                    val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$cleanedApiKey"

                    val request = Request.Builder()
                        .url(urlStr)
                        .post(geminiJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string()

                    if (response.isSuccessful && responseStr != null) {
                        try {
                            val root = JSONObject(responseStr)
                            var extractedText = root.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                            
                            extractedText = extractedText.replace("```srt", "").replace("```", "").trim()
                            generatedSrtContent = extractedText

                            withContext(Dispatchers.Main) {
                                statusText.text = "✅ ပြီးစီးပါပြီ! မြန်မာ SRT ဖိုင်ကို သိမ်းပါ။"
                                saveSrtLauncher.launch("gemini_myanmar.srt")
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                statusText.text = "❌ Gemini မှ အဖြေမရရှိပါ။"
                                statusText.setTextColor(Color.parseColor("#FF5252"))
                            }
                        }
                    } else {
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
                    
                    audioFile.delete()

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

        layout.addView(titleText)
        layout.addView(apiKeyInput)
        layout.addView(TextView(this).apply { height = 60 })
        layout.addView(pickVideoButton)
        layout.addView(statusText)
        layout.addView(generateButton)

        setContentView(layout)
    }

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
