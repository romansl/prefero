package com.romansl.prefs

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Preferences

@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class DefaultInt(val value: Int)

@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class DefaultLong(val value: Long)

@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class DefaultFloat(val value: Float)

@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class DefaultBoolean(val value: Boolean)

@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class DefaultString(val value: String)
