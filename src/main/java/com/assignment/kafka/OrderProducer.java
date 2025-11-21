package com.assignment.kafka;

import com.assignment.kafka.avro.Order;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;

/**
 * Kafka Producer that generates and sends order messages with Avro
 * serialization.
 */
public class OrderProducer {
    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);
    private static final String TOPIC = "orders";
    private static final String[] PRODUCTS = { "Laptop", "Mouse", "Keyboard", "Monitor", "Headphones", "Webcam",
            "USB Cable", "SSD Drive" };
    private static final Random random = new Random();

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", "http://localhost:8081");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        try (KafkaProducer<String, Order> producer = new KafkaProducer<>(props)) {
            logger.info("Starting Order Producer...");
            logger.info("Sending messages to topic: {}", TOPIC);
            logger.info("Press Ctrl+C to stop\n");

            int orderId = 1001;
            while (true) {
                // Create order message
                String orderIdStr = String.valueOf(orderId);
                String product = PRODUCTS[random.nextInt(PRODUCTS.length)];
                float price = 10.0f + (random.nextFloat() * 990.0f); // Random price between $10 and $1000

                Order order = Order.newBuilder()
                        .setOrderId(orderIdStr)
                        .setProduct(product)
                        .setPrice(price)
                        .build();

                // Send message
                ProducerRecord<String, Order> record = new ProducerRecord<>(TOPIC, orderIdStr, order);
                producer.send(record, (metadata, exception) -> {
                    if (exception == null) {
                        logger.info("✓ Sent: OrderID={}, Product={}, Price=${} | Partition={}, Offset={}",
                                order.getOrderId(),
                                order.getProduct(),
                                order.getPrice(),
                                metadata.partition(),
                                metadata.offset());
                    } else {
                        logger.error("✗ Error sending message: {}", exception.getMessage());
                    }
                });

                orderId++;
                Thread.sleep(2000); // Send a message every 2 seconds
            }
        } catch (InterruptedException e) {
            logger.info("Producer interrupted. Shutting down...");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Producer error: {}", e.getMessage(), e);
        }
    }
}
