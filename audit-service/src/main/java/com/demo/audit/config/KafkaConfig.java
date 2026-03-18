package com.demo.audit.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import org.apache.kafka.common.TopicPartition;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.audit-dlq}")
    private String auditDlqTopic;

    @Bean
    public NewTopic auditDlqTopic() {
        return TopicBuilder.name(auditDlqTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public DefaultErrorHandler auditErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(auditDlqTopic, 0)
        );
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
