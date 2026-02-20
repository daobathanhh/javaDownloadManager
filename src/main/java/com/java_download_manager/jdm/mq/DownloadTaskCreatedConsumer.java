package com.java_download_manager.jdm.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.java_download_manager.jdm.service.DownloadTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class DownloadTaskCreatedConsumer {

    private final DownloadTaskService downloadTaskService;

    @RabbitListener(queues = "#{'${jdm.rabbitmq.queue.download-task-created:jdm.download-task.created}'}")
    public void onDownloadTaskCreated(DownloadTaskCreatedEvent event) {
        log.info("Consumed download-task-created event: taskId={}", event.getTaskId());
        try {
            downloadTaskService.executeDownloadTask(event.getTaskId());
        } catch (Exception e) {
            log.error("Failed to execute download task taskId={}", event.getTaskId(), e);
            throw e;
        }
    }
}
