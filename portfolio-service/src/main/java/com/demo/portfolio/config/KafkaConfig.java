package com.demo.portfolio.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.portfolio-dlq}")
    private String portfolioDlqTopic;

    @Bean
    public NewTopic portfolioDlqTopic() {
        return TopicBuilder.name(portfolioDlqTopic).partitions(1).replicas(1).build();
    }
}
