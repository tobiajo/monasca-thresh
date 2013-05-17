package com.hpcloud.maas.infrastructure.messaging;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.hpcloud.messaging.rabbitmq.RabbitMQConfiguration;
import com.hpcloud.messaging.rabbitmq.RabbitMQConnection.RabbitMQConnectionProvider;
import com.hpcloud.messaging.rabbitmq.RabbitMQModule;
import com.hpcloud.messaging.rabbitmq.RabbitMQService;
import com.hpcloud.supervision.SupervisionModule;
import com.hpcloud.util.Injector;

public class MessagingModule extends AbstractModule {
  private final RabbitMQConfiguration rabbitConfig;

  public MessagingModule(RabbitMQConfiguration rabbitConfig) {
    this.rabbitConfig = rabbitConfig;
  }

  @Override
  protected void configure() {
    install(new SupervisionModule());
    if (!Injector.isBound(RabbitMQConnectionProvider.class))
      install(new RabbitMQModule());
  }

  @Provides
  @Singleton
  public RabbitMQService rabbitMQService() throws Exception {
    RabbitMQService rabbitService = new RabbitMQService(rabbitConfig);
    rabbitService.start();
    return rabbitService;
  }
}
