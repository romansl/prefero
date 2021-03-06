package com.romansl.sharedprefsgenerator

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FooImpl(getSharedPreferences("hello", Context.MODE_PRIVATE)).edit {
            someBoolean = true
            someFloat = 10f
            someHello = "hello1"
            someInt = 1234
            someKey = "kkk"
            someLong = 5678L
            someNullable = null
        }
    }
}
