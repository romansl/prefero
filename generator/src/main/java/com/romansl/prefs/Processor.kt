package com.romansl.prefs

import com.squareup.kotlinpoet.*
import org.jetbrains.annotations.Nullable
import java.lang.IllegalArgumentException
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

@SupportedAnnotationTypes("com.romansl.prefs.Preferences")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
class Processor : AbstractProcessor() {
    private var hasErrors = false

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        // annotations почему-то содержат не нужные аннотации.
        roundEnv.getElementsAnnotatedWith(Preferences::class.java).forEach {
            it as TypeElement
            //val simpleName = it.simpleName
            val kind = it.kind
            if (kind == ElementKind.INTERFACE) {
                //val fullClassName = (it as QualifiedNameable).qualifiedName.toString()
                val methods = it.getEnclosedElements().filter {
                    it.kind == ElementKind.METHOD && it.simpleName.length > 3 && it.simpleName.startsWith("get")
                }
                val typeName = ClassName.get(it)
                val className = ClassName.get(typeName.packageName(), "${typeName.simpleName()}Preferences")

                val editorClass = TypeSpec.classBuilder("Editor")
                        .addModifiers(KModifier.INNER)
                        .addSuperinterface(typeName)
                        .primaryConstructor(FunSpec.constructorBuilder()
                                .addParameter("editor", spEditor)
                                .build())
                        .addProperty(PropertySpec.builder("editor", spEditor, KModifier.PRIVATE)
                                .initializer("editor")
                                .build())
                        .apply {
                            methods.forEach {
                                it as ExecutableElement
                                val propertyType = makePropertyType(it)
                                val editorTypeName = makeEditorTypeName(propertyType, it)
                                val name = makePropertyName(it)
                                val keyName = makeKeyName(it, name)
                                addProperty(PropertySpec.builder(name, propertyType, KModifier.OVERRIDE)
                                        .mutable(true)
                                        .getter(FunSpec.getterBuilder().addCode("return this@${className.simpleName()}.$name\n").build())
                                        .setter(FunSpec.setterBuilder()
                                                .addParameter("value", propertyType)
                                                .addCode("editor.put$editorTypeName(%S, value)\n", keyName)
                                                .build())
                                        .build())
                            }
                        }
                        .build()

                val prefsClass = TypeSpec.classBuilder(className)
                        .addSuperinterface(typeName)
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
                                val editorTypeName = makeEditorTypeName(propertyType, it)
                                val name = makePropertyName(it)
                                val keyName = makeKeyName(it, name)
                                val default = makeDefaultValue(it, propertyType)
                                addProperty(PropertySpec.builder(name, propertyType, KModifier.OVERRIDE)
                                        .getter(FunSpec.getterBuilder().addCode("return pref.get$editorTypeName(%S, $default)\n", keyName).build())
                                        .build())
                            }
                        }
                        .addType(editorClass)
                        .addFun(FunSpec.builder("edit")
                                .addParameter("body", EditorBodyType())
                                .addCode("val editor = pref.edit()\n")
                                .addCode("Editor(editor).body()\n")
                                .addCode("editor.commit()\n")
                                .build())
                        .build()

                if (!hasErrors) {
                    KotlinFile.builder(className.packageName(), className.simpleName())
                            .addType(prefsClass)
                            .build()
                            .writeTo(processingEnv.filer)
                }
            } else {
                error("The Preferences annotation can only be applied to interfaces.", it)
            }
        }

        return true
    }

    private fun makeKeyName(element: ExecutableElement, name: String): String {
        return element.getAnnotation(Key::class.java)?.name ?: name
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
            STRING_NULLABLE -> "null"
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

    private fun makeEditorTypeName(propertyType: TypeName, element: Element): String {
        return when (propertyType.asNonNullable().toString()) {
            "kotlin.Int" -> "Int"
            "kotlin.Long" -> "Long"
            "kotlin.Boolean" -> "Boolean"
            "kotlin.Float" -> "Float"
            "kotlin.String" -> "String"
            else -> {
                error("This property can not be Nullable.", element)
                ""
            }
        }
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
            if (propertyType == STRING) {
                propertyType.asNullable()
            } else {
                error("This property can not be Nullable.", element)
                propertyType
            }
        } else {
            propertyType
        }
    }

    private fun note(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, message)
    }

    private fun error(message: String, element: Element) {
        hasErrors = true
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message, element)
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
        private val spEditor = ClassName.get("android.content", "SharedPreferences.Editor")
        private val STRING = ClassName.get("kotlin", "String")
        private val STRING_NULLABLE = STRING.asNullable()
    }

    class EditorBodyType : TypeName(false, emptyList()) {
        override fun asNullable(): TypeName {
            return this
        }

        override fun asNonNullable(): TypeName {
            return this
        }

        override fun annotated(annotations: List<AnnotationSpec>): TypeName {
            return this
        }

        override fun withoutAnnotations(): TypeName {
            return this
        }

        override fun abstractEmit(out: CodeWriter): CodeWriter {
            out.emitCode("Editor.() -> Unit")
            return out
        }
    }
}

