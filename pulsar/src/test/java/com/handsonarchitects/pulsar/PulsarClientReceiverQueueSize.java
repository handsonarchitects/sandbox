package com.handsonarchitects.pulsar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.LongRunningProcessStatus;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.junit.jupiter.api.Test;

public class PulsarClientReceiverQueueSize {

  public static final String TOPIC_NAME = "order_test";

  private final PulsarClient client = createClient();

  private final PulsarAdmin admin = createAdmin();

  @Test
  void expectOrderedMessagesWhenConsumerReadsCompactedTopic()
      throws PulsarClientException, PulsarAdminException, InterruptedException {
    String topicName = TOPIC_NAME + System.currentTimeMillis();
    Producer<String> producer = newProducer(topicName);

    producer.newMessage().value("1").key("A").send();
    producer.newMessage().value("2").key("A").send();
    producer.newMessage().value("3").key("A").send();
    producer.newMessage().value("4").key("B").send();

    Consumer<String> consumer = newConsumer(topicName, "unit-test")
        .readCompacted(Boolean.TRUE)
        .subscribe();

    waitUntilCompacted(topicName);

    assertNextMessageValue("1", consumer);
    assertNextMessageValue("2", consumer);
    assertNextMessageValue("3", consumer);
    assertNextMessageValue("4", consumer);

    producer.close();
    consumer.close();
  }

  @Test
  void expectOnlyFirstMessageStaleWhenConsumerReadsCompactedTopicAndLimitedReceiverQueueSize()
      throws PulsarClientException, PulsarAdminException, InterruptedException {
    String topicName = TOPIC_NAME + System.currentTimeMillis();
    Producer<String> producer = newProducer(topicName);

    producer.newMessage().value("1").key("A").send();
    producer.newMessage().value("2").key("A").send();
    producer.newMessage().value("3").key("A").send();
    producer.newMessage().value("4").key("B").send();

    Consumer<String> consumer = newConsumer(topicName, "unit-test")
        .readCompacted(Boolean.TRUE)
        .receiverQueueSize(1)
        .subscribe();

    waitUntilCompacted(topicName);

    assertNextMessageValue("1", consumer);
    assertNextMessageValue("3", consumer);
    assertNextMessageValue("4", consumer);

    producer.close();
    consumer.close();
  }

  private void waitUntilCompacted(String topicName)
      throws PulsarAdminException, InterruptedException {
    admin.topics().triggerCompaction(topicName);
    LongRunningProcessStatus processStatus = admin.topics().compactionStatus(topicName);

    while (LongRunningProcessStatus.Status.SUCCESS != processStatus.status) {
      System.out.println("Waiting for compaction, current status: " + processStatus.status);
      Thread.sleep(200);
      processStatus = admin.topics().compactionStatus(topicName);
    }
    System.out.println("Compacted!");
  }

  private static Message<String> assertNextMessageValue(String expectedValue,
      Consumer<String> consumer) throws PulsarClientException {
    System.out.println("Expecting: " + expectedValue);
    Message<String> message = consumer.receive();
    if (message == null) {
      throw new IllegalStateException("No messages to read!");
    }
    System.out.println("Consumed: " + message.getMessageId() + " value: " + message.getValue());
    assertEquals(expectedValue, message.getValue());
    return message;
  }

  private Producer<String> newProducer(String topicName) throws PulsarClientException {
    return client.newProducer(Schema.STRING).topic(topicName).create();
  }

  //@formatter:off
  private ConsumerBuilder<String> newConsumer(String topicName,
      String subscriptionName) {
    return client.newConsumer(Schema.STRING)
        .topic(topicName)
        .subscriptionName(subscriptionName)
        .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
        .subscriptionType(SubscriptionType.Exclusive)
        .negativeAckRedeliveryDelay(500, TimeUnit.MILLISECONDS);
  }
  //@formatter:on

  private PulsarClient createClient() {
    try {
      return PulsarClient.builder().serviceUrl("pulsar://localhost:6650").build();
    } catch (PulsarClientException e) {
      throw new RuntimeException(e);
    }
  }

  private PulsarAdmin createAdmin() {
    try {
      return PulsarAdmin.builder().serviceHttpUrl("http://localhost:8080").build();
    } catch (PulsarClientException e) {
      throw new RuntimeException(e);
    }
  }

}
