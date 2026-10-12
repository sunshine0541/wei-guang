package com.weiguang.modules.admin.dto;

import com.weiguang.modules.incentive.dto.LedgerVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /admin/users/{id} 详情（详设 §3.4）：画像 + 微光值/积分明细 + 订单摘要。
 */
@Data
public class AdminUserDetailVO {

    private Long id;
    private String openid;
    private String uiMode;
    private Integer motionEnabled;
    private Integer creditScore;
    private Integer violationCount;
    private String status;
    private String lightState;
    private Integer lightPassed;
    private Integer helpedBefore;
    private LocalDateTime createdAt;

    private String nickname;
    private String avatarUrl;
    private Integer age;
    private String signature;
    private List<String> hobbies;
    private String careTip;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String phone;

    /** 微光值余额与最近明细 */
    private LedgerVO credit;
    /** 积分余额与最近明细 */
    private LedgerVO points;

    /** 订单摘要：发布数 / 发布且完成数 / 作为邻居完成数 */
    private Long publishedCount;
    private Long completedCount;
    private Long helpedCompletedCount;
}