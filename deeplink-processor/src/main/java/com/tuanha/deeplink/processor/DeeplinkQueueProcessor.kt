package com.tuanha.deeplink.processor

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class DeeplinkQueueProcessor : AbstractProcessor() {

    private val packageName = "com.tuanha.deeplink"
    private val classInfoList = mutableListOf<ClassInfo>()

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf("com.tuanha.deeplink.annotation.DeeplinkQueue")
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        val elements = roundEnv.getElementsAnnotatedWith(
            processingEnv.elementUtils.getTypeElement("com.tuanha.deeplink.annotation.DeeplinkQueue")
        )

        elements.forEach {

            val className = it.simpleName.toString()
            val generatedClassName = "${className}DeeplinkQueueHandler"

            classInfoList.add(
                ClassInfo(className = className, generatedClassName = generatedClassName)
            )
        }

        if (elements.isNotEmpty()) {
            return true
        }

        val kaptKotlinGeneratedDir = processingEnv.options["kapt.kotlin.generated"] ?: return false

        val deeplinkQueueHandlerClass = ClassName("com.tuanha.deeplink.queue", "DeeplinkQueueHandler")

        classInfoList.forEach {

            val classSpec = TypeSpec.classBuilder(it.generatedClassName)
                .superclass(deeplinkQueueHandlerClass)
                .addModifiers(KModifier.PUBLIC)
                .build()

            val fileSpec = FileSpec.builder(packageName, it.generatedClassName)
                .addType(classSpec)
                .build()

            fileSpec.writeTo(File(kaptKotlinGeneratedDir))
        }


        val fileBuilder = FileSpec.builder("com.tuanha.deeplink", "DeeplinkQueueProvider")

        classInfoList.forEach {

            val property = PropertySpec.builder("instance${it.generatedClassName}", ClassName(packageName, it.generatedClassName))
                .initializer("%T()", ClassName(packageName, it.generatedClassName))
                .addModifiers(KModifier.PUBLIC)
                .build()

            fileBuilder.addProperty(property)

            val extras = ParameterSpec.builder("extras", ClassName("android.os", "Bundle").copy(nullable = true))
                .defaultValue("%L", "null")
                .build()

            val sharedElement = ParameterSpec.builder("sharedElement", Map::class.asClassName().parameterizedBy(String::class.asClassName(), ClassName("android.view", "View")).copy(nullable = true))
                .defaultValue("%L", "null")
                .build()

            val sendDeeplinkFunction = FunSpec.builder("send${it.className}")
                .addParameter("deepLink", String::class)
                .addParameter(extras)
                .addParameter(sharedElement)
                .addStatement(
                    "instance${it.generatedClassName}.sendDeeplink(deepLink, extras, sharedElement)"
                )
                .build()

            fileBuilder.addFunction(sendDeeplinkFunction)
        }

        val handlerList = classInfoList.joinToString(", ") { "instance${it.generatedClassName}" }

        val listFunction = FunSpec.builder("all")
            .returns(
                ClassName("kotlin.collections", "List").parameterizedBy(deeplinkQueueHandlerClass)
            )
            .addStatement("return listOf($handlerList)")
            .build()

        val globalHandlersClass = TypeSpec.objectBuilder("DeeplinkQueueProvider")
            .addFunction(listFunction)
            .build()

        fileBuilder.addType(globalHandlersClass)

        fileBuilder.build().writeTo(File(kaptKotlinGeneratedDir))


        return true
    }

    data class ClassInfo(
        val className: String,
        val generatedClassName: String,
    )
}