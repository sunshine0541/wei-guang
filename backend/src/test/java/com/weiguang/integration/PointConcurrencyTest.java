package com.weiguang.integration;

import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.order.service.SettleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private SettleService settleService;
    @Autowired
    private HelpOrderMapper helpOrderMapper;

    @Test
    void concurrentSettlementKeepsPointBalanceSequential() throws Exception {
        insertUser(1L, "受助者");
        insertUser(2L, "助工");
        insertOrder(100L, 1L, 2L, "PENDING_CONFIRM", "HELPER");
        insertOrder(101L, 1L, 2L, "PENDING_CONFIRM", "HELPER");
        HelpOrder first = helpOrderMapper.selectById(100L);
        HelpOrder second = helpOrderMapper.selectById(101L);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (HelpOrder order : List.of(first, second)) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return settleService.settle(order, 1L);
            }));
        }
        ready.await();
        go.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get());
        }
        pool.shutdownNow();

        assertTrue(results.stream().allMatch(Boolean::booleanValue), "both settlements should succeed: " + results);
        List<Integer> balances = jdbc.queryForList(
                "SELECT balance_after FROM point_ledger WHERE user_id = 2 ORDER BY id", Integer.class);
        assertEquals(List.of(10, 20), balances);
    }
}
