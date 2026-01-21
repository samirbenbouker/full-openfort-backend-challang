# Mempool Orchestrator

A lightweight Spring Boot service that accepts transactions, maintains an in-memory mempool, batches transactions based on economic priority, and submits them to an external **Blocks** service for simulation.

---

## Overview

This project implements a simplified **transaction mempool and batch orchestrator**, similar in spirit to how blockchain mempools operate.

The system:

* Accepts transactions via a REST API
* Fetches current pricing information at submission time
* Stores transactions in an in-memory mempool
* Periodically selects the best batch of transactions based on fee and gas constraints
* Submits batches to an external Blocks service with retry and backoff logic
* Exposes runtime statistics for monitoring and debugging

---

## Architecture

### Main Components

#### 1. **TransactionController**

REST API layer that:

* Accepts new transactions (`POST /transactions`)
* Exposes system statistics (`GET /stats`)

#### 2. **Orchestrator**

Core batching and scheduling engine that:

* Manages the in-memory mempool
* Selects optimal batches under a gas limit
* Handles retries and exponential backoff
* Tracks detailed runtime statistics
* Ensures thread safety using locks

#### 3. **BlocksClient**

HTTP client responsible for:

* Fetching current gas price and fee data
* Submitting transaction batches for simulation

---

## Transaction Flow

1. A client submits a transaction via `POST /transactions`
2. The service fetches current pricing from the Blocks service
3. The transaction is added to the mempool
4. A scheduled task periodically:

    * Selects the best batch of transactions
    * Submits the batch to the Blocks service
    * Retries on failure with exponential backoff
5. Statistics are updated and exposed via `GET /stats`

---

## Batch Selection Strategy

Transactions are:

1. Sorted by **highest fee first**
2. Tie-broken by **lowest gas price**
3. Added greedily until the batch reaches the gas limit

Transactions that do not fit remain in the mempool for future batches.

---

## Concurrency Model

* **Reentrant locks** are used to ensure thread safety
* Separate locks are used for:

    * Mempool access
    * Batch submission
* This prevents:

    * Concurrent modifications of the mempool
    * Multiple batch submissions at the same time

---

## Retry & Backoff Policy

* Up to **8 submission attempts**
* Initial backoff: **200 ms**
* Exponential backoff capped at **1500 ms**
* Failed batches are reinserted into the mempool

---

## Configuration

### Application Properties

```properties
# Interval between batch attempts (milliseconds)
batch.tick-ms=250

# Base URL of the Blocks service
blocks.base-url=http://blocks:8080
```

---

## API Endpoints

### Submit Transaction

```
POST /transactions
```

**Request body**

```json
{
  "id": "tx-123"
}
```

**Response**

```json
{
  "accepted": true,
  "tx": {
    "id": "tx-123",
    "gasPrice": 123,
    "fee": 45
  }
}
```

---

### Get Stats

```
GET /stats
```

**Response**

```json
{
  "mempoolSize": 5,
  "accepted": 20,
  "submittedBatches": 3,
  "submittedTxs": 12,
  "failedSubmits": 1,
  "totalFees": 450,
  "totalGas": 9800
}
```

---

## Design Decisions

* **In-memory mempool**
  Simple and fast, suitable for a single-instance service.

* **Blocking WebClient calls**
  Acceptable here because batch submission is already off the request path
  and controlled by a scheduler.

* **No persistence**
  State is ephemeral by design; restarting the service clears the mempool.

* **Explicit locking**
  Chosen over synchronized collections to clearly separate critical sections.

---

## Limitations

* No persistence or crash recovery
* Single-node only (no distributed coordination)
* No authentication or rate limiting
* Blocking calls inside scheduler

These trade-offs are intentional to keep the implementation focused and easy to reason about.

---

## Running the Project

### Requirements

* Java 17+
* Maven or Gradle
* A running Blocks service

### Run locally

```bash
./mvnw spring-boot:run
```

or

```bash
./gradlew bootRun
```

---

## Future Improvements

* Persist mempool and stats
* Replace blocking calls with non-blocking pipelines
* Distributed batching and leader election
* Metrics export (Prometheus)
* Dead-letter queue for permanently failing batches

---

## License

MIT License
