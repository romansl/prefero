package com.romansl.prefs

import com.squareup.kotlinpoet.*
import org.jetbrains.annotations.Nullable
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
        if (annotations.isEmpty())
            return false

        roundEnv.getElementsAnnotatedWith(Preferences::class.java).forEach {
            it as TypeElement
            val kind = it.kind
            if (kind == ElementKind.INTERFACE) {
                val innerMetaClass = it.enclosedElements.firstOrNull {
                    it.kind == ElementKind.CLASS && it.simpleName.contentEquals("DefaultImpls")
                }
                val properties = it.getEnclosedElements().filter {
                    it.kind == ElementKind.METHOD && (it.simpleName.startsWith("get") || it.simpleName.startsWith("is"))
                }.map {
                    Property(it as ExecutableElement, innerMetaClass)
                }
                if (!hasErrors) {
                    generateFile(it, properties)
                }
            } else {
                error("The Preferences annotation can only be applied to interfaces.", it)
            }
        }

        return true
    }

    private fun generateFile(it: TypeElement, properties: List<Property>) {
        val typeName = it.asClassName()
        val className = ClassName(typeName.packageName(), "${typeName.simpleName()}Impl")

        val editorClass = TypeSpec.classBuilder("Editor")
                .addModifiers(KModifier.INNER)
                .addSuperinterface(typeName)
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("editor", spEditor)
                        .build())
                .addProperty(PropertySpec.builder("editor", spEditor)
                        .initializer("editor")
                        .build())
                .addProperties(properties.map {
                    PropertySpec.builder(it.name, it.propertyType, KModifier.OVERRIDE)
                            .mutable(true)
                            .getter(FunSpec.getterBuilder()
                                    .addCode("return this@${className.simpleName()}.${it.name}\n")
                                    .build())
                            .setter(FunSpec.setterBuilder()
                                    .addParameter("value", it.propertyType)
                                    .addCode("editor.put${it.editorTypeName}(%S, value)\n", it.keyName)
                                    .build())
                            .build()
                })
                .build()

        val prefsClass = TypeSpec.classBuilder(className)
                .addSuperinterface(typeName)
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("preferences", spName)
                        .build())
                .addProperty(PropertySpec.builder("preferences", spName)
                        .initializer("preferences")
                        .build())
                .addProperties(properties.map {
                    val ii = if (it.propertyType.nullable) {
                        ""
                    } else {
                        if (it.propertyType == STRING) {
                            "!!"
                        } else {
                            ""
                        }
                    }
                    PropertySpec.varBuilder(it.name, it.propertyType, KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder()
                                    .addCode("return preferences.get${it.editorTypeName}(%S, ${it.default})$ii\n", it.keyName)
                                    .build())
                            .setter(FunSpec.setterBuilder()
                                    .addParameter("value", it.propertyType)
                                    .addCode("preferences.edit().put${it.editorTypeName}(%S, value).apply()", it.keyName)
                                    .build())
                            .build()
                })
                .addType(editorClass)
                .addFunction(FunSpec.builder("edit")
                        .addParameter("body", LambdaTypeName.get(ClassName("", editorClass.name!!), emptyList(), UNIT))
                        .addCode("val editor = preferences.edit()\n")
                        .addCode("Editor(editor).body()\n")
                        .addCode("editor.apply()\n")
                        .build())
                .build()

        FileSpec.builder(className.packageName(), className.simpleName())
                .addType(prefsClass)
                .build()
                .writeTo(processingEnv.filer)
    }

    private fun error(message: String, element: Element) {
        hasErrors = true
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message, element)
    }

    private fun FileSpec.writeTo(filer: Filer) {
        val filerSourceFile = filer.createResource(StandardLocation.SOURCE_OUTPUT,
                packageName, "$name.kt")
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

    inner class Property(private val propertyElement: ExecutableElement, innerMetaClass: Element?) {
        val propertyType = makePropertyType(propertyElement)
        val editorTypeName = makeEditorTypeName(propertyType, propertyElement)
        val name = makePropertyName(propertyElement)
        private val innerMetaProperty = run {
            val nameToFind = "$name\$annotations"
            innerMetaClass?.enclosedElements?.firstOrNull {
                it.kind == ElementKind.METHOD && it.simpleName.contentEquals(nameToFind)
            }
        }
        val keyName = makeKeyName(name)
        val default = makeDefaultValue(propertyType)

        init {
            if (propertyElement.simpleName.contentEquals("is") || propertyElement.simpleName.contentEquals("get")) {
                error("Wrong getter name.", propertyElement)
            }
        }

        private fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
            return propertyElement.getAnnotation(annotationType) ?: innerMetaProperty?.getAnnotation(annotationType)
        }

        private fun makeKeyName(name: String): String {
            return getAnnotation(Key::class.java)?.name ?: name
        }

        private fun makeDefaultValue(propertyType: TypeName): Any {
            val defaultInt = getAnnotation(DefaultInt::class.java)
            if (defaultInt != null) {
                require(propertyType == INT) { "Int required" }
                return defaultInt.value
            }

            val defaultLong = getAnnotation(DefaultLong::class.java)
            if (defaultLong != null) {
                require(propertyType == LONG) { "Long required" }
                return "" + defaultLong.value + "L"
            }

            val defaultString = getAnnotation(DefaultString::class.java)
            if (defaultString != null) {
                require(propertyType == STRING) { "String required" }
                return "\"" + defaultString.value + "\""
            }

            val defaultBool = getAnnotation(DefaultBoolean::class.java)
            if (defaultBool != null) {
                require(propertyType == BOOLEAN) { "Boolean required" }
                return defaultBool.value
            }

            val defaultFloat = getAnnotation(DefaultFloat::class.java)
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
            val n = it.simpleName
            val pos = when {
                n.startsWith("get") -> 3
                n.startsWith("is") -> 0
                else -> 0
            }

            val sb = StringBuilder(n.substring(pos))
            sb[0] = sb[0].toLowerCase()
            return sb.toString()
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
            val type = element.returnType.asTypeName()
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
    }

    companion object {
        private val spName = ClassName("android.content", "SharedPreferences")
        private val spEditor = ClassName("android.content", "SharedPreferences.Editor")
        private val STRING = ClassName("kotlin", "String")
        private val STRING_NULLABLE = STRING.asNullable()
    }
}

