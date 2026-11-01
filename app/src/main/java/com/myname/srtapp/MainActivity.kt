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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
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

        val titleText = TextView(this).apply {
            text = "📢 Auto SRT Gen\n✨ (Burmese AI Edition)"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E5E4E2"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#00c6ff"))
        }

        val apiKeyInput = EditText(this).apply {
            hint = "🔑 Groq API Key ထည့်ပါ"
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
                        statusText.text = "🌐 ၂။ Whisper AI ဖြင့် အသံကို စာပြောင်းနေပါသည်..."
                    }

                    val client = OkHttpClient.Builder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .build()

                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("model", "whisper-large-v3")
                        .addFormDataPart("response_format", "verbose_json")
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
                        
                        val engSrtContent = parseVerboseJsonToSrt(responseStr)
                        
                        withContext(Dispatchers.Main) {
                            statusText.text = "🇲🇲 ၃။ Llama AI ဖြင့် မြန်မာလို ဘာသာပြန်နေပါသည်..."
                        }

                        val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                        val translateJson = JSONObject().apply {
                            put("model", "llama-3.3-70b-versatile")
                            put("messages", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "system")
                                    put("content", "You are an expert subtitle translator. Translate the following English SRT to Myanmar (Burmese). You MUST keep the exact original SRT format, block numbers, and timestamps (00:00:00,000 --> 00:00:00,000). ONLY translate the subtitle text to Burmese. DO NOT add any other explanation.")
                                })
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("content", engSrtContent)
                                })
                            })
                            put("temperature", 0.3)
                        }

                        val transRequest = Request.Builder()
                            .url("https://api.groq.com/openai/v1/chat/completions")
                            .header("Authorization", "Bearer $apiKey")
                            .post(translateJson.toString().toRequestBody(jsonMediaType)) // ဤနေရာတွင် မှန်ကန်စွာ ပြင်ဆင်ထားပါသည်
                            .build()

                        val transResponse = client.newCall(transRequest).execute()
                        val transResponseStr = transResponse.body?.string()

                        if (transResponse.isSuccessful && transResponseStr != null) {
                            val translatedText = JSONObject(transResponseStr)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            
                            generatedSrtContent = translatedText

                            withContext(Dispatchers.Main) {
                                statusText.text = "✅ ပြီးစီးပါပြီ! မြန်မာ SRT ဖိုင်ကို သိမ်းပါ။"
                                saveSrtLauncher.launch("myanmar_subtitle.srt")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                statusText.text = "❌ ဘာသာပြန်ခြင်း အဆင်မပြေပါ။"
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

    private fun parseVerboseJsonToSrt(jsonStr: String): String {
        val srtBuilder = StringBuilder()
        try {
            val jsonObject = JSONObject(jsonStr)
            val segments = jsonObject.getJSONArray("segments")
            for (i in 0 until segments.length()) {
                val segment = segments.getJSONObject(i)
                val start = segment.getDouble("start")
                val end = segment.getDouble("end")
                val text = segment.getString("text").trim()
                
                srtBuilder.append(i + 1).append("\n")
                srtBuilder.append(formatTimestamp(start)).append(" --> ").append(formatTimestamp(end)).append("\n")
                srtBuilder.append(text).append("\n\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return srtBuilder.toString()
    }

    private fun formatTimestamp(seconds: Double): String {
        val millis = (seconds * 1000).toLong()
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val secs = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        val ms = millis % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, secs, ms)
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
