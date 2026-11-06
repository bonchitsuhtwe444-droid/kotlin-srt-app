package com.myname.srtapp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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

    // Root Views for View Switching
    private lateinit var rootLayout: FrameLayout
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var resultLayout: LinearLayout

    // Main UI Elements
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

    // Result UI Elements
    private lateinit var resultPlatformTitle: TextView
    private lateinit var resultFilePathText: TextView
    private lateinit var resultMetadataInput: EditText

    private var isMyanmar = true

    private val textLabels: Map<String, Map<Boolean, String>> = mapOf(
        "title" to mapOf(true to "CREATOR STUDIO", false to "CREATOR STUDIO"),
        "subtitle" to mapOf(true to "AI SRT & Metadata Generator (Platinum Edition)", false to "AI SRT & Metadata Generator (Platinum Edition)"),
        "api_hint" to mapOf(true to "🔑 Gemini API Key", false to "🔑 API Key"),
        "pick_vid" to mapOf(true to "📁 ဗီဒီယို ဖိုင်ရွေးချယ်ပါ", false to "📁 Select Video File"),
        "vid_ready" to mapOf(true to "✅ ဗီဒီယို အသင့်ဖြစ်ပါပြီ", false to "✅ Video Ready"),
        "vid_none" to mapOf(true to "ဗီဒီယို မရွေးရသေးပါ", false to "No video selected"),
        "gen_btn" to mapOf(true to "🚀 ဂျင်နရိတ် လုပ်မည်", false to "🚀 Generate Now"),
        "ready" to mapOf(true to "အသင့်ဖြစ်ပါပြီ... စတင်ရန် ခလုတ်နှိပ်ပါ။", false to "Ready... Press generate to start.")
    )

    private val arrayLabels: Map<String, Map<Boolean, Array<String>>> = mapOf(
        "styles" to mapOf(
            true to arrayOf("🎬 ပုံမှန်", "💬 အလွတ်သဘော", "🔥 အက်ရှင်", "😂 ဟာသ"),
            false to arrayOf("🎬 Standard", "💬 Informal", "🔥 Action", "😂 Comedy")
        ),
        "platforms" to mapOf(
            true to arrayOf("▶️ YouTube", "🎵 TikTok", "📘 Facebook"),
            false to arrayOf("▶️ YouTube", "🎵 TikTok", "📘 Facebook")
        ),
        "modes" to mapOf(
            true to arrayOf("📄 Normal (မြန်မာ)", "📑 Dual (၂ မျိုးတွဲ)"),
            false to arrayOf("📄 Normal", "📑 Dual")
        ),
        "sec_langs" to mapOf(
            true to arrayOf("🇬🇧 အင်္ဂလိပ်", "🇨🇳 တရုတ်", "🇰🇷 ကိုရီးယား", "🇯🇵 ဂျပန်", "🇹🇭 ထိုင်း"),
            false to arrayOf("🇬🇧 English", "🇨🇳 Chinese", "🇰🇷 Korean", "🇯🇵 Japanese", "🇹🇭 Thai")
        )
    )

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            videoStatusText.text = textLabels["vid_ready"]!![isMyanmar]
            videoStatusText.setTextColor(Color.parseColor("#4ADE80"))
            btnPickVideo.background = createGradientDrawable("#1E293B", "#334155", 25f, 2, "#4ADE80") // Green border on ready
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root FrameLayout - No ScrollView
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#090E17")) // Deep Platinum Dark Base
        }

        // ==========================================
        // 1. MAIN SCREEN (Fixed Layout)
        // ==========================================
        mainContentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(40, 50, 40, 50)
            weightSum = 10f // Use weights for responsive fixed layout
        }

        // --- Top Bar (Weight: 1) ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // ☕ Developer Tip Button (Facebook Link)
        val btnTip = Button(this).apply {
            text = "☕ မုန့်ဖိုးပေးမည်"
            isAllCaps = false
            setTextColor(Color.parseColor("#FCD34D")) // Gold/Amber text
            textSize = 12f
            background = createGradientDrawable("#1E293B", "#1E293B", 20f, 1, "#F59E0B") // Gold Border
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply { topMargin=10; bottomMargin=10 }
            setPadding(30, 10, 30, 10)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/profile.php?id=61593224578740"))
                startActivity(intent)
            }
        }

        val topSpacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

        val btnSettings = Button(this).apply {
            text = "⚙️" // Icon only for cleaner look
            setTextColor(Color.WHITE)
            textSize = 16f
            background = createGradientDrawable("#1E293B", "#1E293B", 20f, 1, "#475569")
            layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.MATCH_PARENT).apply { marginEnd = 15; topMargin=10; bottomMargin=10}
            setOnClickListener { showSettingsDialog() }
        }

        val btnLang = Button(this).apply {
            text = "ENG"
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = createGradientDrawable("#1E293B", "#1E293B", 20f, 1, "#475569")
            layoutParams = LinearLayout.LayoutParams(130, LinearLayout.LayoutParams.MATCH_PARENT).apply{ topMargin=10; bottomMargin=10}
        }

        topBar.addView(btnTip)
        topBar.addView(topSpacer)
        topBar.addView(btnSettings)
        topBar.addView(btnLang)

        // --- Header Section (Weight: 1.5) ---
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f)
        }

        titleText = TextView(this).apply {
            text = textLabels["title"]!![isMyanmar]
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0")) // Platinum text
            gravity = Gravity.CENTER_HORIZONTAL
        }

        subTitleText = TextView(this).apply {
            text = textLabels["subtitle"]!![isMyanmar]
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        headerLayout.addView(titleText)
        headerLayout.addView(subTitleText)

        // --- Settings Area (Weight: 4.5) ---
        val settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 4.5f)
        }

        apiKeyInput = EditText(this).apply {
            hint = textLabels["api_hint"]!![isMyanmar]
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#334155")
            setPadding(40, 0, 40, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = 20 }
        }

        // Row 1: Mode & Language
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = 20 }
            weightSum = 2f
        }

        modeSpinner = Spinner(this).apply {
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#334155")
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = 10 }
        }
        setSpinnerAdapter(modeSpinner, arrayLabels["modes"]!![isMyanmar]!!)

        langSpinner = Spinner(this).apply {
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#334155")
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
            visibility = View.INVISIBLE // Reserve space, hide content initially
        }
        setSpinnerAdapter(langSpinner, arrayLabels["sec_langs"]!![isMyanmar]!!)

        row1.addView(modeSpinner)
        row1.addView(langSpinner)

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedMode = if (pos == 1) "Dual" else "Normal"
                langSpinner.visibility = if (selectedMode == "Dual") View.VISIBLE else View.INVISIBLE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        langSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedSecLang = when(pos) { 1 -> "Chinese"; 2 -> "Korean"; 3 -> "Japanese"; 4 -> "Thai"; else -> "English" }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Row 2: Style & Platform
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = 20 }
            weightSum = 2f
        }

        styleSpinner = Spinner(this).apply {
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#334155")
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = 10 }
        }
        setSpinnerAdapter(styleSpinner, arrayLabels["styles"]!![isMyanmar]!!)
        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedStyle = when(pos) { 1 -> "Informal"; 2 -> "Action"; 3 -> "Comedy"; else -> "Standard professional" }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        platformSpinner = Spinner(this).apply {
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#334155")
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 10 }
        }
        setSpinnerAdapter(platformSpinner, arrayLabels["platforms"]!![isMyanmar]!!)
        platformSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedPlatform = when(pos) { 1 -> "TikTok"; 2 -> "Facebook"; else -> "YouTube" }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        row2.addView(styleSpinner)
        row2.addView(platformSpinner)

        // Video Pick Area
        val videoPickContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#334155")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f)
        }

        btnPickVideo = Button(this).apply {
            text = textLabels["pick_vid"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.parseColor("#E2E8F0")); textSize = 14f
            background = null // Transparent, container handles background
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { pickVideoLauncher.launch("video/*") }
        }

        videoStatusText = TextView(this).apply {
            text = textLabels["vid_none"]!![isMyanmar]
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = 11f
        }

        videoPickContainer.addView(btnPickVideo)
        videoPickContainer.addView(videoStatusText)

        settingsLayout.addView(apiKeyInput)
        settingsLayout.addView(row1)
        settingsLayout.addView(row2)
        settingsLayout.addView(videoPickContainer)


        // --- Action Area (Weight: 3) ---
        val actionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f)
        }

        btnGenerate = Button(this).apply {
            text = textLabels["gen_btn"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
            // Platinum style gradient button
            background = createGradientDrawable("#3B82F6", "#1D4ED8", 25f, 0, "")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150).apply { bottomMargin = 20 }
            elevation = 8f
        }

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#1E293B")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120)
        }

        statusText = TextView(this).apply {
            text = textLabels["ready"]!![isMyanmar]
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            textSize = 12f
        }
        statusCard.addView(statusText)

        actionLayout.addView(btnGenerate)
        actionLayout.addView(statusCard)

        // Add all to Main Layout
        mainContentLayout.addView(topBar)
        mainContentLayout.addView(headerLayout)
        mainContentLayout.addView(settingsLayout)
        mainContentLayout.addView(actionLayout)


        // ==========================================
        // 2. RESULT SCREEN (Fixed Layout)
        // ==========================================
        resultLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(40, 50, 40, 50)
            weightSum = 10f
            visibility = View.GONE
        }

        // Header (Weight 1.5)
        val resultHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f)
        }
        val successIcon = TextView(this).apply {
            text = "🎉 အောင်မြင်ပါသည်!"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#4ADE80"))
            gravity = Gravity.CENTER
        }
        resultHeader.addView(successIcon)

        // File Info Card (Weight 1.5)
        val fileInfoCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGradientDrawable("#151C2C", "#151C2C", 20f, 1, "#334155")
            setPadding(30, 20, 30, 20)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f).apply { bottomMargin = 20 }
        }

        val folderIcon = TextView(this).apply {
            text = "📁"
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 20 }
        }

        val fileTextsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val saveLocTitle = TextView(this).apply {
            text = "SRT ဖိုင်သိမ်းဆည်းထားသော နေရာ:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
        }

        resultFilePathText = TextView(this).apply {
            text = "Downloads/AutoSRT/..."
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }

        fileTextsLayout.addView(saveLocTitle)
        fileTextsLayout.addView(resultFilePathText)
        fileInfoCard.addView(folderIcon)
        fileInfoCard.addView(fileTextsLayout)

        // Metadata Card (Weight 5.5) - This inner part can scroll
        val resultMetaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGradientDrawable("#151C2C", "#151C2C", 25f, 1, "#334155")
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 5.5f).apply { bottomMargin = 20 }
        }

        resultPlatformTitle = TextView(this).apply {
            text = "▶️ YouTube Metadata"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        val scrollMetaWrapper = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }

        resultMetadataInput = EditText(this).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            background = null
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            isFocusable = true
            isFocusableInTouchMode = true
            keyListener = null
        }

        scrollMetaWrapper.addView(resultMetadataInput)
        resultMetaCard.addView(resultPlatformTitle)
        resultMetaCard.addView(scrollMetaWrapper)

        // Action Row (Weight 1.5)
        val resultActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f)
        }

        val btnBack = Button(this).apply {
            text = "🔙 ပြန်ထွက်မည်"
            isAllCaps = false; setTextColor(Color.parseColor("#E2E8F0")); textSize = 13f
            background = createGradientDrawable("#1E293B", "#1E293B", 20f, 1, "#475569")
            layoutParams = LinearLayout.LayoutParams(0, 130, 1f).apply { marginEnd = 10 }
            setOnClickListener {
                resultLayout.visibility = View.GONE
                mainContentLayout.visibility = View.VISIBLE
                statusText.text = textLabels["ready"]!![isMyanmar]
                statusText.setTextColor(Color.parseColor("#94A3B8"))
                btnGenerate.isEnabled = true
                btnGenerate.alpha = 1.0f
            }
        }

        val btnCopy = Button(this).apply {
            text = "📋 Copy All"
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 13f; setTypeface(null, Typeface.BOLD)
            background = createGradientDrawable("#10B981", "#059669", 20f, 0, "")
            layoutParams = LinearLayout.LayoutParams(0, 130, 1f).apply { marginStart = 10 }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Metadata", resultMetadataInput.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "စာသားများ Copy ကူးယူပြီးပါပြီ!", Toast.LENGTH_SHORT).show()
            }
        }

        resultActionRow.addView(btnBack)
        resultActionRow.addView(btnCopy)

        resultLayout.addView(resultHeader)
        resultLayout.addView(fileInfoCard)
        resultLayout.addView(resultMetaCard)
        resultLayout.addView(resultActionRow)


        // ==========================================
        // ADD TO ROOT
        // ==========================================
        rootLayout.addView(mainContentLayout)
        rootLayout.addView(resultLayout)
        setContentView(rootLayout)

        // Language Toggle Logic
        btnLang.setOnClickListener {
            isMyanmar = !isMyanmar
            btnLang.text = if (isMyanmar) "ENG" else "မြန်မာ"
            titleText.text = textLabels["title"]!![isMyanmar]
            subTitleText.text = textLabels["subtitle"]!![isMyanmar]
            apiKeyInput.hint = textLabels["api_hint"]!![isMyanmar]
            btnPickVideo.text = textLabels["pick_vid"]!![isMyanmar]
            btnGenerate.text = textLabels["gen_btn"]!![isMyanmar]

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

        // Main AI Generation Logic
        btnGenerate.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty() || selectedVideoUri == null) {
                statusText.text = if (isMyanmar) "⚠️ API Key နှင့် Video ဖိုင် လိုအပ်ပါသည်။" else "⚠️ API Key & Video required."
                statusText.setTextColor(Color.parseColor("#F87171"))
                return@setOnClickListener
            }

            btnGenerate.isEnabled = false; btnGenerate.alpha = 0.5f

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

                    val srtTaskInstruction = if (selectedMode == "Dual") {
                        """
                        For EVERY subtitle block, provide EXACTLY TWO lines of text:
                        Line 1: Translated directly into $selectedSecLang language.
                        Line 2: Translated directly into Myanmar (Burmese) language using a $selectedStyle tone.
                        CRITICAL SRT FORMATTING FOR CAPCUT:
                        - Format must be standard SubRip (.srt).
                        - No extra empty lines within a single subtitle block.
                        - Just ONE line break between Line 1 and Line 2.
                        - Do NOT add multiple line breaks inside Burmese text.
                        - Use standard time format: 00:00:00,000 --> 00:00:00,000
                        """.trimIndent()
                    } else {
                        """
                        For EVERY subtitle block, translate the spoken words directly into Myanmar (Burmese) language using a $selectedStyle tone.
                        CRITICAL SRT FORMATTING FOR CAPCUT:
                        - Format must be standard SubRip (.srt).
                        - No extra empty lines within a single subtitle block.
                        - Output ONLY in Myanmar script.
                        - Use standard time format: 00:00:00,000 --> 00:00:00,000
                        """.trimIndent()
                    }

                    // --- Content Moderation Rules Added ---
                    val aiPrompt = """
                        STRICT INSTRUCTIONS: You are a professional subtitler. You MUST completely ignore the original language text and ONLY output the requested translations.
                        
                        CRITICAL CONTENT MODERATION (YELLOW DOLLAR PROTECTION):
                        - You MUST aggressively sanitize all Burmese translations and metadata.
                        - Replace violent, adult, or sensitive words (e.g., သတ်တယ်, သွေးထွက်တယ်, မုဒိမ်းကျင့်တယ်, သေတယ်) with monetization-friendly, safe alternatives (e.g., ရှင်းလင်းတယ်, ထိခိုက်သွားတယ်, အနိုင်ကျင့်ခံရတယ်, ဆုံးပါးသွားတယ်).
                        - Ensure 100% compliance with YouTube/TikTok advertiser-friendly guidelines.
                        
                        Task 1: Generate a perfectly timed SRT file from the audio.
                        $srtTaskInstruction
                        
                        Task 2: Generate highly engaging SEO metadata specifically optimized for $selectedPlatform.
                        CRITICAL: The Titles, Description, and Hashtags MUST be written ENTIRELY in Myanmar (Burmese) language and be completely safe/monetization-friendly.
                        
                        You MUST return your EXACT response in the following format with delimiters:
                        
                        ===SRT===
                        [Insert raw SRT text here following the strict CapCut format]
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

                            srtContent = srtContent.replace(Regex("\n{3,}"), "\n\n")

                            val timestamp = System.currentTimeMillis()
                            val fileName = "AutoSRT_$timestamp.srt"
                            val srtSaved = autoSaveFile(fileName, "application/x-subrip", srtContent)

                            withContext(Dispatchers.Main) {
                                if (srtSaved) {
                                    // Switch to Result Screen (No scroll needed)
                                    mainContentLayout.visibility = View.GONE
                                    resultLayout.visibility = View.VISIBLE

                                    val pfIcon = when (selectedPlatform) {
                                        "YouTube" -> "▶️ YouTube"
                                        "TikTok" -> "🎵 TikTok"
                                        else -> "📘 Facebook"
                                    }
                                    resultPlatformTitle.text = "$pfIcon Metadata"
                                    resultMetadataInput.setText(metaContent)
                                    resultFilePathText.text = "Downloads/AutoSRT/$fileName"
                                } else {
                                    statusText.text = if (isMyanmar) "⚠️ ဖုန်းထဲသိမ်း၍မရပါ။ Storage Permission လိုအပ်ပါသည်။" else "⚠️ Save Failed. Storage Permission required."
                                    statusText.setTextColor(Color.parseColor("#F59E0B"))
                                    btnGenerate.isEnabled = true
                                    btnGenerate.alpha = 1.0f
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                statusText.text = "❌ Gemini Parsing Error"; statusText.setTextColor(Color.parseColor("#F87171"))
                                btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f
                            }
                        }
                    } else {
                        val errMsg = try { JSONObject(responseStr ?: "").getJSONObject("error").getString("message") } catch(e:Exception){ "HTTP ${response.code}" }
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ API Error: $errMsg"; statusText.setTextColor(Color.parseColor("#F87171"))
                            btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f
                        }
                    }
                    audioFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "❌ Error: ${e.message}"; statusText.setTextColor(Color.parseColor("#F87171"))
                        btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f
                    }
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 60)
            setBackgroundColor(Color.parseColor("#1E293B"))
        }

        val title = TextView(this).apply {
            text = "⚙️ Settings & Help"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }

        val capcutBtn = Button(this).apply {
            text = "🎬 CapCut တွင် SRT ဖိုင်ထည့်နည်း"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
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
            textSize = 16f
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
            textSize = 14f
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
                view.setTextColor(Color.parseColor("#E2E8F0")); view.textSize = 13f; view.setPadding(10, 20, 10, 20)
                view.gravity = Gravity.CENTER
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
