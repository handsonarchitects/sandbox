package com.handsonarchitects.pulsar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.impl.MessageIdImpl;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.admin.ZooKeeperAdmin;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * helm install -f src/test/resources/values-ledger-small.yaml --namespace pulsar local-pulsar
 * apache/pulsar --create-namespace
 * <p>
 * kubectl port-forward local-pulsar-zookeeper-0 2181:2181 -n pulsar
 */
public class LedgerCleanupTest {

  public static final String TOPIC_NAME = "ledgers-cleanup_" + System.currentTimeMillis();
  private static final Logger LOG = LoggerFactory.getLogger(LedgerCleanupTest.class);
  private final PulsarClient pulsarClient = createPulsarClient();

  private final PulsarAdmin pulsarAdmin = createPulsarAdmin();

  private final Producer<String> producer = getProducer(TOPIC_NAME);

  private final ZooKeeperAdmin zkAdmin = createZkAdmin();


  @Test
  void scenario() throws IOException, InterruptedException, KeeperException {
    Set<String> currentLedgers = getLedgers();

    LOG.info("Ledgers: {}", currentLedgers);

    Consumer<String> cleanerConsumer = createConsumer();

    LOG.info("=========== WRITING ==========");



    List<MessageIdImpl> ids = new ArrayList<>();

    ids.add(sendMessage("A"));
    checkNewLedger(currentLedgers, ids.get(ids.size() - 1).getLedgerId());
    ids.add(sendMessage("B"));
    checkNewLedger(currentLedgers, ids.get(ids.size() - 1).getLedgerId());
    Thread.sleep(60000);

    ids.add(sendMessage("C"));
    checkNewLedger(currentLedgers, ids.get(ids.size() - 1).getLedgerId());
    ids.add(sendMessage("D"));
    checkNewLedger(currentLedgers, ids.get(ids.size() - 1).getLedgerId());
    Thread.sleep(60000);

    ids.add(sendMessage("E"));
    checkNewLedger(currentLedgers, ids.get(ids.size() - 1).getLedgerId());


    LOG.info("=========== READING ==========");

    Message<String> message = cleanerConsumer.receive();
    acknowledge(cleanerConsumer, message);
    message = cleanerConsumer.receive();
    acknowledge(cleanerConsumer, message);
    message = cleanerConsumer.receive();
    acknowledge(cleanerConsumer, message);
    message = cleanerConsumer.receive();
    acknowledge(cleanerConsumer, message);
    message = cleanerConsumer.receive();
    acknowledge(cleanerConsumer, message);

    Thread.sleep(60000);

    Set<String> existingLedgers = getLedgers();
    currentLedgers.removeAll(existingLedgers);
    LOG.info("Deleted ledgers: {}", currentLedgers);

    producer.close();
    pulsarClient.close();
    pulsarAdmin.close();
    zkAdmin.close();
  }

  private Consumer<String> createConsumer() throws PulsarClientException {
    return pulsarClient.newConsumer(Schema.STRING)
        .topic(TOPIC_NAME)
        .subscriptionName("cleaner")
        .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
        .subscriptionType(SubscriptionType.Exclusive).subscribe();
  }

  private void checkNewLedger(Set<String> ledgers, long ledgerId) {
    String ledger = String.valueOf(ledgerId);
    if (!ledgers.contains(ledger)) {
      LOG.info("New ledger created {}", ledgerId);
      ledgers.add(ledger);
    }
  }

  private Set<String> getLedgers() throws InterruptedException, KeeperException {
    Set<String> ledgers = new HashSet<>();

    for (String xxGroup : zkAdmin.getChildren("/ledgers", false)) {
      for (String xxxxGroup : zkAdmin.getChildren("/ledgers/" + xxGroup, false)) {
        List<String> result = zkAdmin.getChildren("/ledgers/" + xxGroup + "/" + xxxxGroup, false)
            .stream().filter(l -> l.startsWith("L")).map(l -> l.replaceFirst("^L0*", ""))
            .collect(Collectors.toList());
        ledgers.addAll(result);
      }
    }
    return ledgers;
  }

  private Producer<String> getProducer(String topicName) {
    try {
      return pulsarClient.newProducer(Schema.STRING).topic(topicName).create();
    } catch (PulsarClientException e) {
      LOG.error("Could not create Producer!", e);
      throw new RuntimeException(e);
    }
  }

  private PulsarClient createPulsarClient() {
    try {
      return PulsarClient.builder().serviceUrl("pulsar://localhost:6650").build();
    } catch (PulsarClientException e) {
      LOG.error("Could not create Pulsar Broker connection!", e);
      throw new RuntimeException(e);
    }
  }

  private PulsarAdmin createPulsarAdmin() {
    try {
      return PulsarAdmin.builder().serviceHttpUrl("http://localhost:8080").build();
    } catch (PulsarClientException e) {
      LOG.error("Could not create Pulsar Broker connection!", e);
      throw new RuntimeException(e);
    }
  }

  private ZooKeeperAdmin createZkAdmin() {
    try {
      return new ZooKeeperAdmin("127.0.0.1:2181", 1000, event -> {
      });
    } catch (IOException e) {
      LOG.error("Could not create ZK connection!", e);
      throw new RuntimeException(e);
    }
  }

  private void acknowledge(Consumer<String> cleanerConsumer, Message<String> message)
      throws PulsarClientException {
    MessageIdImpl messageId = (MessageIdImpl) message.getMessageId();
    if (messageId.getLedgerId() % 2 == 0) {
      LOG.info("Message {} ack: [ledger: {}, entry: {}]!", message.getData(),
          messageId.getLedgerId(), messageId.getEntryId());
      cleanerConsumer.acknowledge(messageId);
    }
  }

  private MessageIdImpl sendMessage(String value) throws PulsarClientException {
    MessageIdImpl messageId = (MessageIdImpl) producer.send(value);
    LOG.info("Message {} sent: [ledger: {}, entry: {}]", value, messageId.getLedgerId(),
        messageId.getEntryId());
    return messageId;
  }
}
