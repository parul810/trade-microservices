package com.demo.price.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.prices-live}")
    private String pricesLiveTopic;

    @Value("${app.kafka.topics.prices-updated}")
    private String pricesUpdatedTopic;

    @Value("${app.kafka.topics.price-dlq}")
    private String priceDlqTopic;

    @Bean
    public NewTopic pricesLiveTopic() {
        return TopicBuilder.name(pricesLiveTopic).partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic pricesUpdatedTopic() {
        return TopicBuilder.name(pricesUpdatedTopic).partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic priceDlqTopic() {
        return TopicBuilder.name(priceDlqTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler priceErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(priceDlqTopic, 0)
        );
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
