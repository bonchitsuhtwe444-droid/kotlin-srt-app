package com.myname.srtapp

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var selectedVideoUri: Uri? = null

    // ဖုန်းထဲက Video ဖိုင်ကို ရွေးချယ်ပေးမည့်စနစ် (Video Picker Launcher)
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            statusText.text = "Video ရွေးချယ်ပြီးပါပြီ။\nSRT ထုတ်ရန် အောက်ပါခလုတ်ကို နှိပ်ပါ။"
            statusText.setTextColor(Color.GREEN)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // အဓိက Dark Mode မျက်နှာပြင်
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(60, 60, 60, 60)
        }

        val titleText = TextView(this).apply {
            text = "Auto SRT Gen\n(Groq API)"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }

        val apiKeyInput = EditText(this).apply {
            hint = "Groq API Key ထည့်ပါ"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setPadding(30, 30, 30, 30)
        }

        val pickVideoButton = Button(this).apply {
            text = "Video ဖိုင်ရွေးမည်"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                pickVideoLauncher.launch("video/*") // Video ဖိုင်များကိုသာ ရွေးခိုင်းခြင်း
            }
        }

        statusText = TextView(this).apply {
            text = "Video ကို ရွေးချယ်ရန် အပေါ်ခလုတ်ကို နှိပ်ပါ"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }

        val generateButton = Button(this).apply {
            text = "SRT စာတန်းထိုး ဖန်တီးမည်"
            setBackgroundColor(Color.parseColor("#1E88E5"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (apiKeyInput.text.toString().isEmpty()) {
                    Toast.makeText(this@MainActivity, "API Key ထည့်ပေးပါ!", Toast.LENGTH_SHORT).show()
                } else if (selectedVideoUri == null) {
                    Toast.makeText(this@MainActivity, "Video အရင်ရွေးပေးပါ!", Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "၁။ Video ထဲမှ အသံကို ခွဲထုတ်ရန် ပြင်ဆင်နေပါသည်..."
                    statusText.setTextColor(Color.YELLOW)
                    // မှတ်ချက် - ဒီနေရာမှာ FFmpeg သုံးပြီး အသံဖြတ်မယ့် Code ကို နောက်အဆင့်မှာ ဆက်ရေးပါမယ်
                }
            }
        }

        // ဖန်တီးထားတဲ့ UI များကို မျက်နှာပြင်ပေါ်သို့ အစဉ်လိုက် ထည့်သွင်းခြင်း
        layout.addView(titleText)
        layout.addView(apiKeyInput)
        layout.addView(TextView(this).apply { height = 40 }) // နေရာလွတ် (Spacer)
        layout.addView(pickVideoButton)
        layout.addView(statusText)
        layout.addView(generateButton)

        setContentView(layout)
    }
}
