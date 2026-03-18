package com.demo.execution.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.trades-processed}")
    private String tradesProcessedTopic;

    @Value("${app.kafka.topics.trades-dlq}")
    private String tradesDlqTopic;

    @Bean
    public NewTopic tradesProcessedTopic() {
        return TopicBuilder.name(tradesProcessedTopic).partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic tradesDlqTopic() {
        return TopicBuilder.name(tradesDlqTopic).partitions(1).replicas(1).build();
    }
}
