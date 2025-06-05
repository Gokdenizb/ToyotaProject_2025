# Rate Processing Platform

## Overview

This project is a Spring Boot‑based microservice that collects and processes currency or price **rates** from real‑time data streams. The application:

* Uses **Fetcher** components (REST & TCP) to pull raw data from external sources.
* Processes the incoming feed through the **Coordinator** and applies formulas.
* Publishes the calculated output to **Kafka** via **KafkaRateProducer**.
* Listens to Kafka streams (**KafkaRateConsumer**, **KafkaLogConsumer**) and writes to **OpenSearch** while feeding a **Redis Stream** pub/sub.
* Exposes a **RateController** REST API for on‑demand rate queries or inserts.

Follow the steps below to set up and run everything from scratch.

---

## Prerequisites

| Component               | Recommended Version | Notes                                 |
| ----------------------- | ------------------- | ------------------------------------- |
| Java                    | 17+                 | Compatible with Spring Boot 3.x       |
| Maven                   | 3.9+                | Wrapper `mvnw` script included        |
| Docker & Docker Compose | 24.x                | Easiest way to start the full stack   |
| Kafka                   | 3.6.x               | Required only if you don’t use Docker |
| Redis                   | 7.x                 | For Stream & Pub/Sub                  |
| OpenSearch              | 2.x                 | For search & analytics                |

---

## Quick Start (Docker Compose)

The simplest path is to spin up all dependencies with Docker.

```bash
# 1) Clone the repo
git clone <REPO_URL>
cd <REPO_DIR>

# 2) Start the infrastructure
docker compose up -d kafka redis opensearch dashboards
# First run will download images and may take a couple of minutes.
```

> **Note:** The `docker-compose.yml` file lives in the project root. Edit it if you need to change port mappings.

---

## Build & Run the Application

```bash
# Build a fat jar with Maven
./mvnw clean package -DskipTests

# Run the jar
java -jar target/mainprogram-0.0.1-SNAPSHOT.jar
```

You should see the Spring banner and logs showing each component connecting.

### Switching Spring Boot Profile

The default profile is `default`. To activate the `local` profile, for example:

```bash
java -jar target/mainprogram-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

---

## Configuration

All settings reside in `src/main/resources/application.yml`. Key sections:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  data:
    redis:
      host: localhost
      port: 6379
opensearch:
  host: localhost
  port: 9200
fetcher:
  rest:
    urls:
      - https://api.example.com/rates
  tcp:
    host: feed.example.com
    port: 5000
```

Adjust values to match your environment or override them with **environment variables** (e.g., `OPENSEARCH_HOST`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`).

---

## Testing the Application

### 1. Sample REST Call

```bash
curl -X POST http://localhost:8080/api/rates/PF2_USDTRY \
     -H "Content-Type: application/json" \
     -d '{
           "symbol": "EURUSD",
           "bid": 1.0864,
           "ask": 1.0866,
           "timestamp": "2025-06-04T12:30:00Z"
         }'
```

### 2. Check Messages in Kafka

```bash
docker exec -it kafka \
  kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic rates --from-beginning
```

---

## Sample Terminal Outputs

```text
# -------- SAMPLE OUTPUTS: Simplified --------
# 2025-06-05T15:25:23.491Z  INFO 1 --- [MainProgram] [main] o.a.k.c.t.i.KafkaMetricsCollector        : initializing Kafka metrics collector
2025-06-05T15:25:23.499Z  INFO 1 --- [MainProgram] [main] o.a.kafka.common.utils.AppInfoParser     : Kafka version: 4.0.0
2025-06-05T15:25:23.499Z  INFO 1 --- [MainProgram] [main] o.a.kafka.common.utils.AppInfoParser     : Kafka commitId: 985bc99521dd22bb
2025-06-05T15:25:23.499Z  INFO 1 --- [MainProgram] [main] o.a.kafka.common.utils.AppInfoParser     : Kafka startTimeMs: 1749137123499
2025-06-05T15:25:23.500Z  INFO 1 --- [MainProgram] [main] o.a.k.c.c.i.ClassicKafkaConsumer         : [Consumer clientId=consumer-postgres-group-2, groupId=postgres-group] Subscribed to topic(s): rate-topic
2025-06-05T15:25:23.513Z  INFO 1 --- [MainProgram] [main] c.e.mainprogram.MainProgramApplication   : Started MainProgramApplication in 6.417 seconds (process running for 7.157)
2025-06-05T15:25:23.524Z  INFO 1 --- [MainProgram] [scheduling-1] com.example.mainprogram.Coordinator      : checkDataTimeouts() çalıştı
2025-06-05T15:25:23.584Z  INFO 1 --- [MainProgram] [main] com.example.mainprogram.Coordinator      : Yüklenen derived-formulas sayısı: 4
2025-06-05T15:25:23.600Z  INFO 1 --- [MainProgram] [main] com.example.mainprogram.Coordinator      : Fetcher başarıyla yüklendi: PF1 (TcpDataFetcher)
2025-06-05T15:25:23.602Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.TcpDataFetcher   : TCP sunucusuna bağlanıldı: PF1
2025-06-05T15:25:23.604Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.Coordinator      : [CONNECT] PF1 SUCCESS
2025-06-05T15:25:23.605Z  INFO 1 --- [MainProgram] [main] c.example.mainprogram.RestDataFetcher    : REST fetcher polling başlatıldı: http://rest-api:8080
2025-06-05T15:25:23.605Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.Coordinator      : [STATUS] PF1_* → null → OK
2025-06-05T15:25:23.606Z  INFO 1 --- [MainProgram] [main] com.example.mainprogram.Coordinator      : [CONNECT] PF2 SUCCESS
2025-06-05T15:25:23.607Z  INFO 1 --- [MainProgram] [main] com.example.mainprogram.Coordinator      : [STATUS] PF2_* → null → OK
2025-06-05T15:25:23.611Z  INFO 1 --- [MainProgram] [main] c.example.mainprogram.RestDataFetcher    : PF2_USDTRY için REST aboneliği başlatıldı.
2025-06-05T15:25:23.611Z  INFO 1 --- [MainProgram] [main] c.example.mainprogram.RestDataFetcher    : PF2_EURUSD için REST aboneliği başlatıldı.
2025-06-05T15:25:23.613Z  INFO 1 --- [MainProgram] [main] c.example.mainprogram.RestDataFetcher    : PF2_GBPUSD için REST aboneliği başlatıldı.
2025-06-05T15:25:23.612Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.TcpDataFetcher   : Gelen mesaj: Bağlandınız! Abonelik için: subscribe|PF1_USDTRY , PF1_EURUSD veya PF1_GBPUSD
2025-06-05T15:25:23.615Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.TcpDataFetcher   : Gelen mesaj: ✅ PF1_GBPUSD kuruna abone oldunuz.
2025-06-05T15:25:23.614Z  INFO 1 --- [MainProgram] [main] com.example.mainprogram.Coordinator      : Fetcher başarıyla yüklendi: PF2 (RestDataFetcher)
2025-06-05T15:25:23.620Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.TcpDataFetcher   : Gelen mesaj: ✅ PF1_USDTRY kuruna abone oldunuz.
2025-06-05T15:25:23.625Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.TcpDataFetcher   : Gelen mesaj: ✅ PF1_EURUSD kuruna abone oldunuz.
2025-06-05T15:25:23.639Z  INFO 1 --- [MainProgram] [tcp-data-fetcher-PF1] com.example.mainprogram.TcpDataFetcher   : Gelen mesaj: Updated rate: PF1_USDTRY|22:number:38.0609|25:number:38.1517|5:timestamp:2025-06-05 15:25:23
2025-06-05T15:25:23.807Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] c.example.mainprogram.RestDataFetcher    : Güncellenen kur verisi alındı: {"rateName":"PF2_USDTRY","bid":38.2122075585307205,"ask":38.2222075585307205,"timeStamp":"2025-06-05T15:25:19.260641838"}
2025-06-05T15:25:23.817Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] c.example.mainprogram.RestDataFetcher    : Calling onRateUpdate from REST: PF2_USDTRY → BID=38.21220755853072 ASK=38.22220755853072
2025-06-05T15:25:24.090Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] com.example.mainprogram.Coordinator      : [DERIVED][USD_MID] bid=38.16175377926536 ask=38.16175377926536 @2025-06-05T15:25:24.000968757
2025-06-05T15:25:24.097Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] com.example.mainprogram.Coordinator      : [DERIVED][USDTRY] bid=38.13655377926536 ask=38.18695377926536 @2025-06-05T15:25:24.093869757
2025-06-05T15:25:24.102Z  INFO 1 --- [MainProgram] [kafka-producer-network-thread | MainProgram-producer-1] o.a.k.c.p.internals.TransactionManager   : [Producer clientId=MainProgram-producer-1] ProducerId set to 4000 with epoch 0
2025-06-05T15:25:24.103Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] c.example.mainprogram.RestDataFetcher    : Güncellenen kur verisi alındı: {"rateName":"PF2_EURUSD","bid":1.0841557301093865485,"ask":1.08546448029027781835,"timeStamp":"2025-06-05T15:25:19.260493047"}
2025-06-05T15:25:24.105Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] c.example.mainprogram.RestDataFetcher    : Calling onRateUpdate from REST: PF2_EURUSD → BID=1.0841557301093865 ASK=1.0854644802902778
2025-06-05T15:25:24.111Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] com.example.mainprogram.Coordinator      : [RAW][PF2_EURUSD] BID=1.0841557301093865 ASK=1.0854644802902778 platform=PF2
2025-06-05T15:25:24.117Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] com.example.mainprogram.Coordinator      : [DERIVED][EURTRY] bid=41.381761089902640777056516187552 ask=41.423905980184183492799817158504 @2025-06-05T15:25:24.114812757
2025-06-05T15:25:24.124Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] c.example.mainprogram.RestDataFetcher    : Güncellenen kur verisi alındı: {"rateName":"PF2_GBPUSD","bid":1.26817653120926307886,"ask":1.26835299057877725455,"timeStamp":"2025-06-05T15:25:19.260597297"}
2025-06-05T15:25:24.125Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] c.example.mainprogram.RestDataFetcher    : Calling onRateUpdate from REST: PF2_GBPUSD → BID=1.268176531209263 ASK=1.2683529905787772
2025-06-05T15:25:24.130Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] com.example.mainprogram.Coordinator      : [RAW][PF2_GBPUSD] BID=1.268176531209263 ASK=1.2683529905787772 platform=PF2
2025-06-05T15:25:24.133Z  INFO 1 --- [MainProgram] [rest-data-fetcher-PF2] com.example.mainprogram.Coordinator      : [DERIVED][GBPTRY] bid=48.39628833775754738406517314752 ask=48.401563424952214223439511558896 @2025-06-05T15:25:24.132368174
                                  
# ----------------------------------------------------
```

---


## Development Tips

* Code reloads automatically with `spring-boot-devtools` (requires IDE support).
* Schedulers are enabled via `@EnableScheduling`. To tweak intervals, see the `@Scheduled` annotations in **Coordinator**.

---

## Contributing

Pull requests are always welcome! Please follow the code style guide and commit message template.

---

## License

MIT
