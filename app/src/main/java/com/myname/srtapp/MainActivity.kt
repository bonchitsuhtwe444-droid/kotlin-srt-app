package com.myname.srtapp

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
    private var selectedColor = "#FFFFFF"

    // Preview UI Components
    private lateinit var previewContainer: LinearLayout
    private lateinit var videoView: VideoView
    private lateinit var subtitleOverlay: TextView
    private var isSubVisible = true
    private var isBgVisible = true

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            statusText.text = "✅ ဗီဒီယိုဖိုင် ရွေးချယ်ပြီးပါပြီ။"
            statusText.setTextColor(Color.parseColor("#4ADE80"))
            
            // Show Video Preview
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
            statusText.text = "🎉 အောင်မြင်ပါသည်။ SRT ကို သိမ်းဆည်းပြီးပါပြီ။"
            statusText.setTextColor(Color.parseColor("#38BDF8"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B101E")) // Obsidian Dark
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
                setTextColor(Color.parseColor("#00C6FF")) // Cyan Title
                setPadding(0, 0, 0, 25)
            }
            card.addView(titleView)
            card.addView(contentView)
            return card
        }

        // --- PLATINUM SPINNER FUNCTION ---
        fun createPlatinumSpinner(items: Array<String>, onItemSelected: (Int) -> Unit): Spinner {
            val spinner = Spinner(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0B101E"))
                    cornerRadius = 25f
                    setStroke(2, Color.parseColor("#00C6FF")) // Platinum Border
                }
                setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#151C2C"))) // Dark Dropdown
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

        // --- 1. API Key ---
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

        // --- 2. Configuration ---
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
            // Update preview text color if initialized
            if (::subtitleOverlay.isInitialized) {
                subtitleOverlay.setTextColor(Color.parseColor(selectedColor))
            }
        }
        mediaLayout.addView(colorSpinner)
        val mediaCard = createCardSection("2. Configuration", mediaLayout)

        // --- 3. Video Preview & Overlays (PHASE 2) ---
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
            text = "Subtitle Preview\n(စာတန်းထိုး နမူနာ ဤနေရာတွင်ပေါ်မည်)"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000")) // Semi-transparent black bg box
            setPadding(20, 10, 20, 10)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 40
            }
        }
        
        frameLayout.addView(videoView)
        frameLayout.addView(subtitleOverlay)
        
        // Toggle Buttons
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
        
        val previewCard = createCardSection("3. Live Preview", previewContainer)

        // --- 4. Generation ---
        val statusLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        statusText = TextView(this).apply {
            text = "Waiting to start..."
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }
        val btnGenerate = Button(this).apply {
            text = "🚀 Generate SRT"
            isAllCaps = false
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#00C6FF"), Color.parseColor("#0072FF"))
            ).apply { cornerRadius = 25f }
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
                                statusText.text = "✅ ပြီးစီးပါပြီ! SRT ဖိုင်ကို သိမ်းပါ။"
                                statusText.setTextColor(Color.parseColor("#4ADE80"))
                                saveSrtLauncher.launch("gemini_myanmar.srt")
                                // Update preview with real subtitle sample
                                subtitleOverlay.text = "Generation Complete!\n(SRT is ready to save)"
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

        statusLayout.addView(statusText)
        statusLayout.addView(btnGenerate)
        val actionCard = createCardSection("4. Action", statusLayout)

        mainLayout.addView(titleText)
        mainLayout.addView(subTitleText)
        mainLayout.addView(apiKeyCard)
        mainLayout.addView(mediaCard)
        mainLayout.addView(previewCard) // Added Video Preview Card
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

            val bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).takeIf { it > 0 } ?: (1024 * 1024)
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
