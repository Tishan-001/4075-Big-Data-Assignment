# Kafka Order Processing System - Architecture

## System Overview

This system implements a real-time order processing pipeline using Apache Kafka with Avro serialization, featuring retry logic, Dead Letter Queue (DLQ) handling, and price aggregation.

## Architecture Diagram

```mermaid
graph TB
    subgraph "Producer Application"
        P[OrderProducer<br/>Java Application]
    end
    
    subgraph "Docker Infrastructure"
        ZK[Zookeeper<br/>Port: 2181]
        
        subgraph "Kafka Broker"
            K[Kafka<br/>Internal: 9092<br/>External: 29092]
            T1[orders Topic]
            T2[orders-dlq Topic]
        end
        
        SR[Schema Registry<br/>Port: 8081<br/>Stores Avro Schemas]
    end
    
    subgraph "Consumer Application"
        C[OrderConsumer<br/>Java Application]
        RL[Retry Logic<br/>Max 3 attempts<br/>Exponential Backoff]
        AGG[Price Aggregator<br/>Running Average]
    end
    
    subgraph "Message Flow"
        direction LR
        M1[Order Message<br/>Avro Serialized]
        M2[Failed Message<br/>with Error Metadata]
    end
    
    P -->|1. Generate Orders| M1
    M1 -->|2. Serialize with Schema| SR
    M1 -->|3. Publish| T1
    
    T1 -->|4. Poll Messages| C
    C -->|5. Deserialize| SR
    C -->|6. Process| RL
    
    RL -->|Success| AGG
    RL -->|Max Retries Failed| M2
    M2 -->|7. Send to DLQ| T2
    
    K -.->|Coordination| ZK
    SR -.->|Connect| K
    
    style P fill:#4CAF50
    style C fill:#2196F3
    style T1 fill:#FF9800
    style T2 fill:#F44336
    style SR fill:#9C27B0
    style AGG fill:#00BCD4
    style RL fill:#FFC107
```

## Component Details

### 1. **OrderProducer** (Producer Application)
- **Purpose**: Generates random order messages
- **Configuration**:
  - Bootstrap Server: `localhost:29092`
  - Serialization: Avro (KafkaAvroSerializer)
  - Target Topic: `orders`
- **Behavior**:
  - Creates orders every 2 seconds
  - Random products from predefined list
  - Random prices ($10 - $1000)
  - Uses Schema Registry for Avro schema validation

### 2. **Kafka Broker**
- **Dual Listener Setup**:
  - **INTERNAL** (`kafka:9092`): For Docker container communication
  - **EXTERNAL** (`localhost:29092`): For host applications
- **Topics**:
  - `orders`: Main topic for order messages
  - `orders-dlq`: Dead Letter Queue for failed messages

### 3. **Schema Registry**
- **Purpose**: Centralized Avro schema management
- **Port**: 8081
- **Function**:
  - Stores and versions Avro schemas
  - Validates message compatibility
  - Enables schema evolution

### 4. **OrderConsumer** (Consumer Application)
- **Purpose**: Processes orders with fault tolerance
- **Configuration**:
  - Bootstrap Server: `localhost:29092`
  - Consumer Group: `order-consumer-group`
  - Deserialization: Avro (KafkaAvroDeserializer)

### 5. **Retry Logic**
- **Max Retries**: 3 attempts
- **Backoff Strategy**: Exponential (1s, 2s, 4s)
- **Failure Simulation**: 20% random failure rate (for demo)
- **On Max Retries**: Send to DLQ

### 6. **Price Aggregator**
- **Function**: Calculates running average of processed orders
- **Metrics Tracked**:
  - Total price sum
  - Message count
  - Running average price

### 7. **Dead Letter Queue (DLQ)**
- **Topic**: `orders-dlq`
- **Metadata Headers**:
  - `error.message`: Failure reason
  - `retry.count`: Number of retry attempts
  - `timestamp`: Failure timestamp

## Data Flow

```mermaid
sequenceDiagram
    participant P as OrderProducer
    participant SR as Schema Registry
    participant K as Kafka (orders)
    participant C as OrderConsumer
    participant DLQ as Kafka (orders-dlq)
    
    P->>SR: 1. Get/Register Avro Schema
    SR-->>P: Schema ID
    P->>K: 2. Publish Order (Avro)
    
    C->>K: 3. Poll Messages
    K-->>C: Order Record
    C->>SR: 4. Fetch Schema by ID
    SR-->>C: Avro Schema
    C->>C: 5. Deserialize Order
    
    alt Processing Success
        C->>C: 6a. Process Order
        C->>C: 7a. Update Running Average
    else Processing Failure (Retry 1-3)
        C->>C: 6b. Retry with Backoff
        alt Retry Success
            C->>C: 7b. Update Running Average
        else Max Retries Reached
            C->>DLQ: 8. Send to DLQ with Metadata
        end
    end
```

## Avro Schema Structure

```json
{
  "type": "record",
  "name": "Order",
  "namespace": "com.assignment.kafka.avro",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "product", "type": "string"},
    {"name": "price", "type": "float"}
  ]
}
```

## Network Configuration

```mermaid
graph LR
    subgraph "Host Machine"
        JA[Java Applications<br/>Producer & Consumer]
    end
    
    subgraph "Docker Network: kafka-network"
        ZK2[Zookeeper<br/>2181]
        K2[Kafka<br/>INTERNAL: 9092<br/>EXTERNAL: 29092]
        SR2[Schema Registry<br/>8081]
    end
    
    JA -->|localhost:29092| K2
    JA -->|localhost:8081| SR2
    SR2 -->|kafka:9092| K2
    K2 -->|zookeeper:2181| ZK2
    
    style JA fill:#4CAF50
    style K2 fill:#FF9800
```

## Key Features

### ✅ **Fault Tolerance**
- Retry logic with exponential backoff
- Dead Letter Queue for permanent failures
- Error metadata preservation

### ✅ **Schema Management**
- Centralized schema registry
- Avro serialization for efficiency
- Schema evolution support

### ✅ **Real-time Processing**
- Continuous message consumption
- Running average calculation
- Low-latency processing

### ✅ **Observability**
- Detailed logging with SLF4J
- Success/failure tracking
- Performance metrics (partition, offset)

## Message Processing States

```mermaid
stateDiagram-v2
    [*] --> Received: Message Polled
    Received --> Processing: Deserialize
    Processing --> Success: Process OK
    Processing --> Retry1: Failure (20%)
    Retry1 --> Success: Retry OK
    Retry1 --> Retry2: Failure
    Retry2 --> Success: Retry OK
    Retry2 --> Retry3: Failure
    Retry3 --> Success: Retry OK
    Retry3 --> DLQ: Max Retries
    Success --> Aggregated: Update Stats
    Aggregated --> [*]
    DLQ --> [*]
```

## Performance Characteristics

- **Message Rate**: 1 message every 2 seconds (configurable)
- **Processing Latency**: ~1-7 seconds (including retries)
- **Failure Rate**: 20% simulated (for demonstration)
- **Retry Delays**: 1s → 2s → 4s (exponential backoff)
- **Serialization**: Avro (compact binary format)

