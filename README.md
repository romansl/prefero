# prefero
Android SharedPreferences helper code generator for Kotlin.

[![](https://jitpack.io/v/romansl/Promeso.svg)](https://jitpack.io/#romansl/prefero)

Usage:

1. Declare dependecies:
```
apply plugin: 'kotlin-kapt'

dependencies {
    compile 'com.github.romansl.prefero:annotations:1.2'
    kapt 'com.github.romansl.prefero:generator:1.2'
}
```

2. Create preferences interface:
```kotlin
@Preferences
interface Foo {
    @DefaultInt(111)
    var someInt: Int

    @DefaultLong(222L)
    var someLong: Long

    @DefaultString("hello")
    var someHello: String

    @Key("replacedKey")
    var someKey: String

    var someNullable: String?
    var someBoolean: Boolean
    var someFloat: Float
}
```

3. Use generated class:
```kotlin
    val foo = FooImpl(getSharedPreferences("hello", Context.MODE_PRIVATE))

    val increment = foo.someInt++

    foo.edit {
        someBoolean = true
        someFloat = 10f
        someHello = "hello1"
        someInt = 1234
        someKey = "kkk"
        someLong = 5678L
        someNullable = null
    }
```
