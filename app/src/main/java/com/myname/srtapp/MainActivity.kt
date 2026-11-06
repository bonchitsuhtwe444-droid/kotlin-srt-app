package com.myname.srtapp

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
    private var selectedStyle = "Standard"

    private lateinit var titleText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var btnPickVideo: Button
    private lateinit var btnGenerate: Button
    private lateinit var styleSpinner: Spinner

    private var isMyanmar = true 

    private val textLabels: Map<String, Map<Boolean, String>> = mapOf(
        "title" to mapOf(true to "AUTO SRT GEN", false to "AUTO SRT GEN"),
        "api_hint" to mapOf(true to "Gemini API Key ထည့်ပါ", false to "Enter Gemini API Key"),
        "pick_vid" to mapOf(true to "📁 ဗီဒီယို ရွေးပါ", false to "📁 Select Video"),
        "gen_srt" to mapOf(true to "🚀 SRT ဖိုင်ထုတ်မည်", false to "🚀 Generate SRT"),
        "ready" to mapOf(true to "အသင့်ဖြစ်ပါပြီ...", false to "Ready to start...")
    )

    private val arrayLabels: Map<String, Map<Boolean, Array<String>>> = mapOf(
        "styles" to mapOf(
            true to arrayOf("ပုံမှန် (Standard)", "အလွတ်သဘော (Informal)", "အက်ရှင် (Action)", "ဟာသ (Comedy)"),
            false to arrayOf("Standard", "Informal", "Action", "Comedy")
        )
    )

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            statusText.text = if (isMyanmar) "✅ ဗီဒီယိုဖိုင် ရွေးချယ်ပြီးပါပြီ။" else "✅ Video selected."
            statusText.setTextColor(Color.parseColor("#4ADE80"))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B101E")) 
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(40, 60, 40, 60)
        }

        // Language Toggle
        val langToggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val btnLang = Button(this).apply {
            text = "🌐 English"
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = GradientDrawable().apply { setColor(Color.parseColor("#334155")); cornerRadius = 15f }
            setPadding(20, 10, 20, 10)
        }
        langToggleRow.addView(btnLang)

        // Title
        titleText = TextView(this).apply {
            text = textLabels["title"]!![isMyanmar]
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00C6FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }

        // API Key Input
        apiKeyInput = EditText(this).apply {
            hint = textLabels["api_hint"]!![isMyanmar]
            setHintTextColor(Color.parseColor("#475569"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151C2C"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#2A344A"))
            }
            setPadding(40, 35, 40, 35)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
        }

        // Style Spinner
        styleSpinner = Spinner(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#151C2C")); cornerRadius = 20f; setStroke(2, Color.parseColor("#2A344A")) }
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#151C2C")))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        setSpinnerAdapter(styleSpinner, arrayLabels["styles"]!![isMyanmar]!!)
        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedStyle = when(pos) { 1 -> "Informal"; 2 -> "Action"; 3 -> "Comedy"; else -> "Standard professional" }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Buttons
        btnPickVideo = Button(this).apply {
            text = textLabels["pick_vid"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 16f
            background = GradientDrawable().apply { setColor(Color.parseColor("#2563EB")); cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            setPadding(0, 30, 0, 30)
            setOnClickListener { pickVideoLauncher.launch("video/*") }
        }

        btnGenerate = Button(this).apply {
            text = textLabels["gen_srt"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#00C6FF"), Color.parseColor("#0072FF"))).apply { cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            setPadding(0, 35, 0, 35)
        }

        // Status Text
        statusText = TextView(this).apply {
            text = textLabels["ready"]!![isMyanmar]
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        btnLang.setOnClickListener {
            isMyanmar = !isMyanmar
            btnLang.text = if (isMyanmar) "🌐 English" else "🌐 မြန်မာ"
            titleText.text = textLabels["title"]!![isMyanmar]
            apiKeyInput.hint = textLabels["api_hint"]!![isMyanmar]
            btnPickVideo.text = textLabels["pick_vid"]!![isMyanmar]
            btnGenerate.text = textLabels["gen_srt"]!![isMyanmar]
            
            if (statusText.text.toString().contains("Ready") || statusText.text.toString().contains("အသင့်")) {
                statusText.text = textLabels["ready"]!![isMyanmar]
            }
            setSpinnerAdapter(styleSpinner, arrayLabels["styles"]!![isMyanmar]!!)
        }

        btnGenerate.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty() || selectedVideoUri == null) {
                statusText.text = if (isMyanmar) "⚠️ Gemini API Key နှင့် Video ထည့်ပေးပါ!" else "⚠️ Enter Gemini API Key & Video!"
                statusText.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }

            btnGenerate.isEnabled = false; btnGenerate.alpha = 0.5f

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val audioFile = File(cacheDir, "extracted_audio.m4a")
                    val extractSuccess = extractAudioFromVideo(selectedVideoUri!!, audioFile.absolutePath) { progress ->
                        withContext(Dispatchers.Main) { 
                            statusText.text = if (isMyanmar) "⏳ အသံထုတ်ယူနေသည်... ($progress%)" else "⏳ Extracting Audio... ($progress%)"
                            statusText.setTextColor(Color.parseColor("#FBBF24")) 
                        }
                    }

                    if (!extractSuccess || !audioFile.exists()) {
                        withContext(Dispatchers.Main) { 
                            statusText.text = if (isMyanmar) "❌ အသံထုတ်မရပါ" else "❌ Audio extraction failed"
                            btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f 
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) { statusText.text = if (isMyanmar) "🌐 AI ဘာသာပြန်နေသည်... ခဏစောင့်ပါ" else "🌐 AI Translating... Please wait." }

                    val bytes = audioFile.readBytes()
                    val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    val client = OkHttpClient.Builder()
                        .connectTimeout(0, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS)
                        .writeTimeout(0, TimeUnit.SECONDS)
                        .build()
                        
                    val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    // --- မြန်မာဘာသာသို့ အတင်းအကျပ်ပြောင်းခိုင်းသော Prompt အသစ် (Strict Translation Prompt) ---
                    val aiPrompt = """
                        You are an expert translator and subtitler. 
                        Listen to the audio (which may be in Chinese, English, or any other language) and generate a perfectly timed SRT file. 
                        You MUST translate the meaning directly into Myanmar (Burmese) language using a $selectedStyle tone. 
                        The final subtitle text MUST be written entirely in Myanmar script. DO NOT output in the original spoken language. 
                        Return ONLY the raw SRT text format without any markdown or extra words.
                    """.trimIndent()

                    val geminiJson = JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(JSONObject().apply { put("text", aiPrompt) })
                                    put(JSONObject().apply { put("inline_data", JSONObject().apply { put("mime_type", "audio/mp4"); put("data", base64Audio) }) })
                                })
                            })
                        })
                    }

                    val cleanedApiKey = apiKey.trim()
                    val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$cleanedApiKey"
                    
                    val request = Request.Builder().url(urlStr).post(geminiJson.toString().toRequestBody(jsonMediaType)).build()
                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string()

                    if (response.isSuccessful && responseStr != null) {
                        try {
                            var extractedText = JSONObject(responseStr).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                            extractedText = extractedText.replace("```srt", "").replace("```", "").trim()
                            generatedSrtContent = extractedText

                            val isSaved = autoSaveTextFile("AutoSRT_${System.currentTimeMillis()}.srt", generatedSrtContent)

                            withContext(Dispatchers.Main) {
                                if (isSaved) {
                                    statusText.text = if (isMyanmar) "✅ ပြီးစီးပါပြီ! Downloads/AutoSRT သို့သိမ်းပြီးပါပြီ။" else "✅ Success! Saved to Downloads/AutoSRT."
                                    statusText.setTextColor(Color.parseColor("#4ADE80"))
                                } else {
                                    statusText.text = if (isMyanmar) "⚠️ ဖုန်းထဲသိမ်း၍မရပါ။ Storage Permission လိုအပ်ပါသည်။" else "⚠️ Save Failed. Storage Permission required."
                                    statusText.setTextColor(Color.parseColor("#F59E0B"))
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { statusText.text = "❌ Gemini Parsing Error"; statusText.setTextColor(Color.parseColor("#EF4444")) }
                        }
                    } else {
                        val errMsg = try { JSONObject(responseStr ?: "").getJSONObject("error").getString("message") } catch(e:Exception){ "HTTP ${response.code}" }
                        withContext(Dispatchers.Main) { statusText.text = "❌ API Error: $errMsg"; statusText.setTextColor(Color.parseColor("#EF4444")) }
                    }
                    audioFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText.text = "❌ Error: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f }
                }
            }
        }

        // Add views to layout
        mainLayout.addView(langToggleRow)
        mainLayout.addView(titleText)
        mainLayout.addView(apiKeyInput)
        mainLayout.addView(styleSpinner)
        
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        mainLayout.addView(spacer)
        
        mainLayout.addView(btnPickVideo)
        mainLayout.addView(btnGenerate)
        mainLayout.addView(statusText)

        setContentView(mainLayout)
    }

    private fun setSpinnerAdapter(spinner: Spinner, items: Array<String>) {
        val adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE); view.textSize = 14f; view.setPadding(30, 30, 30, 30); view.gravity = Gravity.CENTER
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.parseColor("#E2E8F0")); view.setBackgroundColor(Color.parseColor("#151C2C")); view.setPadding(40, 40, 40, 40)
                return view
            }
        }
        spinner.adapter = adapter
    }

    private fun autoSaveTextFile(fileName: String, content: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AutoSRT")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun extractAudioFromVideo(videoUri: Uri, outputPath: String, onProgress: suspend (Int) -> Unit): Boolean {
        val extractor = MediaExtractor()
        try {
            contentResolver.openFileDescriptor(videoUri, "r")?.fileDescriptor?.let { extractor.setDataSource(it) } ?: return false

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { audioTrackIndex = i; break }
            }
            if (audioTrackIndex == -1) return false

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val durationUs = try { format.getLong(MediaFormat.KEY_DURATION) } catch (e: Exception) { 0L }
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            val bufferSize = try { val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE); if (size > 0) size else 1024 * 1024 } catch (e: Exception) { 1024 * 1024 }
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var lastProgress = -1

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0; bufferInfo.size = sampleSize; bufferInfo.flags = extractor.sampleFlags; bufferInfo.presentationTimeUs = extractor.sampleTime
                if (durationUs > 0) {
                    val progress = ((bufferInfo.presentationTimeUs.toDouble() / durationUs.toDouble()) * 100).toInt()
                    if (progress > lastProgress && progress <= 100) { onProgress(progress); lastProgress = progress }
                }
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            
            onProgress(100); muxer.stop(); muxer.release(); extractor.release()
            return true
        } catch (e: Exception) { e.printStackTrace(); return false }
    }
}
