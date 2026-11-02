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

    private lateinit var videoView: VideoView
    private lateinit var subtitleOverlay: TextView
    private lateinit var btnExportVideo: Button
    
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

        val titleText = TextView(this).apply {
            text = "AUTO SRT GEN"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00C6FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        val apiKeyInput = EditText(this).apply {
            hint = "Enter Gemini API Key"
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        fun createSpinner(items: Array<String>, onItemSelected: (Int) -> Unit): Spinner {
            val spinner = Spinner(this@MainActivity).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#151C2C"))
                    cornerRadius = 20f
                    setStroke(2, Color.parseColor("#2A344A"))
                }
                setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#151C2C")))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10; marginStart = 10 }
            }
            spinner.adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(Color.WHITE); view.textSize = 12f; view.setPadding(20, 20, 20, 20)
                    return view
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent) as TextView
                    view.setTextColor(Color.parseColor("#E2E8F0")); view.setBackgroundColor(Color.parseColor("#151C2C")); view.setPadding(30, 30, 30, 30)
                    return view
                }
            }
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) { onItemSelected(pos) }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            return spinner
        }

        val styleSpinner = createSpinner(arrayOf("Standard", "Informal", "Action", "Comedy")) { pos ->
            selectedStyle = when(pos) { 1 -> "Informal"; 2 -> "Action"; 3 -> "Comedy"; else -> "Standard professional" }
        }
        val colorSpinner = createSpinner(arrayOf("White", "Yellow", "Green", "Cyan")) { pos ->
            selectedColor = when(pos) { 1 -> "#FFFF00"; 2 -> "#00FF00"; 3 -> "#00FFFF"; else -> "#FFFFFF" }
            if (::subtitleOverlay.isInitialized) subtitleOverlay.setTextColor(Color.parseColor(selectedColor))
        }
        spinnerRow.addView(styleSpinner); spinnerRow.addView(colorSpinner)

        val previewFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = 20 }
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = 25f }
            clipToOutline = true
        }

        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.CENTER }
        }

        subtitleOverlay = TextView(this).apply {
            text = "Subtitle Preview\n(လက်ဖြင့်ရွှေ့နိုင်၊ ချုံ့ချဲ့နိုင်ပါသည်)"
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
                scaleFactor *= detector.scaleFactor
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f))
                subtitleOverlay.scaleX = scaleFactor; subtitleOverlay.scaleY = scaleFactor
                return true
            }
        })

        subtitleOverlay.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) { view.x = event.rawX + dX; view.y = event.rawY + dY }
                }
            }
            true
        }

        previewFrame.addView(videoView)
        previewFrame.addView(subtitleOverlay)

        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        
        val btnToggleSub = Button(this).apply {
            text = "💬 Sub: ON"; isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E293B")); cornerRadius = 15f }
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { marginEnd = 10 }
        }
        val btnToggleBg = Button(this).apply {
            text = "⬛ Box: ON"; isAllCaps = false; setTextColor(Color.WHITE); textSize = 12f
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E293B")); cornerRadius = 15f }
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { marginStart = 10 }
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
        toggleRow.addView(btnToggleSub); toggleRow.addView(btnToggleBg)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
        }

        val btnPickVideo = Button(this).apply {
            text = "📁 Select Video"
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#2563EB")); cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            setOnClickListener { pickVideoLauncher.launch("video/*") }
        }

        val btnGenerate = Button(this).apply {
            text = "🚀 Generate SRT"
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#00C6FF"), Color.parseColor("#0072FF"))).apply { cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 10 }
        }
        actionRow.addView(btnPickVideo); actionRow.addView(btnGenerate)

        btnExportVideo = Button(this).apply {
            text = "🎬 Export Video (Burn-in)"
            isAllCaps = false; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#D97706"))).apply { cornerRadius = 20f }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 15 }
            visibility = View.GONE
        }

        statusText = TextView(this).apply {
            text = "Ready to start..."
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        btnGenerate.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty() || selectedVideoUri == null) {
                statusText.text = "⚠️ API Key နှင့် Video ထည့်ပေးပါ!"
                statusText.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }

            btnGenerate.isEnabled = false; btnGenerate.alpha = 0.5f; btnExportVideo.visibility = View.GONE

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val audioFile = File(cacheDir, "extracted_audio.m4a")
                    val extractSuccess = extractAudioFromVideo(selectedVideoUri!!, audioFile.absolutePath) { progress ->
                        withContext(Dispatchers.Main) { statusText.text = "⏳ Extracting Audio... ($progress%)"; statusText.setTextColor(Color.parseColor("#FBBF24")) }
                    }

                    if (!extractSuccess || !audioFile.exists()) {
                        withContext(Dispatchers.Main) { statusText.text = "❌ Audio ထုတ်မရပါ"; btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f }
                        return@launch
                    }

                    withContext(Dispatchers.Main) { statusText.text = "🌐 AI Translating... Please wait." }

                    val bytes = audioFile.readBytes()
                    val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val client = OkHttpClient.Builder().connectTimeout(0, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS).build()
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
                    val domain1 = "generativelanguage"
                    val domain2 = ".googleapis.com"
                    val urlStr = "https://" + domain1 + domain2 + "/v1beta/models/gemini-3.5-flash-lite:generateContent?key=" + cleanedApiKey
                    
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
                                    statusText.text = "✅ ပြီးစီးပါပြီ! SRT အား Downloads/AutoSRT တွင် အလိုလိုသိမ်းပေးလိုက်ပါသည်။"
                                    statusText.setTextColor(Color.parseColor("#4ADE80"))
                                    btnExportVideo.visibility = View.VISIBLE
                                } else {
                                    statusText.text = "⚠️ SRT ထုတ်ပြီးသော်လည်း ဖုန်းထဲသိမ်း၍မရပါ။"
                                    statusText.setTextColor(Color.parseColor("#F59E0B"))
                                    btnExportVideo.visibility = View.VISIBLE
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { statusText.text = "❌ Gemini Error"; statusText.setTextColor(Color.parseColor("#EF4444")) }
                        }
                    } else {
                        withContext(Dispatchers.Main) { statusText.text = "❌ API Error / High Demand"; statusText.setTextColor(Color.parseColor("#EF4444")) }
                    }
                    audioFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText.text = "❌ Error: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { btnGenerate.isEnabled = true; btnGenerate.alpha = 1.0f }
                }
            }
        }

        // --- CRASH FIX: Safe Video Export Logic ---
        btnExportVideo.setOnClickListener {
            if (generatedSrtContent.isEmpty() || selectedVideoUri == null) return@setOnClickListener
            
            // RAM မပြည့်စေရန် Video ဖွင့်ထားခြင်းကို ခဏရပ် (Pause) ပါမည်
            if (videoView.isPlaying) {
                videoView.pause()
            }
            
            btnExportVideo.isEnabled = false; btnExportVideo.text = "⏳ Rendering Video..."
            statusText.text = "⚙️ Video အသစ်ထုတ်နေပါသည်...\nApp ကို မပိတ်ဘဲ စောင့်ပေးပါ။ (ကြာနိုင်ပါသည်)"
            statusText.setTextColor(Color.parseColor("#FBBF24"))

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Font Error မတက်စေရန် ကြိုတင်သတ်မှတ်ခြင်း
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
                    
                    // Path ကြောင့် Crash မဖြစ်စေရန် သေချာစွာ ရှင်းလင်းရေးသားခြင်း
                    val safeSrtPath = srtFile.absolutePath.replace("\\", "/").replace(":", "\\:")
                    val vfArg = "subtitles=$safeSrtPath:force_style='${boxStyle}Fontsize=18'"
                    
                    // String အစား Array ဖြင့်ခေါ်ယူခြင်းဖြင့် String Parsing Crash များကို 100% ကာကွယ်ပေးသည်
                    val cmdArgs = arrayOf(
                        "-y",
                        "-i", inputVideoFile.absolutePath,
                        "-vf", vfArg,
                        "-c:v", "libx264",
                        "-preset", "ultrafast",
                        "-threads", "2",
                        "-c:a", "copy",
                        outputVideoFile.absolutePath
                    )

                    val session = FFmpegKit.executeWithArguments(cmdArgs)
                    
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        // --- AUTO SAVE VIDEO ---
                        val isSaved = autoSaveVideoFile("AutoSRT_Video_${System.currentTimeMillis()}.mp4", outputVideoFile)

                        withContext(Dispatchers.Main) {
                            if (isSaved) {
                                statusText.text = "✅ အောင်မြင်ပါသည်! ဗီဒီယိုအား Movies/AutoSRT တွင် အလိုလိုသိမ်းပေးလိုက်ပါသည်။"
                                statusText.setTextColor(Color.parseColor("#4ADE80"))
                            } else {
                                statusText.text = "❌ ဖိုင်သိမ်းရန် Permission လိုအပ်ပါသည်။"
                                statusText.setTextColor(Color.parseColor("#EF4444"))
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            statusText.text = "❌ Rendering Failed! ဗီဒီယို ဖိုင်ဆိုဒ် ကြီးမားလွန်းနေနိုင်ပါသည်။"
                            statusText.setTextColor(Color.parseColor("#EF4444"))
                        }
                    }

                    try { inputVideoFile.delete(); srtFile.delete(); outputVideoFile.delete() } catch(e: Exception){}

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText.text = "❌ Error: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { btnExportVideo.isEnabled = true; btnExportVideo.text = "🎬 Export Video (Burn-in)" }
                }
            }
        }

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
