package com.tuanha.deeplink.processor

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

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
            // Lấy thông tin package và className một cách an toàn
            val className = element.simpleName.toString()
            val packageName = processingEnv.elementUtils.getPackageOf(element).qualifiedName.toString()

            // Tạo đối tượng ClassInfo và thêm vào danh sách
            classInfoList.add(ClassInfo(packageName = packageName, className = className))
        }

        if (elements.isNotEmpty()) {
            return true
        }


        val packages = classInfoList.map { it.packageName }.toSet()

        val packageName = packages.reduce { acc, pkg ->
            acc.commonPrefixWith(pkg).substringBeforeLast('.')
        }


        val kaptKotlinGeneratedDir = processingEnv.options["kapt.kotlin.generated"] ?: return false


        val list = ClassName("kotlin.collections", "MutableList")
        val navigationDeepLink = ClassName("com.tuanha.deeplink", "DeeplinkHandler")
        val listOfNavigationDeepLink = list.parameterizedBy(navigationDeepLink)

        val allMethod = FunSpec.builder("provider")
            .addModifiers(KModifier.OVERRIDE, KModifier.PUBLIC)
            .returns(listOfNavigationDeepLink)
            .addStatement("val result = mutableListOf<%T>()", navigationDeepLink)

        classInfoList.forEach {
            allMethod.addStatement("result.add(%T())", ClassName(it.packageName, it.className))
        }

        allMethod.addStatement("return result")


        val deeplinkProviderClassName = ClassName("com.tuanha.deeplink.provider", "DeeplinkProvider")

        val keepAnnotation = AnnotationSpec.builder(ClassName("androidx.annotation", "Keep")).build()

        val autoServiceAnnotation = AnnotationSpec.builder(ClassName("com.google.auto.service", "AutoService"))
            .addMember("%T::class", deeplinkProviderClassName)
            .build()

        val generatedClass = TypeSpec.objectBuilder("DeeplinkProvider")
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(keepAnnotation)
            .addAnnotation(autoServiceAnnotation)
            .addSuperinterface(deeplinkProviderClassName)
            .addFunction(allMethod.build())
            .build()

        FileSpec.builder(packageName, "DeeplinkProvider")
            .addType(generatedClass)
            .build()
            .writeTo(File(kaptKotlinGeneratedDir))
        return true
    }

    data class ClassInfo(
        val className: String,
        val packageName: String,
    )
}
