package company.vk.edu.distrib.compute.near.moderation;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.moderation.LinkCreatedEvent;
import company.vk.edu.distrib.compute.moderation.ModerationResultEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShortenerModeration implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ShortenerModeration.class);

    private final String bootstrapServers;
    private final String group;
    private final Dao<String> links;
    private final BlockingQueue<LinkCreatedEvent> pending = new ArrayBlockingQueue<>(1024);
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final AtomicBoolean running = new AtomicBoolean();

    public ShortenerModeration(String bootstrapServers, int port, Dao<String> links) {
        this.bootstrapServers = bootstrapServers;
        group = "near-shortener-" + port;
        this.links = links;
    }

    public void start() {
        running.set(true);
        workers.execute(this::publish);
        workers.execute(this::consume);
    }

    public void created(String id, String url) {
        if (!pending.offer(new LinkCreatedEvent(id, url, Instant.now()))) {
            log.warn("Moderation queue full: {}", id);
        }
    }

    private void publish() {
        KafkaProducer<String, String> producer = KafkaUtils.producer(bootstrapServers);
        try {
            while (running.get()) {
                publish(producer, pending.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (KafkaException e) {
            if (running.get()) {
                log.warn("Link publisher failed", e);
            }
        } finally {
            KafkaUtils.shutdown(producer);
        }
    }

    private void publish(KafkaProducer<String, String> producer, LinkCreatedEvent event) throws InterruptedException {
        ProducerRecord<String, String> record = new ProducerRecord<>(KafkaUtils.CREATED_TOPIC,
            event.id(), KafkaUtils.encode(event));
        while (running.get()) {
            try {
                producer.send(record).get();
                return;
            } catch (KafkaException | ExecutionException e) {
                log.warn("Kafka send failed", e);
                Thread.sleep(200);
            }
        }
    }

    private void consume() {
        KafkaConsumer<String, String> consumer = KafkaUtils.consumer(bootstrapServers, group,
            KafkaUtils.MODERATED_TOPIC);
        try {
            while (running.get()) {
                try {
                    for (ConsumerRecord<String, String> record : consumer.poll(KafkaUtils.POLL_TIMEOUT)) {
                        if (!running.get() || !apply(consumer, record)) {
                            break;
                        }
                    }
                } catch (KafkaException e) {
                    if (running.get()) {
                        log.warn("Moderation results unavailable", e);
                    }
                }
            }
        } finally {
            KafkaUtils.shutdown(consumer);
        }
    }

    private boolean apply(KafkaConsumer<String, String> consumer, ConsumerRecord<String, String> record) {
        try {
            try {
                ModerationResultEvent result = KafkaUtils.moderated(record);
                if (!result.passed()) {
                    links.delete(result.id());
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid moderation result", e);
            }
            KafkaUtils.commit(consumer, record);
            return true;
        } catch (IOException e) {
            consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
            log.warn("Cannot remove moderated link", e);
            return false;
        }
    }

    @Override
    public void close() {
        running.set(false);
        workers.shutdownNow();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
