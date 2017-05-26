package com.romansl.sharedprefsgenerator

import com.romansl.prefs.*

@Preferences
interface Prefs1 {
    @get:DefaultInt(111)
    val aaa1: Int
    val aaa2: Int?

    @get:DefaultLong(222L)
    val bbb1: Long
    val bbb2: Long?

    @get:DefaultString("hello")
    val ccc1: String
    val ccc2: String?

    @get:DefaultBoolean(true)
    val ddd1: Boolean
    val ddd2: Boolean?

    @get:DefaultFloat(0.5f)
    val eee1: Float
    val eee2: Float?
}
