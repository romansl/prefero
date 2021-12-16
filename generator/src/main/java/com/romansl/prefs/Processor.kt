package com.romansl.prefs

import com.google.auto.common.MoreElements
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.classinspector.elements.ElementsClassInspector
import com.squareup.kotlinpoet.metadata.ImmutableKmProperty
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.squareup.kotlinpoet.metadata.specs.ClassInspector
import com.squareup.kotlinpoet.metadata.specs.toTypeSpec
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedAnnotationTypes("com.romansl.prefs.Preferences")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@OptIn(KotlinPoetMetadataPreview::class)
class Processor : AbstractProcessor() {
    private lateinit var classInspector: ClassInspector
    private var hasErrors = false

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)

        classInspector = ElementsClassInspector.create(processingEnv.elementUtils, processingEnv.typeUtils)
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (annotations.isEmpty())
            return false

        roundEnv.getElementsAnnotatedWith(Preferences::class.java).forEach {
            it as TypeElement
            val kind = it.kind
            if (kind == ElementKind.INTERFACE) {
                val classSpec = it.toTypeSpec(classInspector)

                if (!hasErrors) {
                    generateFile(it, classSpec)
                }
            } else {
                error("The Preferences annotation can only be applied to interfaces.", it)
            }
        }

        return true
    }

    private fun generateFile(element: TypeElement, interfaceSpec: TypeSpec) {
        val packageName = MoreElements.getPackage(element).toString()
        val interfaceName = ClassName(packageName, interfaceSpec.name!!)
        val className = ClassName(packageName, "${interfaceSpec.name}Impl")

        val innerSyntheticClassElement = element.enclosedElements.firstOrNull {
            it.kind == ElementKind.CLASS && it.simpleName.contentEquals("DefaultImpls")
        }
        val declarationContainer = classInspector.declarationContainerFor(interfaceName)

        val properties = interfaceSpec.propertySpecs.map { propertySpec ->
            val propertyMetadata = declarationContainer.properties.first {
                it.name == propertySpec.name
            }
            Property(element, innerSyntheticClassElement, propertySpec, propertyMetadata)
        }

        val editorClass = TypeSpec.classBuilder("Editor")
                .addModifiers(KModifier.INNER)
                .addSuperinterface(interfaceName)
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
                                    .addCode("return this@${className.simpleName}.${it.name}\n")
                                    .build())
                            .setter(FunSpec.setterBuilder()
                                    .addParameter("value", it.propertyType)
                                    .addCode("editor.put${it.editorTypeName}(%S, value)\n", it.keyName)
                                    .build())
                            .build()
                })
                .build()

        val prefsClass = TypeSpec.classBuilder(className)
                .addOriginatingElement(element)
                .addSuperinterface(interfaceName)
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter("preferences", spName)
                        .build())
                .addProperty(PropertySpec.builder("preferences", spName)
                        .initializer("preferences")
                        .build())
                .addProperties(properties.map {
                    val ii = if (it.propertyType.isNullable || it.propertyType != STRING) {
                        ""
                    } else {
                        "!!"
                    }
                    PropertySpec.builder(it.name, it.propertyType, KModifier.OVERRIDE)
                            .mutable(true)
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

        FileSpec.builder(className.packageName, className.simpleName)
                .addType(prefsClass)
                .indent("    ")
                .build()
                .writeTo(processingEnv.filer)
    }

    private fun error(message: String, element: Element) {
        hasErrors = true
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, message, element)
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(Preferences::class.java.name)
    }

    private inner class Property(
            private val propertyElement: Element,
            private val innerSyntheticClassElement: Element?,
            propertySpec: PropertySpec,
            propertyMetadata: ImmutableKmProperty) {
        /**
         * Суффикс геттеров и сеттеров SharedPreferences.
         */
        val editorTypeName = makeEditorTypeName(propertySpec.type)
        val name = propertySpec.name
        val propertyType = propertySpec.type
        val keyName: String
        val default: Any

        init {
            if (propertySpec.name.contentEquals("is") || propertySpec.name.contentEquals("get")) {
                error("Wrong getter name.", propertyElement)
            }

            val innerSyntheticPropertyElement = run {
                // Т.к. kotlinpoet не умеет читать аннотации у попертей интерфейсов,
                // приходится городить такой изврат...
                // https://github.com/square/kotlinpoet/issues/900
                val nameToFind = propertyMetadata.syntheticMethodForAnnotations?.name
                        ?: return@run null
                innerSyntheticClassElement?.enclosedElements?.firstOrNull {
                    it.kind == ElementKind.METHOD && it.simpleName.contentEquals(nameToFind)
                }
            }

            keyName = makeKeyName(name, innerSyntheticPropertyElement)
            default = makeDefaultValue(propertySpec.type, innerSyntheticPropertyElement)
        }

        private fun <A : Annotation> getAnnotation(annotationType: Class<A>, syntheticPropertyElement: Element?): A? {
            return propertyElement.getAnnotation(annotationType)
                    ?: syntheticPropertyElement?.getAnnotation(annotationType)
        }

        private fun makeKeyName(name: String, syntheticPropertyElement: Element?): String {
            return getAnnotation(Key::class.java, syntheticPropertyElement)?.name ?: name
        }

        private fun makeDefaultValue(propertyType: TypeName, syntheticPropertyElement: Element?): Any {
            val defaultInt = getAnnotation(DefaultInt::class.java, syntheticPropertyElement)
            if (defaultInt != null) {
                require(propertyType == INT) { "Int required" }
                return defaultInt.value
            }

            val defaultLong = getAnnotation(DefaultLong::class.java, syntheticPropertyElement)
            if (defaultLong != null) {
                require(propertyType == LONG) { "Long required" }
                return "" + defaultLong.value + "L"
            }

            val defaultString = getAnnotation(DefaultString::class.java, syntheticPropertyElement)
            if (defaultString != null) {
                require(propertyType == STRING) { "String required" }
                return "\"" + defaultString.value + "\""
            }

            val defaultBool = getAnnotation(DefaultBoolean::class.java, syntheticPropertyElement)
            if (defaultBool != null) {
                require(propertyType == BOOLEAN) { "Boolean required" }
                return defaultBool.value
            }

            val defaultFloat = getAnnotation(DefaultFloat::class.java, syntheticPropertyElement)
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
                else -> throw IllegalArgumentException("Illegal type $propertyType")
            }
        }

        private fun makeEditorTypeName(propertyType: TypeName): String {
            return when (propertyType) {
                INT -> "Int"
                LONG -> "Long"
                BOOLEAN -> "Boolean"
                FLOAT -> "Float"
                STRING, STRING_NULLABLE -> "String"
                else -> {
                    error("Illegal type: $propertyType", propertyElement)
                    ""
                }
            }
        }
    }

    companion object {
        private val spName = ClassName("android.content", "SharedPreferences")
        private val spEditor = ClassName("android.content", "SharedPreferences.Editor")
        private val STRING_NULLABLE = STRING.copy(nullable = true)
    }
}

