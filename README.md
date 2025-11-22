# Kafka Order Processing System

A Kafka-based order processing system with **Avro serialization**, **real-time price aggregation**, **retry logic**, and **Dead Letter Queue (DLQ)** handling.

## 📋 Features

- ✅ **Avro Serialization**: Order messages use Apache Avro schema
- ✅ **Real-time Aggregation**: Running average of order prices
- ✅ **Retry Logic**: Exponential backoff for temporary failures (1s, 2s, 4s)
- ✅ **Dead Letter Queue**: Permanently failed messages sent to DLQ
- ✅ **Docker Infrastructure**: Kafka, Zookeeper, and Schema Registry

## 🏗️ Architecture

```
Producer → Kafka (orders topic) → Consumer
                                     ↓
                              [Retry Logic]
                                     ↓
                              [Success/DLQ]
```

## 📦 Prerequisites

- **Java 21**
- **Maven 3.6+**
- **Docker** and **Docker Compose**

## 🚀 Quick Start

### 1. Start Kafka Infrastructure

```bash
docker-compose up -d
```

Wait ~30 seconds for all services to be ready. Verify with:

```bash
docker-compose ps
```

### 2. Generate Avro Classes

```bash
mvn clean compile
```

This generates Java classes from the Avro schema.

### 3. Run Producer

In one terminal:

```bash
mvn exec:java -Dexec.mainClass="com.assignment.kafka.OrderProducer"
```

### 4. Run Consumer

In another terminal:

```bash
mvn exec:java -Dexec.mainClass="com.assignment.kafka.OrderConsumer"
```

## 📊 Expected Output

### Producer Output
```
✓ Sent: OrderID=1001, Product=Laptop, Price=$459.23 | Partition=0, Offset=0
✓ Sent: OrderID=1002, Product=Mouse, Price=$89.45 | Partition=0, Offset=1
```

### Consumer Output
```
→ Received: OrderID=1001, Product=Laptop, Price=$459.23
✓ Processed successfully | Running Average Price: $459.23 (Total: 459.23, Count: 1)

→ Received: OrderID=1002, Product=Mouse, Price=$89.45
⚠ Processing failed for OrderID=1002 (Attempt 1/3): Simulated temporary processing failure
  Retrying in 1000ms...
✓ Processed successfully | Running Average Price: $274.34 (Total: 548.68, Count: 2)
```

## 🔧 Configuration

### Order Message Schema (`src/main/avro/order.avsc`)
```json
{
  "orderId": "string",
  "product": "string",
  "price": "float"
}
```

### Topics
- **orders**: Main topic for order messages
- **orders-dlq**: Dead Letter Queue for failed messages

### Retry Configuration
- **Max Retries**: 3
- **Backoff Strategy**: Exponential (1s, 2s, 4s)
- **Failure Rate**: 20% (simulated for demonstration)

## 🧪 Testing

### Test Retry Logic
The consumer simulates a 20% failure rate. Watch for retry attempts with exponential backoff.

### Test DLQ
After 3 failed retries, messages are sent to the `orders-dlq` topic.

### View DLQ Messages
```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders-dlq \
  --from-beginning
```

## 📁 Project Structure

```
kafka-order-system/
├── docker-compose.yml              # Kafka infrastructure
├── pom.xml                         # Maven dependencies
├── src/
│   ├── main/
│   │   ├── avro/
│   │   │   └── order.avsc         # Avro schema
│   │   ├── java/
│   │   │   └── com/assignment/kafka/
│   │   │       ├── OrderProducer.java
│   │   │       └── OrderConsumer.java
│   │   └── resources/
│   │       └── simplelogger.properties
└── README.md
```

## 🛑 Shutdown

Stop the producer and consumer with `Ctrl+C`, then:

```bash
docker-compose down
```

## 🎯 Key Concepts Demonstrated

1. **Avro Schema Evolution**: Structured data with schema registry
2. **Producer-Consumer Pattern**: Decoupled message processing
3. **Fault Tolerance**: Retry logic with exponential backoff
4. **Error Handling**: DLQ for unrecoverable failures
5. **Stateful Processing**: Running average calculation

## 📚 Technologies Used

- Apache Kafka 3.6.0
- Apache Avro 1.11.3
- Confluent Schema Registry 7.5.0
- Java 21
- Maven
- Docker

## 🤝 Author

Created for Big Data Assignment - Kafka Order Processing System

---

**Happy Streaming! 🚀**
