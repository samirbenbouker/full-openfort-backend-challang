package com.openfort.mempool.orchestrator;

import com.openfort.mempool.client.BlocksClient;
import com.openfort.mempool.model.Stats;
import com.openfort.mempool.model.dto.SimulateBlockRequest;
import com.openfort.mempool.model.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorTest {

    @Mock
    private BlocksClient blocksClient;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() throws Exception {
        orchestrator = new Orchestrator();
        // Inyección manual del @Autowired
        setField(orchestrator, "blocksClient", blocksClient);
    }

    @Test
    void addTransaction_shouldAddToMempoolAndUpdateStats() throws Exception {
        Transaction tx1 = mock(Transaction.class); // sin stubs, no hacen falta

        orchestrator.addTransaction(tx1);

        Stats stats = orchestrator.getStats();
        assertEquals(1, stats.getMempoolSize());
        assertEquals(1, stats.getAccepted());

        List<Transaction> mempool = getMempool(orchestrator);
        assertEquals(1, mempool.size());
        assertSame(tx1, mempool.get(0));
    }


    @Test
    void takeBestBatch_shouldSortAndRespectGasLimit_andLeaveLeftoversInMempool() throws Exception {
        // fee desc, luego gasPrice asc
        Transaction a = tx(100, 6000);
        Transaction b = tx(100, 5000); // mismo fee, menor gas -> debería ir antes que 'a'
        Transaction c = tx(90, 4000);  // entrará si queda gas
        Transaction d = tx(80, 7000);  // probablemente sobrará

        orchestrator.addTransaction(a);
        orchestrator.addTransaction(b);
        orchestrator.addTransaction(c);
        orchestrator.addTransaction(d);

        List<Transaction> batch = invokeTakeBestBatch(orchestrator);

        // GAS_LIMIT = 10_000, por orden: b(5000) + a(6000) no cabe (11000),
        // entonces b(5000) + c(4000) = 9000, y el resto leftover.
        assertEquals(List.of(b, c), batch);

        List<Transaction> mempool = getMempool(orchestrator);
        assertEquals(2, mempool.size());
        // leftovers conservan el orden en el que se recorrió la mempool ya ordenada
        // orden global esperado: b, a, c, d -> leftover: a, d
        assertEquals(List.of(a, d), mempool);

        assertEquals(2, orchestrator.getStats().getMempoolSize());
    }

    @Test
    void scheduler_whenBatchEmpty_shouldNotSubmit() {
        orchestrator.scheduler();
        verifyNoInteractions(blocksClient);
        assertEquals(0, orchestrator.getStats().getSubmittedBatches());
    }

    @Test
    void scheduler_success_shouldSubmitOnce_updateStats_andNotRequeueBatch() throws Exception {
        Transaction t1 = tx(50, 2000);
        Transaction t2 = tx(20, 3000);

        orchestrator.addTransaction(t1);
        orchestrator.addTransaction(t2);

        doNothing().when(blocksClient).simulateBlock(any(SimulateBlockRequest.class));

        orchestrator.scheduler();

        ArgumentCaptor<SimulateBlockRequest> captor = ArgumentCaptor.forClass(SimulateBlockRequest.class);
        verify(blocksClient, times(1)).simulateBlock(captor.capture());

        SimulateBlockRequest req = captor.getValue();
        assertNotNull(req);

        Stats stats = orchestrator.getStats();
        assertEquals(1, stats.getSubmittedBatches());
        assertEquals(2, stats.getSubmittedTxs());
        assertEquals(70, stats.getTotalFees());      // 50 + 20
        assertEquals(5000, stats.getTotalGas());     // 2000 + 3000

        // Tras submit exitoso, esos tx no se reencolan; mempool debería quedar vacía
        assertTrue(getMempool(orchestrator).isEmpty());
        assertEquals(0, stats.getMempoolSize());
        assertEquals(0, stats.getFailedSubmits());
    }

    // --------------------------
    // Helpers
    // --------------------------

    private static Transaction tx(int fee, int gasPrice) {
        Transaction t = mock(Transaction.class);
        when(t.fee()).thenReturn(fee);
        when(t.gasPrice()).thenReturn(gasPrice);
        return t;
    }

    @SuppressWarnings("unchecked")
    private static List<Transaction> getMempool(Orchestrator orch) throws Exception {
        Field f = Orchestrator.class.getDeclaredField("mempool");
        f.setAccessible(true);
        return (List<Transaction>) f.get(orch);
    }

    private static List<Transaction> invokeTakeBestBatch(Orchestrator orch) throws Exception {
        Method m = Orchestrator.class.getDeclaredMethod("takeBestBatch");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Transaction> res = (List<Transaction>) m.invoke(orch);
        return res;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
