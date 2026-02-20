package com.java_download_manager.jdm.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class RabbitMQConfig {

    public static final String DOWNLOAD_TASK_CREATED_QUEUE = "jdm.download-task.created";
    public static final String DOWNLOAD_TASK_CREATED_EXCHANGE = "jdm.download-task";
    public static final String DOWNLOAD_TASK_CREATED_ROUTING_KEY = "download-task.created";

    @Value("${jdm.rabbitmq.queue.download-task-created:" + DOWNLOAD_TASK_CREATED_QUEUE + "}")
    private String downloadTaskCreatedQueue;

    @Bean
    public Queue downloadTaskCreatedQueue() {
        return new Queue(downloadTaskCreatedQueue, true);
    }

    @Bean
    public TopicExchange downloadTaskCreatedExchange() {
        return new TopicExchange(DOWNLOAD_TASK_CREATED_EXCHANGE, true, false);
    }

    @Bean
    public Binding downloadTaskCreatedBinding(Queue downloadTaskCreatedQueue, TopicExchange downloadTaskCreatedExchange) {
        return BindingBuilder.bind(downloadTaskCreatedQueue)
                .to(downloadTaskCreatedExchange)
                .with(DOWNLOAD_TASK_CREATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
