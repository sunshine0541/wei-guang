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
        return Result.ok(new UploadVO(storageService.upload(file.getBytes(), file.getOriginalFilename())));
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
