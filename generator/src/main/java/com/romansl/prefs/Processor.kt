package com.romansl.prefs

import com.squareup.kotlinpoet.*
import org.jetbrains.annotations.Nullable
import java.lang.IllegalArgumentException
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

@SupportedAnnotationTypes("com.romansl.prefs.Preferences")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
class Processor : AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        // annotations почему-то содержат не нужные аннотации.
        roundEnv.getElementsAnnotatedWith(Preferences::class.java).forEach {
            it as TypeElement
            //val simpleName = it.simpleName
            val kind = it.kind
            if (kind == ElementKind.INTERFACE) {
                //val fullClassName = (it as QualifiedNameable).qualifiedName.toString()
                val methods = it.getEnclosedElements().filter { it.kind == ElementKind.METHOD && it.simpleName.length > 3 && it.simpleName.startsWith("get") }
                val typeName = ClassName.get(it)

                val className = ClassName.get(typeName.packageName(), "${typeName.simpleName()}Preferences")

                val prefsClass = TypeSpec.classBuilder(className)
                        .superclass(typeName)
                        .primaryConstructor(FunSpec.constructorBuilder()
                                .addParameter("preferences", spName)
                                .build())
                        .addProperty(PropertySpec.builder("pref", spName, KModifier.PRIVATE)
                                .initializer("preferences")
                                .build())
                        .apply {
                            methods.forEach {
                                it as ExecutableElement
                                val propertyType = makePropertyType(it)
                                val getterName = makeGetterName(propertyType)
                                val name = makePropertyName(it)
                                val default = makeDefaultValue(it, propertyType.asNonNullable())
                                addProperty(PropertySpec.builder(name, propertyType, KModifier.OVERRIDE)
                                        .getter(FunSpec.getterBuilder().addCode("return pref.$getterName(%S, $default)\n", name).build())
                                        .build())
                            }
                        }
                        .build()

                KotlinFile.builder(className.packageName(), className.simpleName())
                        .addType(prefsClass)
                        .build()
                        .writeTo(processingEnv.filer)
            } else {
                error("The Preferences annotation can only be applied to interfaces.")
            }
        }

        return true
    }

    private fun makeDefaultValue(it: ExecutableElement, propertyType: TypeName): Any {
        val defaultInt = it.getAnnotation(DefaultInt::class.java)
        if (defaultInt != null) {
            require(propertyType == INT) { "Int required" }
            return defaultInt.value
        }

        val defaultLong = it.getAnnotation(DefaultLong::class.java)
        if (defaultLong != null) {
            require(propertyType == LONG) { "Long required" }
            return "" + defaultLong.value + "L"
        }

        val defaultString = it.getAnnotation(DefaultString::class.java)
        if (defaultString != null) {
            require(propertyType == STRING) { "String required" }
            return "\"" + defaultString.value + "\""
        }

        val defaultBool = it.getAnnotation(DefaultBoolean::class.java)
        if (defaultBool != null) {
            require(propertyType == BOOLEAN) { "Boolean required" }
            return defaultBool.value
        }

        val defaultFloat = it.getAnnotation(DefaultFloat::class.java)
        if (defaultFloat != null) {
            require(propertyType == FLOAT) { "Float required" }
            return "" + defaultFloat.value + "f"
        }

        return when (propertyType) {
            INT -> "0"
            LONG -> "0L"
            STRING -> "\"\""
            BOOLEAN -> "false"
            FLOAT -> "0f"
            else -> throw IllegalArgumentException("Illegal type " + propertyType.toString())
        }
    }

    private fun makePropertyName(it: ExecutableElement): String {
        val name = run {
            val n = it.simpleName.toString()
            val sb = StringBuilder(n.substring(3))
            sb[0] = sb[0].toLowerCase()
            sb.toString()
        }
        return name
    }

    private fun makeGetterName(propertyType: TypeName): String {
        val getter = when (propertyType.asNonNullable().toString()) {
            "kotlin.Int" -> "getInt"
            "kotlin.Long" -> "getLong"
            "kotlin.Boolean" -> "getBoolean"
            "kotlin.Float" -> "getFloat"
            "kotlin.String" -> "getString"
            else -> throw IllegalArgumentException("Illegal type " + propertyType.toString())
        }
        return getter
    }

    private fun makePropertyType(element: ExecutableElement): TypeName {
        val type = TypeName.get(element.returnType)
        val propertyType = when (type.toString()) {
            "java.lang.String" -> STRING
            "java.lang.Integer" -> INT
            "java.lang.Long" -> LONG
            "java.lang.Float" -> FLOAT
            "java.lang.Boolean" -> BOOLEAN
            else -> type
        }

        return if (element.getAnnotation(Nullable::class.java) != null) {
            propertyType.asNullable()
        } else {
            propertyType
        }
    }

    private fun note(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, message)
    }

    private fun error(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message)
    }

    private fun KotlinFile.writeTo(filer: Filer) {
        val filerSourceFile = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                packageName, "$fileName.kt")
        try {
            filerSourceFile.openWriter().use { writer -> writeTo(writer) }
        } catch (e: Exception) {
            try {
                filerSourceFile.delete()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    companion object {
        private val spName = ClassName.get("android.content", "SharedPreferences")
        private val STRING = ClassName.get("kotlin", "String")
    }
}

