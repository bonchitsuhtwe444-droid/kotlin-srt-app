package com.myname.srtapp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dark Mode မျက်နှာပြင် (UI) ကို Code ဖြင့် တိုက်ရိုက်ဆွဲခြင်း
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212")) // Dark Mode အရောင်
        }

        val titleText = TextView(this).apply {
            text = "Kotlin SRT App\n\nVideo ရွေးရန် ပြင်ဆင်နေပါသည်..."
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        layout.addView(titleText)
        setContentView(layout)
    }
}
