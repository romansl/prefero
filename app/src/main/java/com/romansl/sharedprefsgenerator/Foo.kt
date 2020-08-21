package com.romansl.sharedprefsgenerator

import com.romansl.prefs.*

@Preferences
interface Foo {
    @Key("SOME_INT")
    @DefaultInt(111)
    var someInt: Int

    @DefaultLong(222L)
    var someLong: Long

    @DefaultString("hello")
    var someHello: String

    @Key("replacedKey")
    var someKey: String

    var someNullable: String?

    @DefaultBoolean(true)
    var someBoolean: Boolean
    @Key("isReplaced")
    var isBoolean: Boolean
    @Key("hasReplaced")
    var hasBoolean: Boolean

    @DefaultFloat(0.567f)
    var someFloat: Float
}
