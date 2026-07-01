package com.km.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SearchConfigChangedConsumer {
    private static final Logger log = LoggerFactory.getLogger(SearchConfigChangedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SearchDynamicConfigHolder holder;

    public SearchConfigChangedConsumer(ObjectMapper objectMapper, SearchDynamicConfigHolder holder) {
        this.objectMapper = objectMapper;
        this.holder = holder;
    }

    @RabbitListener(
            queues = "km.config.changed.search",
            containerFactory = "searchConfigListenerContainerFactory"
    )
    public void onConfigChanged(byte[] body) throws Exception {
        String json = new String(body, StandardCharsets.UTF_8);
        ConfigChangedEvent event = objectMapper.readValue(json, ConfigChangedEvent.class);
        boolean applied = holder.applyEvent(event);
        log.info("Search received km.config.changed: eventId={}, group={}, version={}, applied={}",
                event.getEventId(), event.getConfigGroup(), event.getConfigVersion(), applied);
    }
}
