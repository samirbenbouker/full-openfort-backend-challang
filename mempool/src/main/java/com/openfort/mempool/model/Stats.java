package com.openfort.mempool.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Stats {
    private int mempoolSize;
    private long accepted;
    private long submittedBatches;
    private long submittedTxs;
    private long totalFees;
    private long totalGas;
    private long failedSubmits;
}
