package com.simple.deeplink.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Factory class để KSP tự động phát hiện và khởi tạo [DeeplinkProcessor].
 *
 * KSP sử dụng cơ chế **Service Provider Interface (SPI)** của Java để tìm processor.
 * Class này được khai báo trong file:
 * ```
 * resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider
 * ```
 *
 * ## Luồng KSP load processor
 * ```
 * Gradle build
 *   → KSP plugin đọc file SPI
 *   → Khởi tạo DeeplinkProcessorProvider
 *   → Gọi create() để lấy DeeplinkProcessor
 *   → Gọi DeeplinkProcessor.process() mỗi round compile
 * ```
 *
 * @see DeeplinkProcessor
 */
class DeeplinkProcessorProvider : SymbolProcessorProvider {

    /**
     * Khởi tạo [DeeplinkProcessor] với các dependency được inject từ KSP environment.
     *
     * Hàm này chỉ được KSP gọi **một lần** khi bắt đầu build. Instance
     * [DeeplinkProcessor] trả về sẽ được tái sử dụng cho tất cả các round.
     *
     * @param environment Môi trường KSP, cung cấp:
     *   - [codeGenerator][SymbolProcessorEnvironment.codeGenerator]: tạo file output
     *   - [logger][SymbolProcessorEnvironment.logger]: ghi log/lỗi ra build output
     *   - [options][SymbolProcessorEnvironment.options]: các tham số cấu hình từ `build.gradle`
     * @return Instance [DeeplinkProcessor] sẵn sàng xử lý symbol.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DeeplinkProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
