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

    private lateinit var previewContainer: LinearLayout
    private lateinit var videoView: VideoView
    private lateinit var subtitleOverlay: TextView
    private var isSubVisible = true
    private var isBgVisible = true

    private var scaleFactor = 1.0f
    private var dX = 0f
    private var dY = 0f

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            statusText.text = "✅ ဗီဒီယိုဖိုင် ရွေးချယ်ပြီးပါပြီ။"
            statusText.setTextColor(Color.parseColor("#4ADE80"))
            
            previewContainer.visibility = View.VISIBLE
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { mp -> 
                mp.isLooping = true 
                videoView.start()
            }
        }
    }

    private val saveSrtLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri: Uri? ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(generatedSrtContent.toByteArray())
            }
            statusText.text = "🎉 SRT ဖိုင်ကို အောင်မြင်စွာ သိမ်းဆည်းပြီးပါပြီ။"
            statusText.setTextColor(Color.parseColor("#38BDF8"))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B101E"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(40, 60, 40, 80)
        }

        val titleText = TextView(this).apply {
            text = "AUTO SRT GEN"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 5)
        }
        val subTitleText = TextView(this).apply {
            text = "Pro AI Subtitle Creator (Platinum Edition)"
            textSize = 12f
            setTextColor(Color.parseColor("#00C6FF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }

        fun createCardSection(title: String, contentView: View): LinearLayout {
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#151C2C"))
                    cornerRadius = 35f
                    setStroke(2, Color.parseColor("#2A344A"))
                }
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 40 }
            }
            val titleView = TextView(this@MainActivity).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#00C6FF"))
                setPadding(0, 0, 0, 25)
            }
            card.addView(titleView)
            card.addView(contentView)
            return card
        }

        fun createPlatinumSpinner(items: Array<String>, onItemSelected: (Int) -> Unit): Spinner {
            val spinner = Spinner(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0B101E"))
                    cornerRadius = 25f
                    setStroke(2, Color.parseColor("#00C6FF"))
                }
                setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#151C2C")))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
            }

            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(Color.WHITE)
                    view.setPadding(40, 35, 40, 35)
                    return view
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent) as TextView
                    view.setTextColor(Color.parseColor("#E2E8F0"))
                    view.setBackgroundColor(Color.parseColor("#151C2C"))
                    view.setPadding(50, 40, 50, 40)
                    return view
                }
            }
            spinner.adapter = adapter
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) { onItemSelected(pos) }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            return spinner
        }

        val apiKeyInput = EditText(this).apply {
            hint = "Enter Gemini API Key"
            setHintTextColor(Color.parseColor("#475569"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B101E"))
                cornerRadius = 25f
                setStroke(2, Color.parseColor("#475569"))
            }
            setPadding(40, 35, 40, 35)
        }
        val apiKeyCard = createCardSection("1. Authentication", apiKeyInput)

        val mediaLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        val btnPickVideo = Button(this).apply {
            text = "📁 Select Video File"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = 25f
            }
            setPadding(0, 35, 0, 35)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        btnPickVideo.setOnClickListener { pickVideoLauncher.launch("video/*") }

        mediaLayout.addView(btnPickVideo)
        mediaLayout.addView(TextView(this).apply { text = "Translation Tone:"; setTextColor(Color.parseColor("#94A3B8")); setPadding(10, 0, 0, 15) })
        
        val styleSpinner = createPlatinumSpinner(arrayOf("Standard (ပုံမှန်)", "Informal (အလွတ်သဘော)", "Movie Action (အက်ရှင်)", "Comedy (ဟာသ)")) { pos ->
            selectedStyle = when(pos) {
                1 -> "Informal and natural"
                2 -> "Action movie style, dramatic"
                3 -> "Comedic and funny"
                else -> "Standard professional"
            }
        }
        mediaLayout.addView(styleSpinner)

        mediaLayout.addView(TextView(this).apply { text = "Subtitle Color:"; setTextColor(Color.parseColor("#94A3B8")); setPadding(10, 10, 0, 15) })
        val colorSpinner = createPlatinumSpinner(arrayOf("White (အဖြူ)", "Yellow (အဝါ)", "Green (အစိမ်း)", "Cyan (အပြာနု)")) { pos ->
            selectedColor = when(pos) {
                1 -> "#FFFF00"
                2 -> "#00FF00"
                3 -> "#00FFFF"
                else -> "#FFFFFF"
            }
            if (::subtitleOverlay.isInitialized) {
                subtitleOverlay.setTextColor(Color.parseColor(selectedColor))
            }
        }
        mediaLayout.addView(colorSpinner)
        val mediaCard = createCardSection("2. Configuration", mediaLayout)

        previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        
        val frameLayout = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600).apply { bottomMargin = 30 }
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = 20f }
            clipToOutline = true
        }
        
        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.CENTER }
        }
        
        subtitleOverlay = TextView(this).apply {
            text = "Subtitle Preview\n(လက်ဖြင့်ရွှေ့နိုင်၊ ချုံ့ချဲ့နိုင်ပါသည်)"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000")) 
            setPadding(30, 20, 30, 20)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 40
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f))
                subtitleOverlay.scaleX = scaleFactor
                subtitleOverlay.scaleY = scaleFactor
                return true
            }
        })

        subtitleOverlay.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) {
                        view.x = event.rawX + dX
                        view.y = event.rawY + dY
                    }
                }
            }
            true
        }
        
        frameLayout.addView(videoView)
        frameLayout.addView(subtitleOverlay)
        
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        val btnToggleSub = Button(this).apply {
            text = "💬 Sub: ON"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#334155")); cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 10 }
        }
        val btnToggleBg = Button(this).apply {
            text = "⬛ Box: ON"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#334155")); cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 10 }
        }
        
        btnToggleSub.setOnClickListener {
            isSubVisible = !isSubVisible
            subtitleOverlay.visibility = if (isSubVisible) View.VISIBLE else View.INVISIBLE
            btnToggleSub.text = if (isSubVisible) "💬 Sub: ON" else "💬 Sub: OFF"
            btnToggleSub.setTextColor(if (isSubVisible) Color.WHITE else Color.GRAY)
        }
        btnToggleBg.setOnClickListener {
            isBgVisible = !isBgVisible
            subtitleOverlay.setBackgroundColor(if (isBgVisible) Color.parseColor("#99000000") else Color.TRANSPARENT)
            btnToggleBg.text = if (isBgVisible) "⬛ Box: ON" else "⬛ Box: OFF"
            btnToggleBg.setTextColor(if (isBgVisible) Color.WHITE else Color.GRAY)
        }
        
        toggleRow.addView(btnToggleSub)
        toggleRow.addView(btnToggleBg)
        
        previewContainer.addView(frameLayout)
        previewContainer.addView(toggleRow)
        
        val previewCard = createCardSection("3. Live Preview (Drag & Zoom)", previewContainer)

        val statusLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        statusText = TextView(this).apply {
            text = "Waiting to start..."
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }
        
        val btnGenerate = Button(this).apply {
            text = "🚀 1. Generate SRT"
            isAllCaps = false
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#00C6FF"), Color.parseColor("#0072FF"))
            ).apply { cornerRadius = 25f }
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        val btnExportVideo = Button(this).apply {
            text = "🎬 2. Export Video (Burn-in)"
            isAllCaps = false
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#D97706"))
            ).apply { cornerRadius = 25f }
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }

        btnGenerate.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty()) {
                statusText.text = "⚠️ API Key ထည့်ပေးပါ!"
                statusText.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }
            if (selectedVideoUri == null) {
                statusText.text = "⚠️ Video အရင်ရွေးပေးပါ!"
                statusText.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }

            btnGenerate.isEnabled = false
            btnGenerate.alpha = 0.5f
            btnExportVideo.visibility = View.GONE

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val audioFile = File(cacheDir, "extracted_audio.m4a")
                    val extractSuccess = extractAudioFromVideo(selectedVideoUri!!, audioFile.absolutePath) { progress ->
                        withContext(Dispatchers.Main) {
                            statusText.text = "⏳ Extracting Audio... ($progress%)"
                            statusText.setTextColor(Color.parseColor("#FBBF24"))
                        }
                    }

                    if (!extractSuccess || !audioFile.exists()) {
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ အသံဆွဲထုတ်ခြင်း မအောင်မြင်ပါ။"
                            statusText.setTextColor(Color.parseColor("#EF4444"))
                            btnGenerate.isEnabled = true
                            btnGenerate.alpha = 1.0f
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        statusText.text = "🌐 AI Translating ($selectedStyle)...\nPlease wait."
                    }

                    val bytes = audioFile.readBytes()
                    val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    val client = OkHttpClient.Builder()
                        .connectTimeout(0, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS)
                        .writeTimeout(0, TimeUnit.SECONDS)
                        .build()

                    val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val geminiJson = JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", "You are an expert subtitler. Listen to this audio and generate a perfectly timed SRT file. Translate the spoken English directly into Myanmar (Burmese) language using a $selectedStyle tone. Return ONLY the raw SRT format text starting with block 1. Do NOT include markdown blocks like ```srt.")
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

                    val cleanedApiKey = apiKey.trim()
                    val domain1 = "generativelanguage"
                    val domain2 = ".googleapis.com"
                    val urlStr = "https://" + domain1 + domain2 + "/v1beta/models/gemini-3.8-flash:generateContent?key=" + cleanedApiKey
                    
                    val request = Request.Builder().url(urlStr).post(geminiJson.toString().toRequestBody(jsonMediaType)).build()

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
                            
                            if (selectedColor != "#FFFFFF") {
                                extractedText = applyColorToSrt(extractedText, selectedColor)
                            }
                            
                            generatedSrtContent = extractedText

                            withContext(Dispatchers.Main) {
                                statusText.text = "✅ ပြီးစီးပါပြီ! SRT ဖိုင်ကို အရင်သိမ်းဆည်းပါ။"
                                statusText.setTextColor(Color.parseColor("#4ADE80"))
                                saveSrtLauncher.launch("gemini_myanmar.srt")
                                subtitleOverlay.text = "🎉 ဖန်တီးမှု ပြီးစီးပါပြီ!\n(အောက်ပါခလုတ်ဖြင့် Video ထုတ်နိုင်ပါပြီ)"
                                btnExportVideo.visibility = View.VISIBLE
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                statusText.text = "❌ Gemini မှ အဖြေမရရှိပါ။"
                                statusText.setTextColor(Color.parseColor("#EF4444"))
                            }
                        }
                    } else {
                        val errorMsg = try { JSONObject(responseStr ?: "").getJSONObject("error").getString("message") } catch (e: Exception) { "Network Error" }
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ API Error: $errorMsg"
                            statusText.setTextColor(Color.parseColor("#EF4444"))
                        }
                    }
                    audioFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "❌ Error: ${e.message}"
                        statusText.setTextColor(Color.parseColor("#EF4444"))
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        btnGenerate.isEnabled = true
                        btnGenerate.alpha = 1.0f
                    }
                }
            }
        }

        // FFmpeg Export Logic
        btnExportVideo.setOnClickListener {
            if (generatedSrtContent.isEmpty() || selectedVideoUri == null) return@setOnClickListener
            
            btnExportVideo.isEnabled = false
            btnExportVideo.text = "⏳ Rendering Video... (ကြာနိုင်ပါသည်)"
            statusText.text = "⚙️ Video ပေါ်သို့ စာတန်းထိုးကပ်နေပါသည်...\nဖုန်းအားမပိတ်ဘဲ စောင့်ပေးပါ။"
            statusText.setTextColor(Color.parseColor("#FBBF24"))

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val inputVideoFile = File(cacheDir, "input_video.mp4")
                    contentResolver.openInputStream(selectedVideoUri!!)?.use { input ->
                        FileOutputStream(inputVideoFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val srtFile = File(cacheDir, "temp_sub.srt")
                    srtFile.writeText(generatedSrtContent)

                    val outputVideoFile = File(cacheDir, "output_video.mp4")
                    if (outputVideoFile.exists()) outputVideoFile.delete()

                    val boxStyle = if (isBgVisible) "BorderStyle=4,BackColour=&H80000000," else ""
                    val srtPath = srtFile.absolutePath.replace("'", "\\\\'")
                    
                    val cmd = "-y -i \"${inputVideoFile.absolutePath}\" -vf \"subtitles='${srtPath}':fontsdir=/system/fonts:force_style='${boxStyle}Fontsize=18'\" -c:v libx264 -preset ultrafast -threads 2 -c:a copy \"${outputVideoFile.absolutePath}\""

                    val session = FFmpegKit.execute(cmd)
                    
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        var isSaved = false
                        try {
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, "AutoSRT_Export_${System.currentTimeMillis()}.mp4")
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AutoSRT")
                            }
                            
                            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                contentResolver.openOutputStream(uri)?.use { out ->
                                    outputVideoFile.inputStream().use { it.copyTo(out) }
                                }
                                isSaved = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        withContext(Dispatchers.Main) {
                            if (isSaved) {
                                statusText.text = "✅ အောင်မြင်ပါသည်!\nဗီဒီယိုကို ဖုန်း Gallery (Movies/AutoSRT) ထဲတွင် ကြည့်နိုင်ပါပြီ။"
                                statusText.setTextColor(Color.parseColor("#4ADE80"))
                            } else {
                                statusText.text = "❌ ဖိုင်သိမ်းရန် ခွင့်ပြုချက် လိုအပ်နေပါသည်။"
                                statusText.setTextColor(Color.parseColor("#EF4444"))
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ Rendering Failed! Video မှာ ကြီးမား/ရှုပ်ထွေးလွန်းနေပါသည်။"
                            statusText.setTextColor(Color.parseColor("#EF4444"))
                        }
                    }

                    inputVideoFile.delete()
                    srtFile.delete()
                    outputVideoFile.delete()

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "❌ Error: ${e.message}"
                        statusText.setTextColor(Color.parseColor("#EF4444"))
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        btnExportVideo.isEnabled = true
                        btnExportVideo.text = "🎬 2. Export Video (Burn-in)"
                    }
                }
            }
        }

        statusLayout.addView(statusText)
        statusLayout.addView(btnGenerate)
        statusLayout.addView(btnExportVideo)
        val actionCard = createCardSection("4. Action", statusLayout)

        mainLayout.addView(titleText)
        mainLayout.addView(subTitleText)
        mainLayout.addView(apiKeyCard)
        mainLayout.addView(mediaCard)
        mainLayout.addView(previewCard) 
        mainLayout.addView(actionCard)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
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
            contentResolver.openFileDescriptor(videoUri, "r")?.fileDescriptor?.let {
                extractor.setDataSource(it)
            } ?: return false

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i; break
                }
            }
            if (audioTrackIndex == -1) return false

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val durationUs = try { format.getLong(MediaFormat.KEY_DURATION) } catch (e: Exception) { 0L }
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            // ပုံထဲက Error ကို ဤနေရာတွင် ဖြေရှင်းထားပါသည်
            val bufferSize = try {
                val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                if (size > 0) size else 1024 * 1024
            } catch (e: Exception) {
                1024 * 1024
            }
            
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var lastProgress = -1

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0; bufferInfo.size = sampleSize; bufferInfo.flags = extractor.sampleFlags; bufferInfo.presentationTimeUs = extractor.sampleTime
                if (durationUs > 0) {
                    val progress = ((bufferInfo.presentationTimeUs.toDouble() / durationUs.toDouble()) * 100).toInt()
                    if (progress > lastProgress && progress <= 100) {
                        onProgress(progress)
                        lastProgress = progress
                    }
                }
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            
            onProgress(100)
            muxer.stop(); muxer.release(); extractor.release()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
