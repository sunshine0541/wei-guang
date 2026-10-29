package com.weiguang.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 集成测试基类：Testcontainers 临时 MySQL 8.4（用 schema.sql 初始化），不碰演示库。
 * Redis 指向不可用端口，启动探测自动降级为本地实现。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class IntegrationTestBase {

    /** 单例容器：3 条测试共用，避免每个测试类启停一次导致连接被回收 */
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("weiguang_test")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(MountableFile.forHostPath(schemaPath()),
                    "/docker-entrypoint-initdb.d/01-schema.sql")
            .withCommand("--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_general_ci",
                    "--default-time-zone=+08:00");

    static {
        MYSQL.start();
    }

    private static String schemaPath() {
        Path candidate = Path.of(System.getProperty("user.dir")).resolve("../sql/schema.sql").normalize();
        if (!Files.exists(candidate)) {
            candidate = Path.of(System.getProperty("user.dir")).resolve("sql/schema.sql").normalize();
        }
        return candidate.toAbsolutePath().toString();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "1");
        // 测试专用固定值：不读 .env，不使用真实密钥
        registry.add("JWT_SECRET", () -> "integration-test-jwt-secret-32-bytes-minimum");
        registry.add("ADMIN_SEED_USERNAME", () -> "test-admin");
        registry.add("ADMIN_SEED_PASSWORD", () -> "test-admin-password");
        // DashScope starter 启动时要求非空 key；测试不调用 AI，仅用占位值
        registry.add("DASHSCOPE_API_KEY", () -> "test-dummy-key");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()",
                String.class);
        for (String table : tables) {
            jdbc.update("DELETE FROM `" + table + "`");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    protected void insertUser(long id, String nickname) {
        jdbc.update("INSERT INTO user (id, ui_mode, motion_enabled, credit_score, violation_count, status, "
                        + "light_state, light_passed, helped_before, consecutive_release_count, deleted) "
                        + "VALUES (?, 'NORMAL', 1, 100, 0, 'NORMAL', 'UNLIT', 0, 0, 0, 0)", id);
        jdbc.update("INSERT INTO user_profile (user_id, nickname, age, hobbies, help_directions) "
                + "VALUES (?, ?, 30, '[]', '[]')", id, nickname);
    }

    /**
     * 插入最简求助单：status=PENDING 时 helperId 传 null；PENDING_CONFIRM 时补全接单/开始/标记完成时间。
     */
    protected void insertOrder(long id, long requesterId, Long helperId, String status, String confirmInitiator) {
        LocalDateTime now = LocalDateTime.now();
        boolean pending = "PENDING".equals(status);
        jdbc.update("INSERT INTO help_order (id, requester_id, helper_id, source, order_type, urgent_flag, "
                        + "urgency_base, urgency_score, title, description, tags, time_expect, status, current_round, "
                        + "release_count, age_priority, view_count, publish_deadline, accepted_at, start_deadline, "
                        + "started_at, confirm_initiator, confirm_deadline, complete_requested_at, version, created_at) "
                        + "VALUES (?, ?, ?, 'FREE', 'REMOTE', 0, 1, 1, ?, ?, '[]', 'ANY', ?, 1, 0, 0, 0, ?, ?, ?, ?, ?, ?, ?, 0, ?)",
                id, requesterId, helperId,
                "测试求助 " + id, "集成测试数据",
                status,
                now.plusHours(2),
                pending ? null : now.minusHours(2),
                pending ? null : now.minusHours(1),
                pending ? null : now.minusMinutes(90),
                pending ? null : confirmInitiator,
                pending ? null : now.plusHours(2),
                pending ? null : now.minusMinutes(10),
                now.minusHours(3));
    }
}
