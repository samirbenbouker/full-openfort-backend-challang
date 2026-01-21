package com.openfort.mempool.controller;

import com.openfort.mempool.client.BlocksClient;
import com.openfort.mempool.model.Stats;
import com.openfort.mempool.model.dto.PriceResponse;
import com.openfort.mempool.model.dto.SubmitTxRequest;
import com.openfort.mempool.model.transaction.Transaction;
import com.openfort.mempool.orchestrator.Orchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TransactionController {

    // Orchestrator responsible for managing the mempool and batching logic
    @Autowired
    private Orchestrator orch;

    // Client used to retrieve current network pricing information
    @Autowired
    private BlocksClient blocks;

    /**
     * Accepts a transaction submission request and enqueues it into the mempool.
     *
     * The request is validated to ensure it contains a non-empty transaction ID.
     * Current network pricing is fetched to determine the transaction's fee
     * and gas price at submission time.
     *
     * @param req the incoming transaction submission request
     * @return HTTP 400 if the request is invalid, otherwise HTTP 200 with
     *         confirmation and transaction details
     */
    @PostMapping("/transactions")
    public ResponseEntity<?> submit(@RequestBody SubmitTxRequest req) {

        // Basic validation: transaction ID must be present
        if (req.id() == null || req.id().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Retrieve current gas price and fee from the network
        PriceResponse price = blocks.getCurrentPrice();

        // Create a new transaction using current pricing
        Transaction tx = new Transaction(
                req.id(),
                price.gasPrice(),
                price.fee()
        );

        // Add the transaction to the orchestrator's mempool
        orch.addTransaction(tx);

        // Respond with confirmation and the accepted transaction
        return ResponseEntity.ok(
                Map.of("accepted", true, "tx", tx)
        );
    }

    /**
     * Exposes current runtime statistics of the mempool and batch processing.
     *
     * @return aggregated statistics collected by the orchestrator
     */
    @GetMapping("/stats")
    public Stats stats() {
        return orch.getStats();
    }
}
