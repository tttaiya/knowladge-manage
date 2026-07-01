package com.km.worker.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * F6（commit #17b）：Worker 工程 RabbitMQ 配置。
 *
 * - 提供 Jackson2JsonMessageConverter Bean（admin 端显式 ObjectMapper 写 JSON 字节，Worker 用此 Converter 反序列化为 POJO）
 * - 提供 configListenerContainerFactory Bean（ACK=AUTO + retry + DLQ），专给 configChangedListener 用
 *
 * R31：单独 configListenerContainerFactory，不复用项目其它 listener 的手动 ACK 工厂。
 * R26：失败抛 AmqpRejectAndDontRequeueException → DLQ（不静默 ACK）。
 */
@Configuration
public class WorkerRabbitMqConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean("taskListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory taskListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        return factory;
    }

    @Bean("configListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory configListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        // R31：配置事件 listener 用 AUTO ACK（区别于项目其它 listener 的 manual ACK）
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        // R26：失败后不重新入队，进入 DLQ
        factory.setDefaultRequeueRejected(false);
        // R26：重试 3 次 + 指数退避（1s, 2s, 4s）；最终失败由 RejectAndDontRequeueRecoverer 进入 DLQ
        RetryTemplate retry = new RetryTemplate();
        SimpleRetryPolicy policy = new SimpleRetryPolicy(3);
        retry.setRetryPolicy(policy);
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(1000);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(10000);
        retry.setBackOffPolicy(backoff);

        Advice retryInterceptor = RetryInterceptorBuilder.stateless()
                .retryOperations(retry)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
        factory.setAdviceChain(retryInterceptor);
        // 并发：单消费者（配置事件不需要高并发）
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }
}
