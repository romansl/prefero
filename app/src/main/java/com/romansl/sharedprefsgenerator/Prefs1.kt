package com.romansl.sharedprefsgenerator

import com.romansl.prefs.Preferences

@Preferences
interface Prefs1 {
    val foo: Int
    val bar: String
}
