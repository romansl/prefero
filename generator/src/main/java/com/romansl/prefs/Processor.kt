package com.romansl.prefs

import com.squareup.kotlinpoet.*
import java.lang.IllegalArgumentException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.QualifiedNameable
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedAnnotationTypes("com.romansl.prefs.Preferences")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
class Processor : AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        // annotations почему-то содержат не нужные аннотации.
        roundEnv.getElementsAnnotatedWith(Preferences::class.java).forEach { it as TypeElement
            val simpleName = it.simpleName
            val kind = it.kind
            if (kind == ElementKind.INTERFACE) {
                val fullClassName = (it as QualifiedNameable).qualifiedName.toString()
                val methods = it.getEnclosedElements().filter { it.kind == ElementKind.METHOD && it.simpleName.length > 3 && it.simpleName.startsWith("get") }
                val typeName = ClassName.get(it)

                val className = ClassName.get(typeName.packageName(), "${typeName.simpleName()}Preferences")

                val prefsClass = TypeSpec.classBuilder(className)
                        .primaryConstructor(FunSpec.constructorBuilder()
                                .addParameter("preferences", spName)
                                .build())
                        .addProperty(PropertySpec.builder("pref", spName, KModifier.PRIVATE)
                                .initializer("preferences")
                                .build())
                        .apply {
                            methods.forEach {
                                it as ExecutableElement
                                val type = TypeName.get(it.returnType)
                                val name = run {
                                    val n = it.simpleName.toString()
                                    val sb = StringBuilder(n.substring(3))
                                    sb[0] = sb[0].toLowerCase()
                                    sb.toString()
                                }
                                val getter = when (type.toString()) {
                                    "kotlin.Int" -> "getInt"
                                    "kotlin.Long" -> "getLong"
                                    "kotlin.Boolean" -> "getBoolean"
                                    "java.lang.String" -> "getString"
                                    else -> throw IllegalArgumentException()
                                }
                                addProperty(PropertySpec.builder(name, type)
                                        .initializer("pref.$getter()")
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

    private fun note(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, message)
    }

    private fun error(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message)
    }

    companion object {
        private val spName = ClassName.get("android.content", "SharedPreferences")
    }
}

