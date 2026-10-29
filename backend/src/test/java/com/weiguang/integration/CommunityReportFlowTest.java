package com.weiguang.integration;

import com.weiguang.common.exception.BizException;
import com.weiguang.modules.community.dto.ReportCreateRequest;
import com.weiguang.modules.community.service.CommunityService;
import com.weiguang.modules.report.dto.ReportHandleRequest;
import com.weiguang.modules.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommunityReportFlowTest extends IntegrationTestBase {

    @Autowired
    private CommunityService communityService;
    @Autowired
    private ReportService reportService;

    @Test
    void reportPostDuplicateAndAdminHandleWithCreditBan() {
        insertUser(1L, "发帖邻居");
        insertUser(2L, "举报邻居");
        insertPost(500L, 1L, "今天在小区门口捡到一串钥匙");
        insertComment(600L, 500L, 1L, "可以交到物业");

        ReportCreateRequest postReport = new ReportCreateRequest();
        postReport.setTargetType("POST");
        postReport.setTargetId(500L);
        postReport.setReason("疑似广告");
        communityService.report(2L, postReport);

        Long reportId = jdbc.queryForObject(
                "SELECT id FROM report WHERE reporter_id = 2 AND target_type = 'POST' AND target_id = 500",
                Long.class);
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM report WHERE id = ?", String.class, reportId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT target_user_id FROM report WHERE id = ?", Long.class, reportId));

        var adminPage = reportService.adminList(null, 1L, 20L);
        var adminRow = adminPage.getRecords().stream()
                .filter(item -> reportId.equals(item.getId())).findFirst().orElseThrow();
        assertEquals(500L, adminRow.getTargetPostId());
        assertEquals("今天在小区门口捡到一串钥匙", adminRow.getTargetContent());
        assertEquals("PUBLISHED", adminRow.getTargetStatus());

        BizException duplicate = assertThrows(BizException.class, () -> communityService.report(2L, postReport));
        assertEquals(40003, duplicate.getCode());

        ReportHandleRequest handle = new ReportHandleRequest();
        handle.setResult("CONFIRMED");
        handle.setNote("确认是广告");
        handle.setAction("CREDIT_MINUS");
        handle.setCreditDelta(-20);
        reportService.handle(99L, reportId, handle);

        assertEquals("CONFIRMED", jdbc.queryForObject(
                "SELECT status FROM report WHERE id = ?", String.class, reportId));
        assertEquals(80, jdbc.queryForObject(
                "SELECT credit_score FROM user WHERE id = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = 1 AND type = 'ADMIN_ACTION'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = 2 AND type = 'REPORT_RESULT'", Integer.class));

        // 评论举报 -> 确认违规并封禁
        ReportCreateRequest commentReport = new ReportCreateRequest();
        commentReport.setTargetType("COMMENT");
        commentReport.setTargetId(600L);
        commentReport.setReason("言语不当");
        communityService.report(2L, commentReport);
        Long commentReportId = jdbc.queryForObject(
                "SELECT id FROM report WHERE reporter_id = 2 AND target_type = 'COMMENT' AND target_id = 600",
                Long.class);
        var commentRow = reportService.adminList(null, 1L, 20L).getRecords().stream()
                .filter(item -> commentReportId.equals(item.getId())).findFirst().orElseThrow();
        assertEquals(500L, commentRow.getTargetPostId());
        assertEquals("可以交到物业", commentRow.getTargetContent());

        ReportHandleRequest ban = new ReportHandleRequest();
        ban.setResult("CONFIRMED");
        ban.setNote("确认违规，封禁账号");
        ban.setAction("BAN");
        reportService.handle(99L, commentReportId, ban);
        assertEquals("BANNED", jdbc.queryForObject(
                "SELECT status FROM user WHERE id = 1", String.class));

        // 误报：不改微光值
        insertUser(3L, "另一个邻居");
        insertPost(501L, 3L, "周末有人一起去公园吗");
        ReportCreateRequest falseReport = new ReportCreateRequest();
        falseReport.setTargetType("POST");
        falseReport.setTargetId(501L);
        falseReport.setReason("不确定");
        communityService.report(2L, falseReport);
        Long falseReportId = jdbc.queryForObject(
                "SELECT id FROM report WHERE reporter_id = 2 AND target_type = 'POST' AND target_id = 501",
                Long.class);

        ReportHandleRequest rejected = new ReportHandleRequest();
        rejected.setResult("REJECTED");
        rejected.setNote("内容正常");
        rejected.setAction("NONE");
        reportService.handle(99L, falseReportId, rejected);
        assertEquals("REJECTED", jdbc.queryForObject(
                "SELECT status FROM report WHERE id = ?", String.class, falseReportId));
        assertEquals(100, jdbc.queryForObject(
                "SELECT credit_score FROM user WHERE id = 3", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = 2 AND type = 'REPORT_RESULT'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = 2 AND type = 'REPORT_RESULT' "
                        + "AND content LIKE '%未成立%'", Integer.class));
    }

    private void insertPost(long id, long authorId, String content) {
        jdbc.update("INSERT INTO community_post (id, author_id, category, title, content, images, status, "
                        + "review_degraded, like_count, comment_count, created_at) "
                        + "VALUES (?, ?, 'NORMAL', NULL, ?, '[]', 'PUBLISHED', 0, 0, 0, NOW())",
                id, authorId, content);
    }

    private void insertComment(long id, long postId, long authorId, String content) {
        jdbc.update("INSERT INTO community_comment (id, post_id, author_id, content, status, created_at) "
                + "VALUES (?, ?, ?, ?, 'PUBLISHED', NOW())", id, postId, authorId, content);
    }
}
