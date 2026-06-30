package com.km.worker.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.worker.filestaging.TaskFileStagingService;
import com.km.worker.limits.HeartbeatHandle;
import com.km.worker.limits.HeartbeatService;
import com.km.worker.limits.PermitManager;
import com.km.worker.messaging.KmTaskMessage;
import com.km.worker.processing.DocumentProcessingService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * F4 整合（commit #24）：Worker 消费者（抽出独立文件，硬规则要求）。
 *
 * 5 个 @RabbitListener 全部加 id + autoStartup="false"（R32：ConfigStartupInitializer 启动后才会开始消费）。
 *
 * - process / reprocess / reembed / purge：手动 ACK（沿用项目惯例）
 * - configChangedListener：单独 configListenerContainerFactory（AUTO ACK + retry + DLQ；commit #17b）
 */
@Component
public class WorkerConsumers {
    private static final Logger log = LoggerFactory.getLogger(WorkerConsumers.class);

    private final ObjectMapper objectMapper;
    private final DocumentProcessingService processingService;

    @Autowired
    public WorkerConsumers(ObjectMapper objectMapper, DocumentProcessingService processingService) {
        this.objectMapper = objectMapper;
        this.processingService = processingService;
    }

    @RabbitListener(id = "processListener", queues = "km.doc.process", autoStartup = "false")
    public void process(byte[] body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        processingService.handle(read(body), channel, tag);
    }

    @RabbitListener(id = "reprocessListener", queues = "km.doc.reprocess", autoStartup = "false")
    public void reprocess(byte[] body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        processingService.handle(read(body), channel, tag);
    }

    @RabbitListener(id = "reembedListener", queues = "km.chunk.reembed", autoStartup = "false")
    public void reembed(byte[] body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        processingService.handle(read(body), channel, tag);
    }

    @RabbitListener(id = "purgeListener", queues = "km.doc.purge", autoStartup = "false")
    public void purge(byte[] body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        processingService.handle(read(body), channel, tag);
    }

    @RabbitListener(
            id = "configChangedListener",
            queues = "km.config.changed.worker",
            autoStartup = "false",
            containerFactory = "configListenerContainerFactory"
    )
    public void configChanged(byte[] body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        // R-F4-9 / commit #17a：F4 config event 由 DynamicConfigHolder.refreshFromEvent 解析
        processingService.refreshConfig(new String(body, java.nio.charset.StandardCharsets.UTF_8));
    }

    private KmTaskMessage read(byte[] body) throws Exception {
        return objectMapper.readValue(new String(body, java.nio.charset.StandardCharsets.UTF_8), KmTaskMessage.class);
    }
}