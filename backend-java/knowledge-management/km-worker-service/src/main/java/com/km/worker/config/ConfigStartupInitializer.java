package com.km.worker.config;

import com.km.worker.DynamicConfigHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * F6（commit #17b）：Worker 启动初始化。
 *
 * 1. 拉取 km-admin 内部接口 /internal/km/configs/parser 拿最新 parser 配置（带 X-Service-Token）
 * 2. 初始化 DynamicConfigHolder（markInitialized）
 * 3. 按顺序启动 5 个 listener（process → reprocess → reembed → purge → configChanged）
 *
 * R27：Worker 启动必须调 km-admin 内部接口初始化 DynamicConfigHolder，不能仅用默认值静默启动
 * R32：通过 RabbitListenerEndpointRegistry.start(id) 显式启动监听器
 * R33：路径 /internal/km/configs/parser + X-Service-Token: ${INTERNAL_TOKEN}
 *
 * 启动失败行为（v5 修正）：重试 5 次后抛 IllegalStateException → Worker 启动失败（容器 exit）
 * 不静默用默认值（v5 修正）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@DependsOn("dynamicConfigHolder") // 确保 DynamicConfigHolder 先于本类初始化
public class ConfigStartupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigStartupInitializer.class);

    // v6 修正：所有 Worker 监听器 ID 列表，按顺序启动
    private static final List<String> LISTENER_IDS = Arrays.asList(
            "processListener",
            "reprocessListener",
            "reembedListener",
            "purgeListener",
            "configChangedListener"
    );

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private DynamicConfigHolder holder;

    @Autowired
    private RabbitListenerEndpointRegistry registry;

    @Value("${km.admin-base-url}")
    private String adminBaseUrl;

    @Value("${km.internal.token:demo-internal-token}")
    private String internalToken;

    @Override
    public void run(ApplicationArguments args) {
        log.info("ConfigStartupInitializer starting...");
        loadParserConfigWithRetry();
        startAllListeners();
        log.info("ConfigStartupInitializer done. DynamicConfigHolder.maxConcurrentTasks={}, initialized={}",
                holder.maxConcurrentTasks(), holder.isInitialized());
    }

    private void loadParserConfigWithRetry() {
        int maxRetries = 5;
        long backoffMs = 1000;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Service-Token", internalToken);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                // v6 修正：直接接收 ParserConfigResponse（Worker 本地 DTO，R33）
                ResponseEntity<ParserConfigResponse> resp = restTemplate.exchange(
                        adminBaseUrl + "/internal/km/configs/parser",
                        HttpMethod.GET, entity, ParserConfigResponse.class);

                ParserConfigResponse config = resp.getBody();
                if (config == null) {
                    throw new IllegalStateException("admin returned null parser config");
                }

                holder.setMaxConcurrentTasks(config.getMaxConcurrentTasks());
                holder.markInitialized();

                log.info("ConfigStartupInitializer loaded parser config: maxConcurrentTasks={}, maxRetryCount={}, timeoutSeconds={}",
                        config.getMaxConcurrentTasks(), config.getMaxRetryCount(), config.getTimeoutSeconds());
                return;
            } catch (Exception e) {
                log.error("Failed to load parser config (attempt {}/{}): {}", i, maxRetries, e.getMessage());
                if (i == maxRetries) {
                    // v5 修正：启动失败，不静默用默认 4
                    throw new IllegalStateException(
                            "Failed to load parser config after " + maxRetries + " retries", e);
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while retrying config load", ie);
                }
                backoffMs *= 2;
            }
        }
    }

    private void startAllListeners() {
        for (String id : LISTENER_IDS) {
            MessageListenerContainer container = registry.getListenerContainer(id);
            if (container == null) {
                log.warn("Worker listener container not found: {} (skipping)", id);
                continue;
            }
            if (container.isRunning()) {
                log.warn("Worker listener already running: {}", id);
                continue;
            }
            container.start();
            log.info("Worker listener started: {}", id);
        }
    }
}