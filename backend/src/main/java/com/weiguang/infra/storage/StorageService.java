package com.weiguang.infra.storage;

/**
 * 存储能力（详设 §1.5）：本地磁盘实现为 MVP 唯一实现，OSS 为后置（仅保留 storage.provider 配置位）。
 */
public interface StorageService {

    /**
     * 上传文件并返回可访问的相对 URL（/files/**）。
     *
     * @param content          文件字节
     * @param originalFilename 原始文件名（用于取扩展名）
     */
    String upload(byte[] content, String originalFilename);

    /** 删除已上传文件（传 upload 返回的相对 URL），失败只告警不上抛 */
    void delete(String url);
}
