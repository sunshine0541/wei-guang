package com.weiguang.integration;

import com.weiguang.common.exception.BizException;
import com.weiguang.modules.order.service.OrderService;
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

class AcceptConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @Test
    void concurrentAcceptOnlyOneWins() throws Exception {
        insertUser(1L, "受助者");
        insertUser(2L, "助工甲");
        insertUser(3L, "助工乙");
        insertOrder(100L, 1L, null, "PENDING", null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (long uid : List.of(2L, 3L)) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    orderService.accept(uid, 100L);
                    return 0;
                } catch (BizException e) {
                    return e.getCode();
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) {
            results.add(future.get());
        }
        pool.shutdownNow();

        assertEquals(1, results.stream().filter(code -> code == 0).count());
        assertTrue(results.contains(40001), "loser should get ACCEPT_CONFLICT, actual=" + results);
        assertEquals("ACCEPTED", jdbc.queryForObject(
                "SELECT status FROM help_order WHERE id = 100", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_session WHERE order_id = 100", Integer.class));
    }
}
