package com.demo.trade.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.trades-raw}")
    private String tradesRawTopic;

    @Value("${app.kafka.topics.trades-dlq}")
    private String tradesDlqTopic;

    @Bean
    public NewTopic tradesRawTopic() {
        return TopicBuilder.name(tradesRawTopic).partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic tradesDlqTopic() {
        return TopicBuilder.name(tradesDlqTopic).partitions(1).replicas(1).build();
    }
}
