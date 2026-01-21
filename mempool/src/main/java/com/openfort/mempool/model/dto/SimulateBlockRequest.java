package com.openfort.mempool.model.dto;

import com.openfort.mempool.model.transaction.Transaction;

import java.util.List;

public record SimulateBlockRequest(List<Transaction> transactions) {
}
