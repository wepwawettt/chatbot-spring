package org.example.service;

import org.example.dto.AlarmResponse;
import org.example.event.AlarmCreatedEvent;
import org.example.event.DeviceTelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final boolean rabbitEnabled;
    private final String exchange;

    public DomainEventPublisher(ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                                @Value("${app.rabbitmq.enabled:false}") boolean rabbitEnabled,
                                @Value("${app.rabbitmq.exchange}") String exchange) {
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.rabbitEnabled = rabbitEnabled;
        this.exchange = exchange;
    }

    public void publishTelemetry(DeviceTelemetryEvent event) {
        publish("device.telemetry.generated", event);
    }

    public void publishAlarmCreated(AlarmResponse alarm) {
        publish("alarm.created", new AlarmCreatedEvent(
                UUID.randomUUID().toString(),
                alarm.getId(),
                alarm.getDeviceId(),
                alarm.getDeviceName(),
                alarm.getAlarmType(),
                alarm.getSeverity(),
                alarm.getDescription(),
                alarm.getOccurredAt()
        ));
    }

    private void publish(String routingKey, Object event) {
        if (!rabbitEnabled) {
            log.debug("RabbitMQ disabled. Event not published. routingKey={}", routingKey);
            return;
        }

        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            log.warn("RabbitMQ is enabled but RabbitTemplate is not available. routingKey={}", routingKey);
            return;
        }

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.info("Domain event published. routingKey={}", routingKey);
        } catch (RuntimeException exception) {
            log.warn("Domain event could not be published. routingKey={}", routingKey, exception);
        }
    }
}
