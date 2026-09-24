package com.weiguang.infra.storage;

import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.system.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 本地磁盘存储实现（详设 §1.5）：按 storage.local_path 落盘，按日期分目录、UUID 文件名防覆盖。
 */
@Slf4j
@Service
public class LocalStorageServiceImpl implements StorageService {

    /** 静态映射前缀（WebConfig 中 /files/** → storage.local_path） */
    public static final String URL_PREFIX = "/files/";

    private final ConfigService configService;

    public LocalStorageServiceImpl(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String upload(byte[] content, String originalFilename) {
        LocalDate today = LocalDate.now();
        String relativeDir = today.getYear() + "/" + String.format("%02d", today.getMonthValue());
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extensionOf(originalFilename);
        Path directory = Paths.get(rootPath()).resolve(relativeDir);
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(filename), content);
        } catch (IOException e) {
            log.error("本地存储写入失败 dir={}", directory, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        return URL_PREFIX + relativeDir + "/" + filename;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(rootPath()).resolve(url.substring(URL_PREFIX.length())));
        } catch (IOException e) {
            log.warn("本地存储删除失败 url={}：{}", url, e.getMessage());
        }
    }

    /** 本地存储根目录（sys_config storage.local_path） */
    public String rootPath() {
        return configService.get("storage.local_path", "./uploads");
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int index = originalFilename.lastIndexOf('.');
        return index < 0 ? "" : originalFilename.substring(index + 1).toLowerCase();
    }
}
