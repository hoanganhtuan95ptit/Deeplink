package com.tuanha.deeplink.processor

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.AnnotationSpec
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
import java.nio.file.Paths
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class DeeplinkProviderProcessor : AbstractProcessor() {

    private val annotationName = "com.tuanha.deeplink.annotation.Deeplink"

    private val classInfoList: MutableList<ClassInfo> = arrayListOf()

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(annotationName)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(set: Set<TypeElement?>, roundEnvironment: RoundEnvironment): Boolean {

        if (processingEnv == null) {
            return false
        }

        val elements = roundEnvironment.getElementsAnnotatedWith(
            processingEnv.elementUtils.getTypeElement(annotationName)
        )

        for (element in elements) {

            val className = element.simpleName.toString()

            val packageName = processingEnv.elementUtils.getPackageOf(element).qualifiedName.toString()

            val queueName = element.annotationMirrors.firstOrNull {
                it.annotationType.toString() == annotationName
            }?.let {
                it.elementValues?.toList()?.firstOrNull()?.second?.value
            }?.let {
                "$it".formatAndRemoveWhitespace()
            }

            classInfoList.add(ClassInfo(queueName = queueName ?: "Deeplink", className = className, packageName = packageName))
        }


        if (elements.isNotEmpty()) {
            return true
        }


        val packages = classInfoList.map { it.packageName }.toSet()

        val packageName = packages.reduce { acc, pkg ->
            acc.commonPrefixWith(pkg).substringBeforeLast('.')
        }


        val kaptKotlinGeneratedDir = processingEnv.options["kapt.kotlin.generated"] ?: return false

        generatedScope(packageName = packageName, kaptKotlinGeneratedDir = kaptKotlinGeneratedDir)

        generatedProvider(packageName = packageName, kaptKotlinGeneratedDir = kaptKotlinGeneratedDir)


        return true
    }

    private fun generatedScope(packageName: String, kaptKotlinGeneratedDir: String) {

        val deeplinkDeeplinkQueueClass = ClassName("com.tuanha.deeplink.queue", "DeeplinkQueue")

        val autoServiceAnnotation = AnnotationSpec.builder(ClassName("com.google.auto.service", "AutoService"))
            .addMember("%T::class", deeplinkDeeplinkQueueClass)
            .build()

        val queueNameList = classInfoList.groupBy { it.queueName }.keys.toList()

        queueNameList.forEach {

            val generatedClassName = "${it}DeeplinkQueue"

            val getQueueMethod = FunSpec.builder("getQueue")
                .addModifiers(KModifier.OVERRIDE)
                .returns(String::class)
                .addStatement("return %S", it)
                .build()

            val classSpec = TypeSpec.classBuilder(generatedClassName)
                .superclass(deeplinkDeeplinkQueueClass)
                .addAnnotation(autoServiceAnnotation)
                .addModifiers(KModifier.PUBLIC)
                .addFunction(getQueueMethod)
                .build()

            val fileSpec = FileSpec.builder(packageName, generatedClassName)
                .addType(classSpec)
                .build()

            fileSpec.writeTo(File(kaptKotlinGeneratedDir))
        }


        val resource = processingEnv.filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/${deeplinkDeeplinkQueueClass.canonicalName}")
        resource.openWriter().use { writer ->

            queueNameList.forEach {
                val generatedClassName = "${it}DeeplinkQueue"
                writer.write("${packageName}.${generatedClassName}\n")
            }
        }
    }

    private fun generatedProvider(packageName: String, kaptKotlinGeneratedDir: String) {

        val stringClassName = ClassName("kotlin", "String")
        val deeplinkHandlerClassName = ClassName("com.tuanha.deeplink", "DeeplinkHandler")

        val pairClassName = ClassName("kotlin", "Pair")
        val pairClassNameWithParameterized = pairClassName
            .parameterizedBy(stringClassName, deeplinkHandlerClassName)

        val listClassNameWithParameterized = ClassName("kotlin.collections", "List")
            .parameterizedBy(pairClassNameWithParameterized)

        val allMethod = FunSpec.builder("provider")
            .addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            .returns(listClassNameWithParameterized)

        allMethod.addStatement("val result = mutableListOf<%T<String, %T>>()", pairClassName, deeplinkHandlerClassName)

        classInfoList.forEach {
            allMethod.addStatement("result.add(%T(\"%L\", %T()))", pairClassName, it.queueName, ClassName(it.packageName, it.className))
        }

        allMethod.addStatement("return result")


        val deeplinkProviderClassName = ClassName("com.tuanha.deeplink.provider", "DeeplinkProvider")


        val keepAnnotation = AnnotationSpec.builder(ClassName("androidx.annotation", "Keep")).build()

        val autoServiceAnnotation = AnnotationSpec.builder(ClassName("com.google.auto.service", "AutoService"))
            .addMember("%T::class", deeplinkProviderClassName)
            .build()

        val generatedClass = TypeSpec.classBuilder("DeeplinkProvider")
            .superclass(deeplinkProviderClassName)
            .addAnnotation(keepAnnotation)
            .addAnnotation(autoServiceAnnotation)
            .addModifiers(KModifier.PUBLIC)
            .addFunction(allMethod.build())
            .build()

        FileSpec.builder(packageName, "DeeplinkProvider")
            .addType(generatedClass)
            .build()
            .writeTo(File(kaptKotlinGeneratedDir))


        val resource = processingEnv.filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/${deeplinkProviderClassName.canonicalName}")
        resource.openWriter().use { writer ->
            writer.write("${packageName}.DeeplinkProvider\n")
        }
    }

    private fun String.formatAndRemoveWhitespace(): String {

        val noSpaces = this.replace("\\s+".toRegex(), "")

        return if (noSpaces.isNotEmpty()) {
            noSpaces.replaceFirstChar { it.uppercase() } + noSpaces.substring(1).lowercase()
        } else {
            noSpaces
        }
    }

    data class ClassInfo(
        val queueName: String,
        val className: String,
        val packageName: String,
    )
}
