# FlowGen

Scans Spring Boot annotations and generates Mermaid flowcharts. No code changes needed.

## Quick Start

Add to your project's `pom.xml`:

```xml
<plugin>
    <groupId>com.flowgen</groupId>
    <artifactId>flowgen-maven-plugin</artifactId>
    <version>1.0.0</version>
</plugin>
```

Run:

```bash
mvn flowgen:generate
```

Output:

```
docs/flowgen/
├── index.md
├── OrderController/
│   ├── placeOrder.md
│   ├── getOrder.md
│   └── cancelOrder.md
├── ProductController/
│   ├── listProducts.md
│   ├── getProduct.md
│   └── updatePrice.md
├── PaymentEventListener/
│   └── onPaymentEvent.md
└── InventoryScheduler/
    └── syncInventory.md
```

## Configuration

```xml
<plugin>
    <groupId>com.flowgen</groupId>
    <artifactId>flowgen-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <outputDirectory>docs/flowgen</outputDirectory> <!-- default -->
    </configuration>
</plugin>
```

To run automatically during build:

```xml
<executions>
    <execution>
        <goals><goal>generate</goal></goals>
        <phase>compile</phase>
    </execution>
</executions>
```

## Supported Annotations

| Annotation | Detected As |
|-----------|------------|
| `@GetMapping` | REST GET |
| `@PostMapping` | REST POST |
| `@PutMapping` | REST PUT |
| `@DeleteMapping` | REST DELETE |
| `@PatchMapping` | REST PATCH |
| `@RequestMapping` (class-level) | URL prefix |
| `@KafkaListener` | Kafka Listener |
| `@Scheduled` | Scheduler |
| `@RabbitListener` | RabbitMQ Listener |
| `@SqsListener` | SQS Listener |
| `@JmsListener` | JMS Listener |
| `@EventListener` | Event Listener |

## Diagram Legend

| Code Pattern | Node Style |
|-------------|-----------|
| `if/else`, `switch` | 🟡 Yellow diamond |
| `for`, `while`, `forEach` | 🟣 Purple loop |
| `try/catch/finally` | 🔵 Blue try, 🔴 Red catch |
| `return` | 🟢 Green exit |
| `throw` | 🔴 Red exit |
| `kafkaTemplate.send()`, `repository.save()`, etc. | 🟠 Orange outbound |
