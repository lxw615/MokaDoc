package com.kanade.backend.service.impl;

import cn.hutool.core.bean.BeanUtil;

import com.kanade.backend.ai.rag.DocumentRagService;
import com.kanade.backend.dto.document.DocumentUpdateRequest;
import com.kanade.backend.dto.document.DocumentVO;
import com.kanade.backend.entity.Document;
import com.kanade.backend.exception.BusinessException;
import com.kanade.backend.exception.ErrorCode;
import com.kanade.backend.mapper.DocumentMapper;
import com.kanade.backend.service.DocumentService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档表 服务层实现。
 *
 * @author kanade
 */
@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Value("${file.upload.dir:./uploads/documents}")
    private String uploadDir;

    @Resource
    private DocumentRagService documentRagService;

    @Override
    public Document getByMd5(Long userId, String md5) {
        QueryWrapper qw = new QueryWrapper();
        qw.eq("user_id", userId);
        qw.eq("file_md5", md5);
        qw.eq("delete_flag", 0);
        return this.mapper.selectOneByQuery(qw);
    }

    @Override
    public DocumentVO upload(MultipartFile file, Long userId) {
        log.info("📝 [文档上传] 开始处理文件 - userId={}", userId);
        
        // 1. 校验文件
        if (file.isEmpty()) {
            log.warn("⚠️ [文档上传] 文件为空 - userId={}", userId);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            log.warn("⚠️ [文档上传] 文件名为空 - userId={}", userId);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        
        long fileSize = file.getSize();
        log.debug("📄 [文档上传] 文件信息 - name={}, size={} bytes", originalName, fileSize);

        // 2. 计算MD5 todo 去掉
        String md5;
        try {
            log.debug("🔍 [文档上传] 正在计算文件MD5...");
            md5 = DigestUtils.md5DigestAsHex(file.getBytes());
            log.debug("✅ [文档上传] MD5计算完成: {}", md5);
        } catch (IOException e) {
            log.error("❌ [文档上传] 计算文件MD5失败 - userId={}, fileName={}", userId, originalName, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件读取失败");
        }

        // 3. 查重
        log.debug("🔎 [文档上传] 检查文件是否已存在 - userId={}, md5={}", userId, md5);
        Document existing = getByMd5(userId, md5);
        if (existing != null) {
            log.warn("⚠️ [文档上传] 文件已存在 - userId={}, docId={}, md5={}", userId, existing.getId(), md5);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该文件已上传");
        }

        // 4. 提取文件扩展名和类型
        String fileType = getFileExtension(originalName);
        log.debug("📋 [文档上传] 文件类型: {}", fileType);

        // 5. 先插入记录获取ID
        Document doc = Document.builder()
                .userId(userId)
                .name(originalName)
                .fileType(fileType)
                .fileSize(fileSize)
                .filePath("")  // 暂不填写，等文件保存完更新
                .fileMd5(md5)
                .uploadTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .deleteFlag(0)
                .build();
        this.save(doc);
        Long docId = doc.getId();
        log.info("💾 [文档上传] 文档记录已创建 - docId={}, name={}", docId, originalName);

        // 6. 创建目录并保存文件: {uploadDir}/{userId}/{docId}/originalName
        Path dir = Paths.get(uploadDir, String.valueOf(userId), String.valueOf(docId));
        try {
            log.debug("📁 [文档上传] 创建目录: {}", dir.toAbsolutePath());
            Files.createDirectories(dir);
            
            Path filePath = dir.resolve(originalName);
            log.debug("💿 [文档上传] 正在保存文件到: {}", filePath.toAbsolutePath());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            String relativePath = userId + "/" + docId + "/" + originalName;
            doc.setFilePath(relativePath);
            this.updateById(doc);
            
            log.info("✅ [文档上传] 文件保存成功 - docId={}, path={}, size={} bytes", docId, filePath.toAbsolutePath(), fileSize);
        } catch (IOException e) {
            log.error("❌ [文档上传] 保存文件失败 - docId={}, userId={}", docId, userId, e);
            // 回滚数据库记录
            log.debug("🔄 [文档上传] 回滚数据库记录 - docId={}", docId);
            this.removeById(docId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件保存失败: " + e.getMessage());
        }

        log.info("✅ [文档上传] 文档上传流程完成 - docId={}, userId={}", docId, userId);
        return toVO(doc);
    }

    @Override
    public DocumentVO getDocumentVO(Long id, Long userId) {
        log.debug("🔍 [文档查询] 查询文档 - docId={}, userId={}", id, userId);
        
        Document doc = this.getById(id);
        if (doc == null || doc.getDeleteFlag() == 1) {
            log.warn("⚠️ [文档查询] 文档不存在 - docId={}", id);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        if (!doc.getUserId().equals(userId)) {
            log.warn("⚠️ [文档查询] 无权访问 - docId={}, requestUserId={}, ownerUserId={}", 
                    id, userId, doc.getUserId());
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该文档");
        }
        
        log.debug("✅ [文档查询] 查询成功 - docId={}, name={}", id, doc.getName());
        return toVO(doc);
    }

    @Override
    public List<DocumentVO> listByUser(Long userId) {
        log.debug("📋 [文档列表] 获取用户文档列表 - userId={}", userId);
        
        QueryWrapper qw = new QueryWrapper();
        qw.eq("user_id", userId);
        qw.eq("delete_flag", 0);
        qw.orderBy("upload_time", false);  // 按上传时间降序
        List<Document> docs = this.mapper.selectListByQuery(qw);
        
        log.info("✅ [文档列表] 查询完成 - userId={}, count={}", userId, docs.size());
        return docs.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public DocumentVO update(DocumentUpdateRequest req, Long userId) {
        log.info("✏️ [文档更新] 开始更新文档 - docId={}, userId={}", req.getId(), userId);
        
        Document doc = this.getById(req.getId());
        if (doc == null || doc.getDeleteFlag() == 1) {
            log.warn("⚠️ [文档更新] 文档不存在 - docId={}", req.getId());
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        if (!doc.getUserId().equals(userId)) {
            log.warn("⚠️ [文档更新] 无权操作 - docId={}, requestUserId={}, ownerUserId={}", 
                    req.getId(), userId, doc.getUserId());
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该文档");
        }

        boolean hasChanges = false;
        if (req.getName() != null && !req.getName().isBlank()) {
            log.debug("📝 [文档更新] 更新名称: {} -> {}", doc.getName(), req.getName());
            doc.setName(req.getName());
            hasChanges = true;
        }
        if (req.getDescription() != null) {
            log.debug("📝 [文档更新] 更新描述: {} -> {}", doc.getDescription(), req.getDescription());
            doc.setDescription(req.getDescription());
            hasChanges = true;
        }
        
        if (hasChanges) {
            doc.setUpdateTime(LocalDateTime.now());
            this.updateById(doc);
            log.info("✅ [文档更新] 更新成功 - docId={}", req.getId());
        } else {
            log.debug("ℹ️ [文档更新] 无变更内容 - docId={}", req.getId());
        }
        
        return toVO(doc);
    }

    @Override
    public boolean delete(Long id, Long userId) {
        log.info("🗑️ [文档删除] 开始删除文档 - docId={}, userId={}", id, userId);
        
        Document doc = this.getById(id);
        if (doc == null || doc.getDeleteFlag() == 1) {
            log.warn("⚠️ [文档删除] 文档不存在 - docId={}", id);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        if (!doc.getUserId().equals(userId)) {
            log.warn("⚠️ [文档删除] 无权操作 - docId={}, requestUserId={}, ownerUserId={}", 
                    id, userId, doc.getUserId());
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该文档");
        }
        
        doc.setDeleteFlag(1);
        doc.setUpdateTime(LocalDateTime.now());
        boolean result = this.updateById(doc);

        if (result) {
            documentRagService.removeDocumentEmbeddings(id);
            log.info("✅ [文档删除] 删除成功 - docId={}, name={}", id, doc.getName());
        } else {
            log.error("❌ [文档删除] 删除失败 - docId={}", id);
        }
        
        return result;
    }

    // ---- 私有辅助方法 ----

    private DocumentVO toVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        BeanUtil.copyProperties(doc, vo);
        return vo;
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}
