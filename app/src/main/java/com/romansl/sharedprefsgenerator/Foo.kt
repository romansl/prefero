package com.romansl.sharedprefsgenerator

import com.romansl.prefs.*

@Preferences
interface Foo {
    @DefaultInt(111)
    val someInt: Int

    @DefaultLong(222L)
    val someLong: Long

    @DefaultString("hello")
    val someHello: String

    @Key("replacedKey")
    val someKey: String

    val someNullable: String?

    @DefaultBoolean(true)
    val someBoolean: Boolean
    @Key("isReplaced")
    val isBoolean: Boolean
    @Key("hasReplaced")
    val hasBoolean: Boolean

    @DefaultFloat(0.567f)
    val someFloat: Float
}
