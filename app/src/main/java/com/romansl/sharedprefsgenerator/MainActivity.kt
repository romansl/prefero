package com.romansl.sharedprefsgenerator

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Prefs1Preferences(getSharedPreferences("hello", Context.MODE_PRIVATE))
    }
}
