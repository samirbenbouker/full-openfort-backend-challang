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

## Configuration

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

### Get Stats

```
GET /stats
```

---

## Example Usage (bash)

> These examples assume the service is running locally on **port 9090**
> and that `jq` is installed for JSON formatting.

### Check service statistics

```bash
curl -s localhost:9090/stats | jq
```

---

### Submit a single transaction

```bash
curl -s -X POST localhost:9090/transactions \
  -H 'Content-Type: application/json' \
  -d '{"id":"tx-price-1"}' | jq

curl -s -X POST localhost:9090/transactions \
  -H 'Content-Type: application/json' \
  -d '{"id":"tx-price-2"}' | jq
```

---

### Stress test: submit many transactions quickly

```bash
for i in {1..50}; do
  curl -s -X POST localhost:9090/transactions \
    -H 'Content-Type: application/json' \
    -d "{\"id\":\"tx-load-$i\"}" > /dev/null
done

curl -s localhost:9090/stats | jq
```

---

### Observe batching behavior

Run this in one terminal:

```bash
watch -n 1 'curl -s localhost:9090/stats | jq'
```

And in another terminal submit transactions:

```bash
for i in {1..20}; do
  curl -s -X POST localhost:9090/transactions \
    -H 'Content-Type: application/json' \
    -d "{\"id\":\"tx-live-$i\"}" | jq
  sleep 0.1
done
```

---

## Design Decisions

* **In-memory mempool**
  Simple and fast, suitable for a single-instance service.

* **Blocking WebClient calls**
  Acceptable here because batch submission is already off the request path.

* **No persistence**
  State is ephemeral by design.

* **Explicit locking**
  Chosen to clearly separate critical sections.

---

## Limitations

* No persistence or crash recovery
* Single-node only
* No authentication or rate limiting
* Blocking calls inside scheduler

---

## License

MIT License
