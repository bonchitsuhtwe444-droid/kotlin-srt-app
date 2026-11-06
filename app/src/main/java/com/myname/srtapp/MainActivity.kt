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
    
    // Settings Parameters
    private var selectedStyle = "Standard"
    private var selectedPlatform = "YouTube" 
    private var selectedMode = "Normal"
    private var selectedSecLang = "English"

    // UI Elements
    private lateinit var titleText: TextView
    private lateinit var subTitleText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var btnPickVideo: Button
    private lateinit var btnGenerate: Button
    private lateinit var styleSpinner: Spinner
    private lateinit var platformSpinner: Spinner
    private lateinit var modeSpinner: Spinner
    private lateinit var langSpinner: Spinner
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
        ),
        "modes" to mapOf(
            true to arrayOf("📄 Normal Mode (မြန်မာသီးသန့်)", "📑 Dual Subtitles (၂ မျိုးတွဲ)"),
            false to arrayOf("📄 Normal Mode (Myanmar Only)", "📑 Dual Subtitles (Bilingual)")
        ),
        "sec_langs" to mapOf(
            true to arrayOf("🇬🇧 + အင်္ဂလိပ် (English) တွဲမည်", "🇨🇳 + တရုတ် (Chinese) တွဲမည်", "🇰🇷 + ကိုရီးယား (Korean) တွဲမည်", "🇯🇵 + ဂျပန် (Japanese) တွဲမည်", "🇹🇭 + ထိုင်း (Thai) တွဲမည်"),
            false to arrayOf("🇬🇧 + English", "🇨🇳 + Chinese", "🇰🇷 + Korean", "🇯🇵 + Japanese", "🇹🇭 + Thai")
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
            setPadding(50, 60, 50, 70)
        }

        // Top Bar (Language + Settings)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        
        val topSpacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        
        val btnSettings = Button(this).apply {
            text = "⚙️"
            setTextColor(Color.WHITE)
            textSize = 14f
            background = createGradientDrawable("#1E293B", "#1E293B", 20f, 2, "#475569")
            setPadding(20, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 110).apply { marginEnd = 20 }
            setOnClickListener { showSettingsDialog() }
        }
        
        val btnLang = Button(this).apply {
            text = "🌐 ENG"
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = createGradientDrawable("#1E293B", "#1E293B", 20f, 2, "#475569")
            setPadding(30, 10, 30, 10)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 110)
        }
        
        topBar.addView(topSpacer)
        topBar.addView(btnSettings)
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

        // Settings Card
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

        modeSpinner = Spinner(this).apply {
            background = createGradientDrawable("#0F172A", "#0F172A", 20f, 2, "#3B82F6") // Highlight Mode Spinner
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
        }
        setSpinnerAdapter(modeSpinner, arrayLabels["modes"]!![isMyanmar]!!)
        
        langSpinner = Spinner(this).apply {
            background = createGradientDrawable("#0F172A", "#0F172A", 20f, 2, "#10B981") // Green border for language
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
            visibility = View.GONE // Default Hide
        }
        setSpinnerAdapter(langSpinner, arrayLabels["sec_langs"]!![isMyanmar]!!)
        
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedMode = if (pos == 1) "Dual" else "Normal"
                langSpinner.visibility = if (selectedMode == "Dual") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        
        langSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedSecLang = when(pos) { 
                    1 -> "Chinese"
                    2 -> "Korean"
                    3 -> "Japanese"
                    4 -> "Thai"
                    else -> "English"
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
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
        settingsCard.addView(modeSpinner)
        settingsCard.addView(langSpinner)
        settingsCard.addView(styleSpinner)
        settingsCard.addView(platformSpinner)

        // Video Card
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
            setSpinnerAdapter(modeSpinner, arrayLabels["modes"]!![isMyanmar]!!)
            setSpinnerAdapter(langSpinner, arrayLabels["sec_langs"]!![isMyanmar]!!)
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
                    
                    // --- AI ကို အတင်းအကျပ် ဘာသာပြန်ခိုင်းမည့် တိကျသော Prompt အသစ် (Language Leak ကာကွယ်ရန်) ---
                    val srtTaskInstruction = if (selectedMode == "Dual") {
                        """
                        For EVERY subtitle block, provide EXACTLY TWO lines of text:
                        Line 1: Translated directly into $selectedSecLang language.
                        Line 2: Translated directly into Myanmar (Burmese) language using a $selectedStyle tone.
                        CRITICAL: DO NOT output the original spoken language (e.g. Chinese characters) unless it is EXACTLY the requested target language.
                        """.trimIndent()
                    } else {
                        """
                        For EVERY subtitle block, translate the spoken words directly into Myanmar (Burmese) language using a $selectedStyle tone.
                        CRITICAL: Output ONLY in Myanmar script. DO NOT output Chinese characters or the original spoken language.
                        """.trimIndent()
                    }
                    
                    val aiPrompt = """
                        STRICT INSTRUCTIONS: You are a professional subtitler. You MUST completely ignore the original language text and ONLY output the requested translations. If you output original language text (like Chinese characters) when Burmese or English is requested, you fail.
                        
                        Task 1: Generate a perfectly timed SRT file from the audio.
                        $srtTaskInstruction
                        
                        Task 2: Generate highly engaging SEO metadata specifically optimized for $selectedPlatform.
                        CRITICAL: The Titles, Description, and Hashtags MUST be written ENTIRELY in Myanmar (Burmese) language. No other languages allowed here.
                        
                        You MUST return your EXACT response in the following format with delimiters:
                        
                        ===SRT===
                        [Insert raw SRT text here]
                        ===METADATA===
                        📌 $selectedPlatform Title:
                        [Write 2-3 engaging titles in Myanmar (Burmese) language]
                        
                        📝 Description / Caption:
                        [Write a description in Myanmar (Burmese) language]
                        
                        🏷️ Hashtags:
                        [Write trending hashtags in Myanmar (Burmese) language]
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

    // --- Settings / CapCut Tutorial Dialogs ---
    private fun showSettingsDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 60)
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        
        val title = TextView(this).apply {
            text = "⚙️ Settings"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        
        val capcutBtn = Button(this).apply {
            text = "🎬 CapCut တွင် SRT ဖိုင်ထည့်နည်း"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 15f
            background = createGradientDrawable("#3B82F6", "#2563EB", 20f, 0, "")
            setPadding(0, 40, 0, 40)
        }
        
        layout.addView(title)
        layout.addView(capcutBtn)
        
        builder.setView(layout)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        capcutBtn.setOnClickListener {
            dialog.dismiss()
            showCapCutTutorialDialog()
        }
        
        dialog.show()
    }
    
    private fun showCapCutTutorialDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 60)
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        
        val title = TextView(this).apply {
            text = "📱 CapCut တွင် ဖုန်းဖြင့် SRT ထည့်နည်း"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, 40)
        }
        
        val steps = TextView(this).apply {
            text = "၁။ ဖုန်းထဲမှ CapCut ကို ဖွင့်ပြီး 'New Project' ကနေ မိမိဗီဒီယိုကို ရွေးပါ။\n\n" +
                   "၂။ အောက်ခြေ Menu တန်းမှ 'Text' ကို နှိပ်ပါ။\n\n" +
                   "၃။ 'Auto Captions' (သို့မဟုတ်) 'Subtitles' ကို နှိပ်ပြီး 'Local Subtitles' ကို ရွေးပါ။\n\n" +
                   "၄။ 'Import' ကို နှိပ်ပြီး ဖုန်းထဲက Downloads/AutoSRT ဖိုဒါထဲရှိ ခုနကထုတ်ထားသော .srt ဖိုင်ကို ရွေးထည့်ပါ။\n\n" +
                   "၅။ ဗီဒီယိုပေါ်တွင် စာတန်းထိုးများ ပေါ်လာပါမည်။ အရောင်၊ ဖောင့်နှင့် အရွယ်အစားကို 'Style' ထဲတွင် စိတ်ကြိုက် ပြင်ဆင်နိုင်ပါသည်။"
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            setLineSpacing(0f, 1.4f)
        }
        
        val closeBtn = Button(this).apply {
            text = "နားလည်ပါပြီ"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = createGradientDrawable("#10B981", "#059669", 20f, 0, "")
            setPadding(0, 35, 0, 35)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 50 }
        }
        
        layout.addView(title)
        layout.addView(steps)
        layout.addView(closeBtn)
        
        builder.setView(layout)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        closeBtn.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }
    
    // --- Helper Functions ---
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
