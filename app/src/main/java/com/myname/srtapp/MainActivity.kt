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
    private var selectedStyle = "Standard"
    private var selectedPlatform = "YouTube" 

    private lateinit var titleText: TextView
    private lateinit var subTitleText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var btnPickVideo: Button
    private lateinit var btnGenerate: Button
    private lateinit var styleSpinner: Spinner
    private lateinit var platformSpinner: Spinner
    private lateinit var videoStatusText: TextView
    
    private lateinit var metadataCard: LinearLayout
    private lateinit var metadataTitle: TextView
    private lateinit var metadataInput: EditText

    private var isMyanmar = true 

    private val textLabels: Map<String, Map<Boolean, String>> = mapOf(
        "title" to mapOf(true to "CREATOR STUDIO", false to "CREATOR STUDIO"),
        "subtitle" to mapOf(true to "AI SRT & Metadata Generator", false to "AI SRT & Metadata Generator"),
        "api_hint" to mapOf(true to "🔑 Gemini API Key ထည့်ပါ", false to "🔑 Enter Gemini API Key"),
        "pick_vid" to mapOf(true to "📁 ဗီဒီယို ဖိုင်ရွေးချယ်ပါ", false to "📁 Select Video File"),
        "vid_ready" to mapOf(true to "✅ ဗီဒီယို အသင့်ဖြစ်ပါပြီ", false to "✅ Video Ready"),
        "vid_none" to mapOf(true to "ဗီဒီယို မရွေးရသေးပါ", false to "No video selected"),
        "gen_btn" to mapOf(true to "🚀 ဂျင်နရိတ် လုပ်မည်", false to "🚀 Generate Now"),
        "ready" to mapOf(true to "အသင့်ဖြစ်ပါပြီ... စတင်ရန် ခလုတ်နှိပ်ပါ။", false to "Ready... Press generate to start."),
        "meta_title" to mapOf(true to "📝 ဆိုရှယ်မီဒီယာ စာသားများ (Copy ယူနိုင်ပါသည်)", false to "📝 Social Media Metadata (Ready to copy)")
    )

    private val arrayLabels: Map<String, Map<Boolean, Array<String>>> = mapOf(
        "styles" to mapOf(
            true to arrayOf("🎬 ပုံမှန် (Standard)", "💬 အလွတ်သဘော (Informal)", "🔥 အက်ရှင် (Action)", "😂 ဟာသ (Comedy)"),
            false to arrayOf("🎬 Standard", "💬 Informal", "🔥 Action", "😂 Comedy")
        ),
        "platforms" to mapOf(
            true to arrayOf("▶️ YouTube အတွက်", "🎵 TikTok အတွက်", "📘 Facebook အတွက်"),
            false to arrayOf("▶️ For YouTube", "🎵 For TikTok", "📘 For Facebook")
        )
    )

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            videoStatusText.text = textLabels["vid_ready"]!![isMyanmar]
            videoStatusText.setTextColor(Color.parseColor("#4ADE80")) 
            btnPickVideo.background = createGradientDrawable("#1E293B", "#334155", 25f, 3, "#4ADE80")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#0F172A")) 
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(50, 70, 50, 70)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        
        val btnLang = Button(this).apply {
            text = "🌐 ENG"
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = createGradientDrawable("#1E293B", "#1E293B", 20f, 2, "#475569")
            setPadding(30, 10, 30, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 100)
        }
        topBar.addView(btnLang)

        titleText = TextView(this).apply {
            text = textLabels["title"]!![isMyanmar]
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        subTitleText = TextView(this).apply {
            text = textLabels["subtitle"]!![isMyanmar]
            textSize = 14f
            setTextColor(Color.parseColor("#38BDF8")) 
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 60 }
        }

        val settingsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGradientDrawable("#1E293B", "#1E293B", 30f, 0, "")
            setPadding(40, 50, 40, 50)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            elevation = 10f
        }

        apiKeyInput = EditText(this).apply {
            hint = textLabels["api_hint"]!![isMyanmar]
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = createGradientDrawable("#0F172A", "#0F172A", 20f, 2, "#334155")
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
        }

        styleSpinner = Spinner(this).apply {
            background = createGradientDrawable("#0F172A", "#0F172A", 20f, 2, "#334155")
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
        }
        setSpinnerAdapter(styleSpinner, arrayLabels["styles"]!![isMyanmar]!!)
        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedStyle = when(pos) { 1 -> "Informal"; 2 -> "Action"; 3 -> "Comedy"; else -> "Standard professional" }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        platformSpinner = Spinner(this).apply {
            background = createGradientDrawable("#0F172A", "#0F172A", 20f, 2, "#334155")
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        setSpinnerAdapter(platformSpinner, arrayLabels["platforms"]!![isMyanmar]!!)
        platformSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedPlatform = when(pos) { 1 -> "TikTok"; 2 -> "Facebook"; else -> "YouTube" }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        
        settingsCard.addView(apiKeyInput)
        settingsCard.addView(styleSpinner)
        settingsCard.addView(platformSpinner)

        val videoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGradientDrawable("#1E293B", "#1E293B", 30f, 0, "")
            setPadding(40, 50, 40, 50)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 60 }
            elevation = 10f
        }

        btnPickVideo = Button(this).apply {
            text = textLabels["pick_vid"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 15f
            background = createGradientDrawable("#334155", "#475569", 25f, 0, "")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
            setPadding(0, 40, 0, 40)
            setOnClickListener { pickVideoLauncher.launch("video/*") }
        }

        videoStatusText = TextView(this).apply {
            text = textLabels["vid_none"]!![isMyanmar]
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        videoCard.addView(btnPickVideo)
        videoCard.addView(videoStatusText)

        btnGenerate = Button(this).apply {
            text = textLabels["gen_btn"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
            background = createGradientDrawable("#3B82F6", "#2563EB", 30f, 0, "")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            setPadding(0, 45, 0, 45)
            elevation = 15f
        }

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGradientDrawable("#0F172A", "#0F172A", 20f, 2, "#1E293B")
            setPadding(30, 40, 30, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        
        statusText = TextView(this).apply {
            text = textLabels["ready"]!![isMyanmar]
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        statusCard.addView(statusText)

        metadataCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGradientDrawable("#1E293B", "#1E293B", 30f, 0, "")
            setPadding(40, 50, 40, 50)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            elevation = 10f
            visibility = View.GONE
        }

        metadataTitle = TextView(this).apply {
            text = textLabels["meta_title"]!![isMyanmar]
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        metadataInput = EditText(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            background = createGradientDrawable("#0F172A", "#0F172A", 20f, 2, "#334155")
            setPadding(40, 40, 40, 40)
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        metadataCard.addView(metadataTitle)
        metadataCard.addView(metadataInput)

        btnLang.setOnClickListener {
            isMyanmar = !isMyanmar
            btnLang.text = if (isMyanmar) "🌐 ENG" else "🌐 မြန်မာ"
            titleText.text = textLabels["title"]!![isMyanmar]
            subTitleText.text = textLabels["subtitle"]!![isMyanmar]
            apiKeyInput.hint = textLabels["api_hint"]!![isMyanmar]
            btnPickVideo.text = textLabels["pick_vid"]!![isMyanmar]
            btnGenerate.text = textLabels["gen_btn"]!![isMyanmar]
            metadataTitle.text = textLabels["meta_title"]!![isMyanmar]
            
            if (selectedVideoUri == null) {
                videoStatusText.text = textLabels["vid_none"]!![isMyanmar]
            } else {
                videoStatusText.text = textLabels["vid_ready"]!![isMyanmar]
            }

            if (statusText.text.toString().contains("Ready") || statusText.text.toString().contains("အသင့်")) {
                statusText.text = textLabels["ready"]!![isMyanmar]
            }
            setSpinnerAdapter(styleSpinner, arrayLabels["styles"]!![isMyanmar]!!)
            setSpinnerAdapter(platformSpinner, arrayLabels["platforms"]!![isMyanmar]!!)
        }

        btnGenerate.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty() || selectedVideoUri == null) {
                statusText.text = if (isMyanmar) "⚠️ API Key နှင့် Video ဖိုင်ကို ထည့်သွင်းပေးပါ။" else "⚠️ Please provide API Key and Video file."
                statusText.setTextColor(Color.parseColor("#F87171")) 
                return@setOnClickListener
            }

            btnGenerate.isEnabled = false; btnGenerate.alpha = 0.5f
            metadataCard.visibility = View.GONE
            metadataInput.setText("")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val audioFile = File(cacheDir, "extracted_audio.m4a")
                    
                    // Add explicit type Int to progress lambda to fix inference error
                    val extractSuccess = extractAudioFromVideo(selectedVideoUri!!, audioFile.absolutePath) { progress: Int ->
                        withContext(Dispatchers.Main) { 
                            statusText.text = if (isMyanmar) "⏳ အသံထုတ်ယူနေသည်... ($progress%)" else "⏳ Extracting Audio... ($progress%)"
                            statusText.setTextColor(Color.parseColor("#FBBF24")) 
                        }
                    }

                    if (!extractSuccess || !audioFile.exists()) {
                        withContext(Dispatchers.Main) { 
                            statusText.text = if (isMyanmar) "❌ အသံထုတ်ယူမှု ကျရှုံးပါသည်" else "❌ Audio extraction failed"
                            btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f 
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) { statusText.text = if (isMyanmar) "🌐 AI ဘာသာပြန်နေသည်... ခဏစောင့်ပါ" else "🌐 AI is processing... Please wait." }

                    val bytes = audioFile.readBytes()
                    val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    val client = OkHttpClient.Builder()
                        .connectTimeout(0, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS)
                        .writeTimeout(0, TimeUnit.SECONDS)
                        .build()
                        
                    val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val aiPrompt = """
                        You are a professional subtitler and social media SEO expert.
                        Task 1: Generate a perfectly timed SRT file from the audio. Translate directly to Myanmar (Burmese) language using a $selectedStyle tone.
                        Task 2: Generate highly engaging SEO metadata specifically optimized for $selectedPlatform in Myanmar (Burmese) language based on the video context.
                        
                        You MUST return your EXACT response in the following format with delimiters:
                        
                        ===SRT===
                        [Insert raw SRT text here]
                        ===METADATA===
                        📌 $selectedPlatform Title:
                        [Write 2-3 engaging titles for $selectedPlatform]
                        
                        📝 Description / Caption:
                        [Write a suitable description/caption for $selectedPlatform]
                        
                        🏷️ Hashtags:
                        [Write trending hashtags suitable for $selectedPlatform]
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
                            val extractedText = JSONObject(responseStr).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                            
                            var srtContent = ""
                            var metaContent = ""

                            if (extractedText.contains("===METADATA===")) {
                                val parts = extractedText.split("===METADATA===")
                                srtContent = parts[0].replace("===SRT===", "").replace("```srt", "").replace("```", "").trim()
                                metaContent = parts.getOrNull(1)?.replace("```txt", "")?.replace("```", "")?.trim() ?: ""
                            } else {
                                srtContent = extractedText.replace("```srt", "").replace("```", "").trim()
                            }

                            val timestamp = System.currentTimeMillis()
                            val srtSaved = autoSaveFile("AutoSRT_$timestamp.srt", "application/x-subrip", srtContent)

                            withContext(Dispatchers.Main) {
                                if (srtSaved) {
                                    statusText.text = if (isMyanmar) "🎉 အောင်မြင်ပါသည်!\nSRT အား Downloads/AutoSRT တွင် သိမ်းဆည်းပြီးပါပြီ။" else "🎉 Success!\nSaved SRT to Downloads/AutoSRT."
                                    statusText.setTextColor(Color.parseColor("#4ADE80"))
                                    
                                    if (metaContent.isNotEmpty()) {
                                        metadataCard.visibility = View.VISIBLE
                                        metadataInput.setText(metaContent)
                                        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
                                    }
                                } else {
                                    statusText.text = if (isMyanmar) "⚠️ ဖုန်းထဲသိမ်း၍မရပါ။ Storage Permission လိုအပ်ပါသည်။" else "⚠️ Save Failed. Storage Permission required."
                                    statusText.setTextColor(Color.parseColor("#F59E0B"))
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { statusText.text = "❌ Gemini Parsing Error"; statusText.setTextColor(Color.parseColor("#F87171")) }
                        }
                    } else {
                        val errMsg = try { JSONObject(responseStr ?: "").getJSONObject("error").getString("message") } catch(e:Exception){ "HTTP ${response.code}" }
                        withContext(Dispatchers.Main) { statusText.text = "❌ API Error: $errMsg"; statusText.setTextColor(Color.parseColor("#F87171")) }
                    }
                    audioFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText.text = "❌ Error: ${e.message}"; statusText.setTextColor(Color.parseColor("#F87171")) }
                } finally {
                    withContext(Dispatchers.Main) { btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f }
                }
            }
        }

        mainLayout.addView(topBar)
        mainLayout.addView(titleText)
        mainLayout.addView(subTitleText)
        mainLayout.addView(settingsCard)
        mainLayout.addView(videoCard)
        mainLayout.addView(btnGenerate)
        mainLayout.addView(statusCard)
        mainLayout.addView(metadataCard)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    // HELPER FUNCTIONS (These must be inside the class)
    
    private fun createGradientDrawable(startColor: String, endColor: String, radius: Float, strokeWidth: Int, strokeColor: String): GradientDrawable {
        val drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(startColor), Color.parseColor(endColor)))
        drawable.cornerRadius = radius
        if (strokeWidth > 0 && strokeColor.isNotEmpty()) {
            drawable.setStroke(strokeWidth, Color.parseColor(strokeColor))
        }
        return drawable
    }

    private fun setSpinnerAdapter(spinner: Spinner, items: Array<String>) {
        val adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE); view.textSize = 14f; view.setPadding(20, 30, 20, 30)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.parseColor("#E2E8F0")); view.setBackgroundColor(Color.parseColor("#1E293B")); view.setPadding(40, 40, 40, 40)
                return view
            }
        }
        spinner.adapter = adapter
    }

    private fun autoSaveFile(fileName: String, mimeType: String, content: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
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
