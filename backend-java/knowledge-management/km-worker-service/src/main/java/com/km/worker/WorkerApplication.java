package com.km.worker;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * F4 整合（commit #24）：Worker Application 主类。
 *
 * 历史变更：
 * - 原 WorkerApplication 单文件含 12 个内嵌类（WorkerConsumers / DocumentProcessingService / FastApiClient 等），
 *   违反硬规则"Java 公共顶级类必须与文件名同名"
 * - commit #24 全部抽出到独立文件（filestaging / client / processing / consumers / limits / admin / purge / queue / messaging）
 * - 本文件仅保留主类 + ConfigStartupInitializer 由 com.km.worker.config.ConfigStartupInitializer 单独引入
 */
@SpringBootApplication
@EnableRabbit
@EnableScheduling
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}