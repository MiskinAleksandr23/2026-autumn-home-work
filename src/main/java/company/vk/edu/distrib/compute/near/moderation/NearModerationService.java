package company.vk.edu.distrib.compute.near.moderation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import company.vk.edu.distrib.compute.moderation.LinkCreatedEvent;
import company.vk.edu.distrib.compute.moderation.ModerationResultEvent;
import company.vk.edu.distrib.compute.moderation.ModerationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NearModerationService implements ModerationService {
    private static final Logger log = LoggerFactory.getLogger(NearModerationService.class);
    private static final String GROUP = "near-link-moderation";
    private static final String DEAD_LETTER_TOPIC = "links.created.dlt";
    private static final String ERROR_HEADER = "error";

    private final String bootstrapServers;
    private final List<String> bannedWords;
    private final Thread worker = Thread.ofPlatform().name("near-moderation").unstarted(this::run);
    private final AtomicBoolean running = new AtomicBoolean();

    NearModerationService(String bootstrapServers) throws IOException {
        this.bootstrapServers = bootstrapServers;
        try (var input = NearModerationService.class.getResourceAsStream("/moderation/banned_words.txt")) {
            if (input == null) {
                throw new IOException("Missing banned words");
            }
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                bannedWords = reader.lines().map(String::strip).filter(word -> !word.isEmpty())
                    .map(word -> word.toLowerCase(Locale.ROOT)).toList();
            }
        }
    }

    @Override
    public void start() {
        running.set(true);
        worker.start();
    }

    @Override
    public void stop() {
        running.set(false);
        worker.interrupt();
        try {
            worker.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        KafkaProducer<String, String> producer = KafkaUtils.producer(bootstrapServers);
        try {
            KafkaConsumer<String, String> consumer = KafkaUtils.consumer(bootstrapServers, GROUP,
                KafkaUtils.CREATED_TOPIC);
            try {
                consume(consumer, producer);
            } finally {
                KafkaUtils.shutdown(consumer);
            }
        } catch (KafkaException e) {
            if (running.get()) {
                log.warn("Moderation worker failed", e);
            }
        } finally {
            KafkaUtils.shutdown(producer);
        }
    }

    private void consume(KafkaConsumer<String, String> consumer, KafkaProducer<String, String> producer) {
        while (running.get()) {
            try {
                for (ConsumerRecord<String, String> record : consumer.poll(KafkaUtils.POLL_TIMEOUT)) {
                    if (!running.get() || !process(consumer, producer, record)) {
                        break;
                    }
                }
            } catch (KafkaException e) {
                if (running.get()) {
                    log.warn("Moderation consumer failed", e);
                }
            }
        }
    }

    private boolean process(KafkaConsumer<String, String> consumer, KafkaProducer<String, String> producer,
                            ConsumerRecord<String, String> record) {
        try {
            ModerationResultEvent result;
            try {
                LinkCreatedEvent event = KafkaUtils.created(record);
                result = moderate(event);
            } catch (IllegalArgumentException | DateTimeException e) {
                sendToDeadLetter(producer, record, e);
                KafkaUtils.commit(consumer, record);
                return true;
            }
            producer.send(new ProducerRecord<>(KafkaUtils.MODERATED_TOPIC, result.id(), KafkaUtils.encode(result)))
                .get();
            KafkaUtils.commit(consumer, record);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
        } catch (KafkaException | ExecutionException e) {
            consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
            log.warn("Moderation result not delivered", e);
        }
        return false;
    }

    private void sendToDeadLetter(KafkaProducer<String, String> producer, ConsumerRecord<String, String> record,
                                  RuntimeException error) throws InterruptedException, ExecutionException {
        ProducerRecord<String, String> rejected = new ProducerRecord<>(DEAD_LETTER_TOPIC, record.key(), record.value());
        record.headers().forEach(rejected.headers()::add);
        rejected.headers().add(ERROR_HEADER, error.toString().getBytes(StandardCharsets.UTF_8));
        producer.send(rejected).get();
    }

    private ModerationResultEvent moderate(LinkCreatedEvent event) {
        String url = URLDecoder.decode(event.url(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        boolean passed = bannedWords.stream().noneMatch(url::contains);
        return new ModerationResultEvent(event.id(), passed, passed ? "" : "Banned word", Instant.now());
    }
}
