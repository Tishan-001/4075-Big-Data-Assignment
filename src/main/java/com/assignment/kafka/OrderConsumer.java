package com.assignment.kafka;

import com.assignment.kafka.avro.Order;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.Random;

/**
 * Kafka Consumer that processes order messages with:
 * - Real-time price aggregation (running average)
 * - Retry logic for temporary failures
 * - Dead Letter Queue (DLQ) for permanently failed messages
 */
public class OrderConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);
    private static final String TOPIC = "orders";
    private static final String DLQ_TOPIC = "orders-dlq";
    private static final int MAX_RETRIES = 3;
    private static final Random random = new Random();

    // Aggregation state
    private static double totalPrice = 0.0;
    private static int messageCount = 0;

    public static void main(String[] args) {
        // Consumer properties
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "order-consumer-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        consumerProps.put("schema.registry.url", "http://localhost:8081");
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        // DLQ Producer properties
        Properties dlqProps = new Properties();
        dlqProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        dlqProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        dlqProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        dlqProps.put("schema.registry.url", "http://localhost:8081");

        try (KafkaConsumer<String, Order> consumer = new KafkaConsumer<>(consumerProps);
                KafkaProducer<String, Order> dlqProducer = new KafkaProducer<>(dlqProps)) {

            consumer.subscribe(Collections.singletonList(TOPIC));
            logger.info("Starting Order Consumer...");
            logger.info("Subscribed to topic: {}", TOPIC);
            logger.info("Press Ctrl+C to stop\n");

            while (true) {
                ConsumerRecords<String, Order> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, Order> record : records) {
                    Order order = record.value();
                    logger.info("→ Received: OrderID={}, Product={}, Price=${}",
                            order.getOrderId(),
                            order.getProduct(),
                            order.getPrice());

                    // Process message with retry logic
                    boolean processed = processMessageWithRetry(order, dlqProducer);

                    if (processed) {
                        // Update running average
                        totalPrice += order.getPrice();
                        messageCount++;
                        double runningAverage = totalPrice / messageCount;

                        logger.info(
                                "✓ Processed successfully | Running Average Price: ${} (Total: ${}, Count: {})\n",
                                runningAverage, totalPrice, messageCount);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Consumer error: {}", e.getMessage(), e);
        }
    }

    /**
     * Process message with retry logic for temporary failures.
     * Sends to DLQ after max retries.
     */
    private static boolean processMessageWithRetry(Order order, KafkaProducer<String, Order> dlqProducer) {
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                // Simulate processing with random failures (20% failure rate for demonstration)
                if (random.nextDouble() < 0.2) {
                    throw new RuntimeException("Simulated temporary processing failure");
                }

                // Successful processing
                return true;

            } catch (Exception e) {
                retryCount++;
                logger.warn("⚠ Processing failed for OrderID={} (Attempt {}/{}): {}",
                        order.getOrderId(), retryCount, MAX_RETRIES, e.getMessage());

                if (retryCount < MAX_RETRIES) {
                    // Exponential backoff: 1s, 2s, 4s
                    long backoffMs = (long) Math.pow(2, retryCount - 1) * 1000;
                    logger.info("  Retrying in {}ms...", backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    // Max retries reached - send to DLQ
                    logger.error("✗ Max retries reached for OrderID={}. Sending to DLQ.", order.getOrderId());
                    sendToDLQ(order, dlqProducer, e.getMessage(), retryCount);
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * Send failed message to Dead Letter Queue.
     */
    private static void sendToDLQ(Order order, KafkaProducer<String, Order> dlqProducer,
            String errorMessage, int retryCount) {
        try {
            ProducerRecord<String, Order> dlqRecord = new ProducerRecord<String, Order>(DLQ_TOPIC,
                    order.getOrderId().toString(), order);

            // Add error metadata as headers
            dlqRecord.headers().add("error.message", errorMessage.getBytes());
            dlqRecord.headers().add("retry.count", String.valueOf(retryCount).getBytes());
            dlqRecord.headers().add("timestamp", String.valueOf(System.currentTimeMillis()).getBytes());

            dlqProducer.send(dlqRecord, (metadata, exception) -> {
                if (exception == null) {
                    logger.info("  ➜ Sent to DLQ: OrderID={}, Topic={}, Partition={}, Offset={}",
                            order.getOrderId(), DLQ_TOPIC, metadata.partition(), metadata.offset());
                } else {
                    logger.error("  Failed to send to DLQ: {}", exception.getMessage());
                }
            });

        } catch (Exception e) {
            logger.error("Error sending to DLQ: {}", e.getMessage(), e);
        }
    }
}
