package company.vk.edu.distrib.compute.near.moderation;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import company.vk.edu.distrib.compute.moderation.LinkCreatedEvent;
import company.vk.edu.distrib.compute.moderation.ModerationResultEvent;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

final class KafkaUtils {
    static final String CREATED_TOPIC = "links.created";
    static final String MODERATED_TOPIC = "links.moderated";
    static final Duration POLL_TIMEOUT = Duration.ofMillis(200);
    static final Duration CLOSE_TIMEOUT = Duration.ofMillis(200);
    private static final int CREATED_PARTS = 3;
    private static final int RESULT_PARTS = 4;
    private static final String PASSED = "true";
    private static final String REJECTED = "false";

    private KafkaUtils() {
    }

    static KafkaProducer<String, String> producer(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1024 * 1024L);
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaProducer<>(properties);
    }

    static KafkaConsumer<String, String> consumer(String bootstrapServers, String group, String topic) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        properties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1000);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    static void shutdown(KafkaProducer<String, String> producer) {
        producer.close(CLOSE_TIMEOUT);
    }

    static void shutdown(KafkaConsumer<String, String> consumer) {
        consumer.close(CLOSE_TIMEOUT);
    }

    static void commit(KafkaConsumer<String, String> consumer, ConsumerRecord<String, String> record) {
        consumer.commitSync(Map.of(new TopicPartition(record.topic(), record.partition()),
            new OffsetAndMetadata(record.offset() + 1)));
    }

    static String encode(LinkCreatedEvent event) {
        return event.id() + "\n" + encodeText(event.url()) + "\n" + event.createdAt();
    }

    static LinkCreatedEvent created(ConsumerRecord<String, String> record) {
        String[] parts = Objects.requireNonNullElse(record.value(), "").split("\n", -1);
        if (parts.length != CREATED_PARTS || !parts[0].equals(record.key())) {
            throw new IllegalArgumentException("Bad link event");
        }
        return new LinkCreatedEvent(parts[0], decodeText(parts[1]), Instant.parse(parts[2]));
    }

    static String encode(ModerationResultEvent event) {
        return event.id() + "\n" + event.passed() + "\n" + encodeText(event.reason()) + "\n" + event.moderatedAt();
    }

    static ModerationResultEvent moderated(ConsumerRecord<String, String> record) {
        String[] parts = Objects.requireNonNull(record.value()).split("\n", -1);
        if (parts.length != RESULT_PARTS || !parts[0].equals(record.key())
            || !(PASSED.equals(parts[1]) || REJECTED.equals(parts[1]))) {
            throw new IllegalArgumentException("Bad moderation result");
        }
        return new ModerationResultEvent(parts[0], Boolean.valueOf(parts[1]), decodeText(parts[2]),
            Instant.parse(parts[3]));
    }

    private static String encodeText(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
