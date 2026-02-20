package com.java_download_manager.jdm.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.java_download_manager.jdm.config.RabbitMQConfig;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnBean(RabbitTemplate.class)
@Slf4j
public class DownloadTaskCreatedProducer {

    private final RabbitTemplate rabbitTemplate;

    public DownloadTaskCreatedProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(DownloadTaskCreatedEvent event) {
        log.info("Sent download-task-created event: taskId={}", event.getTaskId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DOWNLOAD_TASK_CREATED_EXCHANGE,
                RabbitMQConfig.DOWNLOAD_TASK_CREATED_ROUTING_KEY,
                event);
    }
}
