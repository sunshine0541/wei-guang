package com.weiguang.infra.storage;

import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.Result;
import com.weiguang.infra.storage.dto.UploadVO;
import com.weiguang.modules.system.service.ConfigService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * P1 图片上传（详设 §3.3）：白名单 jpg/jpeg/png/webp；大小 ≤ storage.image_max_size_mb MB。
 * 鉴权 U（登录即可，头像与聊天共用）；文件写入在事务外。
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final StorageService storageService;
    private final ConfigService configService;

    public UploadController(StorageService storageService, ConfigService configService) {
        this.storageService = storageService;
        this.configService = configService;
    }

    @PostMapping("/image")
    public Result<UploadVO> image(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        long maxBytes = configService.getInt("storage.image_max_size_mb", 5) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        byte[] content = file.getBytes();
        if (!matchesMagic(content, extension)) {
            // 伪造扩展名的可执行文件/脚本在此拦截（v1.3 T28）
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return Result.ok(new UploadVO(storageService.upload(content, file.getOriginalFilename())));
    }

    /** 文件头魔数校验：扩展名与实际内容必须一致 */
    private boolean matchesMagic(byte[] content, String extension) {
        if (content == null || content.length < 12) {
            return false;
        }
        return switch (extension) {
            case "jpg", "jpeg" -> (content[0] & 0xFF) == 0xFF
                    && (content[1] & 0xFF) == 0xD8
                    && (content[2] & 0xFF) == 0xFF;
            case "png" -> (content[0] & 0xFF) == 0x89
                    && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47
                    && content[4] == 0x0D && content[5] == 0x0A && content[6] == 0x1A && content[7] == 0x0A;
            case "webp" -> content[0] == 0x52 && content[1] == 0x49
                    && content[2] == 0x46 && content[3] == 0x46
                    && content[8] == 0x57 && content[9] == 0x45
                    && content[10] == 0x42 && content[11] == 0x50;
            default -> false;
        };
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
