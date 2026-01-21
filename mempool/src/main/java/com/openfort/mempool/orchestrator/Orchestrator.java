package com.openfort.mempool.orchestrator;

import com.openfort.mempool.client.BlocksClient;
import com.openfort.mempool.model.Stats;
import com.openfort.mempool.model.dto.SimulateBlockRequest;
import com.openfort.mempool.model.transaction.Transaction;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class Orchestrator {

    // Maximum total gas allowed per batch
    private static final int GAS_LIMIT = 10_000;

    // Client responsible for simulating / submitting blocks
    @Autowired
    private BlocksClient blocksClient;

    // Lock protecting concurrent access to the mempool
    private final ReentrantLock mempoolLock = new ReentrantLock();

    // Lock to ensure only one batch submission happens at a time
    private final ReentrantLock submitLock = new ReentrantLock();

    // In-memory pool of pending transactions
    private final List<Transaction> mempool = new ArrayList<>();

    // Aggregated runtime statistics
    @Getter
    private final Stats stats = new Stats();

    /**
     * Adds a transaction to the mempool in a thread-safe manner
     * and updates basic statistics.
     */
    public void addTransaction(Transaction tx) {
        mempoolLock.lock();
        try {
            mempool.add(tx);
            stats.setMempoolSize(mempool.size());
            stats.setAccepted(stats.getAccepted() + 1);
        } finally {
            mempoolLock.unlock();
        }
    }

    /**
     * Selects the best possible batch of transactions from the mempool.
     *
     * Transactions are sorted by:
     *  1. Highest fee first
     *  2. Lowest gas price first (tie-breaker)
     *
     * Transactions are added to the batch until the gas limit is reached.
     * Remaining transactions are kept in the mempool.
     */
    private List<Transaction> takeBestBatch() {
        mempoolLock.lock();
        try {
            if (mempool.isEmpty()) {
                return List.of();
            }

            // Sort by fee (descending), then gas price (ascending)
            mempool.sort(
                    Comparator.comparingInt(Transaction::fee).reversed()
                            .thenComparingInt(Transaction::gasPrice)
            );

            List<Transaction> batch = new ArrayList<>();
            List<Transaction> leftover = new ArrayList<>();

            int gas = 0;

            // Greedily fill the batch respecting the gas limit
            for (Transaction tx : mempool) {
                if (gas + tx.gasPrice() <= GAS_LIMIT) {
                    batch.add(tx);
                    gas += tx.gasPrice();
                } else {
                    leftover.add(tx);
                }
            }

            // Replace mempool contents with leftover transactions
            mempool.clear();
            mempool.addAll(leftover);
            stats.setMempoolSize(mempool.size());

            return batch;
        } finally {
            mempoolLock.unlock();
        }
    }

    /**
     * Periodic scheduler that attempts to submit the best batch
     * of transactions at a fixed interval.
     */
    @Scheduled(fixedDelayString = "${batch.tick-ms:250}")
    public void scheduler() {
        List<Transaction> batch = takeBestBatch();

        // Nothing to submit
        if (batch.isEmpty()) {
            return;
        }

        submitLock.lock();
        try {
            int attempts = 0;
            long backoff = 200;

            // Retry submission up to 8 times with exponential backoff
            while (attempts++ < 8) {
                try {
                    blocksClient.simulateBlock(new SimulateBlockRequest(batch));

                    // Successful submission: update statistics
                    stats.setSubmittedBatches(stats.getSubmittedBatches() + 1);
                    stats.setSubmittedTxs(stats.getSubmittedTxs() + batch.size());

                    batch.forEach(tx -> {
                        stats.setTotalFees(stats.getTotalFees() + tx.fee());
                        stats.setTotalGas(stats.getTotalGas() + tx.gasPrice());
                    });

                    return;
                } catch (Exception e) {
                    // Backoff before retrying
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }

                    backoff = Math.min(backoff * 2, 1500);
                }
            }

            // All attempts failed
            stats.setFailedSubmits(stats.getFailedSubmits() + 1);

            // Reinsert failed batch back into the mempool
            mempoolLock.lock();
            try {
                mempool.addAll(batch);
                stats.setMempoolSize(mempool.size());
            } finally {
                mempoolLock.unlock();
            }
        } finally {
            submitLock.unlock();
        }
    }
}
