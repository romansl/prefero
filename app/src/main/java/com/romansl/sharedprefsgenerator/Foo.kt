package com.romansl.sharedprefsgenerator

import com.romansl.prefs.*

@Preferences
interface Foo {
    @get:DefaultInt(111)
    val someInt: Int

    @get:DefaultLong(222L)
    val someLong: Long

    @get:DefaultString("hello")
    val someHello: String

    @get:Key("replacedKey")
    val someKey: String

    val someNullable: String?

    @get:DefaultBoolean(true)
    val someBoolean: Boolean

    @get:DefaultFloat(0.5f)
    val someFloat: Float
}
