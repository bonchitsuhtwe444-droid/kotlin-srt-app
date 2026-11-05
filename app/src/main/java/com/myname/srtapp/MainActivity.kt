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
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var selectedVideoUri: Uri? = null
    private var generatedSrtContent: String = ""
    private var selectedStyle = "Standard"
    private var selectedColor = "#FFFFFF"
    private var selectedRatio = "Original"

    private lateinit var videoView: VideoView
    private lateinit var subtitleOverlay: TextView
    private lateinit var btnExportVideo: Button
    
    private lateinit var titleText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var btnToggleSub: Button
    private lateinit var btnToggleBg: Button
    private lateinit var btnPickVideo: Button
    private lateinit var btnGenerate: Button
    
    private lateinit var styleSpinner: Spinner
    private lateinit var colorSpinner: Spinner
    private lateinit var ratioSpinner: Spinner

    private var isSubVisible = true
    private var isBgVisible = true
    private var isMyanmar = true 

    private var currentScale = 1.0f
    private var dX = 0f
    private var dY = 0f

    private val textLabels: Map<String, Map<Boolean, String>> = mapOf(
        "title" to mapOf(true to "AUTO SRT GEN", false to "AUTO SRT GEN"),
        "api_hint" to mapOf(true to "Gemini API Key ထည့်ပါ", false to "Enter Gemini API Key"),
        "sub_on" to mapOf(true to "💬 စာတန်း: ON", false to "💬 Sub: ON"),
        "sub_off" to mapOf(true to "💬 စာတန်း: OFF", false to "💬 Sub: OFF"),
        "box_on" to mapOf(true to "⬛ နောက်ခံ: ON", false to "⬛ Box: ON"),
        "box_off" to mapOf(true to "⬛ နောက်ခံ: OFF", false to "⬛ Box: OFF"),
        "pick_vid" to mapOf(true to "📁 ဗီဒီယို ရွေးပါ", false to "📁 Select Video"),
        "gen_srt" to mapOf(true to "🚀 SRT ထုတ်မည်", false to "🚀 Generate SRT"),
        "export" to mapOf(true to "🎬 ဗီဒီယို အသစ်ထုတ်မည်", false to "🎬 Export Video (Burn-in)"),
        "ready" to mapOf(true to "အသင့်ဖြစ်ပါပြီ...", false to "Ready to start..."),
        "preview" to mapOf(true to "Subtitle Preview\n(လက်ဖြင့်ရွှေ့နိုင်၊ ချုံ့ချဲ့နိုင်ပါသည်)", false to "Subtitle Preview\n(Drag and zoom manually)")
    )

    private val arrayLabels: Map<String, Map<Boolean, Array<String>>> = mapOf(
        "styles" to mapOf(
            true to arrayOf("ပုံမှန် (Standard)", "အလွတ်သဘော (Informal)", "အက်ရှင် (Action)", "ဟာသ (Comedy)"),
            false to arrayOf("Standard", "Informal", "Action", "Comedy")
        ),
        "colors" to mapOf(
            true to arrayOf("အဖြူ (White)", "အဝါ (Yellow)", "အစိမ်း (Green)", "အပြာနု (Cyan)"),
            false to arrayOf("White", "Yellow", "Green", "Cyan")
        ),
        "ratios" to mapOf(
            true to arrayOf("မူရင်း (Original)", "16:9 (အလျားလိုက်)", "9:16 (ဒေါင်လိုက်)"),
            false to arrayOf("Original", "16:9 (Landscape)", "9:16 (Portrait)")
        )
    )

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            statusText.text = if (isMyanmar) "✅ ဗီဒီယိုဖိုင် ရွေးချယ်ပြီးပါပြီ။" else "✅ Video selected."
            statusText.setTextColor(Color.parseColor("#4ADE80"))
            
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { mp -> 
                mp.isLooping = true 
                videoView.start()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B101E")) 
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(30, 40, 30, 40)
        }

        val langToggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
        }
        val btnLang = Button(this).apply {
            text = "🌐 English"
            isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = GradientDrawable().apply { setColor(Color.parseColor("#334155")); cornerRadius = 15f }
            setPadding(20, 10, 20, 10)
        }
        langToggleRow.addView(btnLang)

        titleText = TextView(this).apply {
            text = textLabels["title"]!![isMyanmar]
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00C6FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

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
            setPadding(30, 25, 30, 25)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
        }

        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
        }

        styleSpinner = Spinner(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#151C2C")); cornerRadius = 20f; setStroke(2, Color.parseColor("#2A344A")) }
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#151C2C")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 5; marginStart = 5 }
        }
        colorSpinner = Spinner(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#151C2C")); cornerRadius = 20f; setStroke(2, Color.parseColor("#2A344A")) }
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#151C2C")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 5; marginStart = 5 }
        }
        ratioSpinner = Spinner(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#151C2C")); cornerRadius = 20f; setStroke(2, Color.parseColor("#2A344A")) }
            setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#151C2C")))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 5; marginStart = 5 }
        }

        setSpinnerAdapter(styleSpinner, arrayLabels["styles"]!![isMyanmar]!!)
        setSpinnerAdapter(colorSpinner, arrayLabels["colors"]!![isMyanmar]!!)
        setSpinnerAdapter(ratioSpinner, arrayLabels["ratios"]!![isMyanmar]!!)

        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedStyle = when(pos) { 1 -> "Informal"; 2 -> "Action"; 3 -> "Comedy"; else -> "Standard professional" }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        colorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedColor = when(pos) { 1 -> "#FFFF00"; 2 -> "#00FF00"; 3 -> "#00FFFF"; else -> "#FFFFFF" }
                if (::subtitleOverlay.isInitialized) subtitleOverlay.setTextColor(Color.parseColor(selectedColor))
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        ratioSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedRatio = when(pos) { 1 -> "16:9"; 2 -> "9:16"; else -> "Original" }
                // No layout resize needed here, VideoView adjusts itself naturally. 
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        spinnerRow.addView(styleSpinner); spinnerRow.addView(colorSpinner); spinnerRow.addView(ratioSpinner)

        val previewFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = 20 }
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = 25f }
            clipToOutline = true
        }

        // [Bug Fix] VideoContainer အသစ် ထည့်သွင်းခြင်း (စာတန်းထိုး အပြင်မထွက်အောင် Video ၏ ပုံမှန်ဆိုဒ်အတိုင်း ထိန်းချုပ်ပေးသည်)
        val videoContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
        }

        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        subtitleOverlay = TextView(this).apply {
            text = textLabels["preview"]!![isMyanmar]
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000")) 
            setPadding(20, 15, 20, 15)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 50
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = Math.max(0.5f, Math.min(currentScale, 3.0f))
                subtitleOverlay.scaleX = currentScale
                subtitleOverlay.scaleY = currentScale
                return true
            }
        })

        // [Performance Fix] စာတန်းထိုး လေးကန်ခြင်းမရှိစေရန် Hardware Acceleration ကိုသုံးပြီး မြန်ဆန်စွာရွှေ့ပေးမည့်စနစ်အသစ်
        subtitleOverlay.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = view.x - event.rawX
                        dY = view.y - event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        var newX = event.rawX + dX
                        var newY = event.rawY + dY
                        
                        val parent = view.parent as View
                        val scaledWidth = view.width * currentScale
                        val scaledHeight = view.height * currentScale
                        val widthDiff = (scaledWidth - view.width) / 2
                        val heightDiff = (scaledHeight - view.height) / 2

                        // Scale ချဲ့ထားသည့်တိုင်အောင် Video အပြင်သို့ လုံးဝထွက်မသွားစေရန် တိကျစွာတွက်ချက်ခြင်း
                        val minX = widthDiff
                        val maxX = parent.width - view.width - widthDiff
                        val minY = heightDiff
                        val maxY = parent.height - view.height - heightDiff

                        if (newX < minX) newX = minX
                        if (newX > maxX) newX = maxX
                        if (newY < minY) newY = minY
                        if (newY > maxY) newY = maxY

                        // .animate().setDuration(0) ဖြင့်ခေါ်ခြင်းက 60fps+ Smooth ဖြစ်စေပါသည်
                        view.animate().x(newX).y(newY).setDuration(0).start()
                    }
                }
            }
            true
        }

        videoContainer.addView(videoView)
        videoContainer.addView(subtitleOverlay)
        previewFrame.addView(videoContainer) // Subtitle သည် VideoContainer အတွင်း၌သာ သေချာစွာ နေမည်ဖြစ်သည်

        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
        }
        
        btnToggleSub = Button(this).apply {
            text = textLabels["sub_on"]!![isMyanmar]; isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E293B")); cornerRadius = 15f }
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { marginEnd = 10 }
        }
        btnToggleBg = Button(this).apply {
            text = textLabels["box_on"]!![isMyanmar]; isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E293B")); cornerRadius = 15f }
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { marginStart = 10 }
        }
        
        btnToggleSub.setOnClickListener {
            isSubVisible = !isSubVisible
            subtitleOverlay.visibility = if (isSubVisible) View.VISIBLE else View.INVISIBLE
            btnToggleSub.text = if (isSubVisible) textLabels["sub_on"]!![isMyanmar] else textLabels["sub_off"]!![isMyanmar]
            btnToggleSub.setTextColor(if (isSubVisible) Color.WHITE else Color.GRAY)
        }
        btnToggleBg.setOnClickListener {
            isBgVisible = !isBgVisible
            subtitleOverlay.setBackgroundColor(if (isBgVisible) Color.parseColor("#99000000") else Color.TRANSPARENT)
            btnToggleBg.text = if (isBgVisible) textLabels["box_on"]!![isMyanmar] else textLabels["box_off"]!![isMyanmar]
            btnToggleBg.setTextColor(if (isBgVisible) Color.WHITE else Color.GRAY)
        }
        toggleRow.addView(btnToggleSub); toggleRow.addView(btnToggleBg)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
        }

        btnPickVideo = Button(this).apply {
            text = textLabels["pick_vid"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#2563EB")); cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            setOnClickListener { pickVideoLauncher.launch("video/*") }
        }

        btnGenerate = Button(this).apply {
            text = textLabels["gen_srt"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#00C6FF"), Color.parseColor("#0072FF"))).apply { cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 10 }
        }
        actionRow.addView(btnPickVideo); actionRow.addView(btnGenerate)

        btnExportVideo = Button(this).apply {
            text = textLabels["export"]!![isMyanmar]
            isAllCaps = false; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#D97706"))).apply { cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
            visibility = View.GONE
        }

        statusText = TextView(this).apply {
            text = textLabels["ready"]!![isMyanmar]
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        btnLang.setOnClickListener {
            isMyanmar = !isMyanmar
            btnLang.text = if (isMyanmar) "🌐 English" else "🌐 မြန်မာ"
            titleText.text = textLabels["title"]!![isMyanmar]
            apiKeyInput.hint = textLabels["api_hint"]!![isMyanmar]
            btnToggleSub.text = if (isSubVisible) textLabels["sub_on"]!![isMyanmar] else textLabels["sub_off"]!![isMyanmar]
            btnToggleBg.text = if (isBgVisible) textLabels["box_on"]!![isMyanmar] else textLabels["box_off"]!![isMyanmar]
            btnPickVideo.text = textLabels["pick_vid"]!![isMyanmar]
            btnGenerate.text = textLabels["gen_srt"]!![isMyanmar]
            btnExportVideo.text = textLabels["export"]!![isMyanmar]
            
            if (statusText.text.toString().contains("Ready") || statusText.text.toString().contains("အသင့်")) {
                statusText.text = textLabels["ready"]!![isMyanmar]
            }
            if (generatedSrtContent.isEmpty()) {
                subtitleOverlay.text = textLabels["preview"]!![isMyanmar]
            }
            
            setSpinnerAdapter(styleSpinner, arrayLabels["styles"]!![isMyanmar]!!)
            setSpinnerAdapter(colorSpinner, arrayLabels["colors"]!![isMyanmar]!!)
            setSpinnerAdapter(ratioSpinner, arrayLabels["ratios"]!![isMyanmar]!!)
        }

        // --- GEMINI 3.5 FLASH LITE API INTEGRATION ---
        btnGenerate.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty() || selectedVideoUri == null) {
                statusText.text = if (isMyanmar) "⚠️ Gemini API Key နှင့် Video ထည့်ပေးပါ!" else "⚠️ Enter Gemini API Key & Video!"
                statusText.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }

            btnGenerate.isEnabled = false; btnGenerate.alpha = 0.5f; btnExportVideo.visibility = View.GONE

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
                        .connectTimeout(60, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build()
                        
                    val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val geminiJson = JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(JSONObject().apply { put("text", "You are an expert subtitler. Listen and generate a perfectly timed SRT file. Translate directly into Myanmar (Burmese) language using a $selectedStyle tone. Return ONLY raw SRT text.") })
                                    put(JSONObject().apply { put("inline_data", JSONObject().apply { put("mime_type", "audio/mp4"); put("data", base64Audio) }) })
                                })
                            })
                        })
                    }

                    val cleanedApiKey = apiKey.trim()
                    // URL ကို သင်တောင်းဆိုထားသည့်အတိုင်း 3.5 flash lite သို့ တိကျစွာ ပြောင်းလဲပေးထားပါသည်
                    val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$cleanedApiKey"
                    
                    val request = Request.Builder().url(urlStr).post(geminiJson.toString().toRequestBody(jsonMediaType)).build()
                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string()

                    if (response.isSuccessful && responseStr != null) {
                        try {
                            var extractedText = JSONObject(responseStr).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                            extractedText = extractedText.replace("```srt", "").replace("```", "").trim()
                            if (selectedColor != "#FFFFFF") extractedText = applyColorToSrt(extractedText, selectedColor)
                            generatedSrtContent = extractedText

                            val isSaved = autoSaveTextFile("AutoSRT_${System.currentTimeMillis()}.srt", generatedSrtContent)

                            withContext(Dispatchers.Main) {
                                if (isSaved) {
                                    statusText.text = if (isMyanmar) "✅ ပြီးစီးပါပြီ! SRT အား Downloads/AutoSRT တွင် သိမ်းပေးလိုက်ပါသည်။" else "✅ Success! SRT saved."
                                    statusText.setTextColor(Color.parseColor("#4ADE80"))
                                    subtitleOverlay.text = "🎉 SRT အဆင်သင့်ဖြစ်ပါပြီ"
                                    btnExportVideo.visibility = View.VISIBLE
                                } else {
                                    statusText.text = if (isMyanmar) "⚠️ ဖုန်းထဲသိမ်း၍မရပါ။" else "⚠️ Save Failed."
                                    statusText.setTextColor(Color.parseColor("#F59E0B"))
                                    btnExportVideo.visibility = View.VISIBLE
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

        btnExportVideo.setOnClickListener {
            if (generatedSrtContent.isEmpty() || selectedVideoUri == null) return@setOnClickListener
            
            if (videoView.isPlaying) videoView.pause()
            
            btnExportVideo.isEnabled = false
            btnExportVideo.text = if (isMyanmar) "⏳ ဗီဒီယိုထုတ်နေသည်..." else "⏳ Rendering..."
            statusText.text = if (isMyanmar) "⚙️ Video အသစ်ထုတ်နေပါသည်...\nApp ကို မပိတ်ဘဲ စောင့်ပေးပါ။" else "⚙️ Rendering new video...\nPlease wait without closing the app."
            statusText.setTextColor(Color.parseColor("#FBBF24"))

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    try { FFmpegKitConfig.setFontDirectory(this@MainActivity, "/system/fonts", emptyMap<String, String>()) } catch (e: Exception) { e.printStackTrace() }

                    val inputVideoFile = File(cacheDir, "input_video.mp4")
                    contentResolver.openInputStream(selectedVideoUri!!)?.use { input ->
                        FileOutputStream(inputVideoFile).use { output -> input.copyTo(output) }
                    }

                    val srtFile = File(cacheDir, "temp_sub.srt")
                    srtFile.writeText(generatedSrtContent)

                    val outputVideoFile = File(cacheDir, "output_video.mp4")
                    if (outputVideoFile.exists()) outputVideoFile.delete()

                    val boxStyle = if (isBgVisible) "BorderStyle=3,BackColour=&H80000000," else ""
                    val safeSrtPath = srtFile.absolutePath.replace("\\", "/").replace(":", "\\:")
                    
                    // [Bug Fix] Video Export တွင် စာတန်းထိုးကပ်ပြီးမှ Scale & Pad လုပ်ခြင်းဖြင့် အမည်းရောင်ဘောင်ပေါ်သို့ စာတန်းထိုးထွက်ခြင်းကို 100% ကာကွယ်ပေးသည်
                    val scaleArg = "scale='trunc(iw/2)*2':'trunc(ih/2)*2'"
                    val subtitleArg = "subtitles=$safeSrtPath:fontsdir=/system/fonts:force_style='${boxStyle}Fontsize=18'"
                    
                    val padArg = when(selectedRatio) {
                        "16:9" -> "scale='if(gt(a,16/9),1280,-1)':'if(gt(a,16/9),-1,720)',pad=1280:720:(ow-iw)/2:(oh-ih)/2"
                        "9:16" -> "scale='if(gt(a,9/16),720,-1)':'if(gt(a,9/16),-1,1280)',pad=720:1280:(ow-iw)/2:(oh-ih)/2"
                        else -> ""
                    }
                    
                    val vfArg = if (padArg.isNotEmpty()) "$scaleArg,$subtitleArg,$padArg" else "$scaleArg,$subtitleArg"
                    
                    val cmdArgs = arrayOf(
                        "-y", "-i", inputVideoFile.absolutePath,
                        "-vf", vfArg,
                        "-c:v", "libx264", "-preset", "ultrafast", "-threads", "2", "-c:a", "copy",
                        outputVideoFile.absolutePath
                    )

                    FFmpegKit.executeAsync(cmdArgs.joinToString(" "), { session ->
                        val returnCode = session.returnCode
                        if (ReturnCode.isSuccess(returnCode)) {
                            val isSaved = autoSaveVideoFile("AutoSRT_${System.currentTimeMillis()}.mp4", outputVideoFile)

                            runOnUiThread {
                                if (isSaved) {
                                    statusText.text = if (isMyanmar) "✅ အောင်မြင်ပါသည်! ဗီဒီယိုကို သိမ်းပေးလိုက်ပါသည်။" else "✅ Success! Video saved."
                                    statusText.setTextColor(Color.parseColor("#4ADE80"))
                                } else {
                                    statusText.text = if (isMyanmar) "❌ ဖိုင်သိမ်းရန် Permission လိုအပ်ပါသည်။" else "❌ Storage Permission needed."
                                    statusText.setTextColor(Color.parseColor("#EF4444"))
                                }
                                btnExportVideo.isEnabled = true
                                btnExportVideo.text = textLabels["export"]!![isMyanmar]
                            }
                        } else {
                            runOnUiThread {
                                statusText.text = if (isMyanmar) "❌ Rendering Failed! (Resolution / RAM မလောက်ငှခြင်း)" else "❌ Rendering Failed!"
                                statusText.setTextColor(Color.parseColor("#EF4444"))
                                btnExportVideo.isEnabled = true
                                btnExportVideo.text = textLabels["export"]!![isMyanmar]
                            }
                        }
                        try { inputVideoFile.delete(); srtFile.delete(); outputVideoFile.delete() } catch(e: Exception){}
                    }, { log -> 
                        // Log
                    }, { statistics -> 
                        val timeSec = statistics.time / 1000
                        runOnUiThread {
                            statusText.text = if (isMyanmar) "⚙️ ရေးဆွဲနေသည်... ($timeSec seconds)" else "⚙️ Rendering... ($timeSec seconds)"
                        }
                    })

                } catch (e: Exception) {
                    runOnUiThread { 
                        statusText.text = "❌ Error: ${e.message}" 
                        btnExportVideo.isEnabled = true
                        btnExportVideo.text = textLabels["export"]!![isMyanmar]
                    }
                }
            }
        }

        mainLayout.addView(langToggleRow)
        mainLayout.addView(titleText)
        mainLayout.addView(apiKeyInput)
        mainLayout.addView(spinnerRow)
        mainLayout.addView(previewFrame) 
        mainLayout.addView(toggleRow)
        mainLayout.addView(actionRow)
        mainLayout.addView(btnExportVideo)
        mainLayout.addView(statusText)

        setContentView(mainLayout)
    }

    private fun setSpinnerAdapter(spinner: Spinner, items: Array<String>) {
        val adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE); view.textSize = 11f; view.setPadding(15, 20, 15, 20); view.gravity = Gravity.CENTER
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.parseColor("#E2E8F0")); view.setBackgroundColor(Color.parseColor("#151C2C")); view.setPadding(30, 30, 30, 30)
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

    private fun autoSaveVideoFile(fileName: String, videoFile: File): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AutoSRT")
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    videoFile.inputStream().use { it.copyTo(out) }
                }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun applyColorToSrt(srtContent: String, colorHex: String): String {
        val lines = srtContent.split("\n")
        val builder = StringBuilder()
        for (line in lines) {
            if (line.trim().isNotEmpty() && !line.contains("-->") && !line.matches(Regex("^\\d+$"))) {
                builder.append("<font color=\"$colorHex\">").append(line).append("</font>\n")
            } else {
                builder.append(line).append("\n")
            }
        }
        return builder.toString()
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
