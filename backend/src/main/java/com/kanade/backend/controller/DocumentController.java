package com.kanade.backend.controller;

import com.kanade.backend.common.BaseResponse;
import com.kanade.backend.common.ResultUtils;
import com.kanade.backend.dto.document.DocumentUpdateRequest;
import com.kanade.backend.dto.document.DocumentVO;
import com.kanade.backend.entity.Document;
import com.kanade.backend.entity.User;
import com.kanade.backend.exception.BusinessException;
import com.kanade.backend.exception.ErrorCode;
import com.kanade.backend.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/document")
@Tag(name = "文档管理", description = "提供文档上传、查询、更新、删除等功能")
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @Resource
    private HttpServletRequest request;

    @Value("${file.upload.dir:./uploads/documents}")
    private String uploadDir;

    /**
     * 上传文档
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档", description = "上传文件，自动MD5去重")
    public BaseResponse<DocumentVO> upload(@RequestParam("file") MultipartFile file) {
        log.info("📤 [文档上传] 开始处理文件上传请求");
        
        Long userId = getLoginUserId();
        log.debug("👤 [文档上传] 用户ID: {}", userId);
        
        DocumentVO vo = documentService.upload(file, userId);
        
        log.info("✅ [文档上传] 文件上传成功 - docId={}, fileName={}", vo.getId(), vo.getName());
        return ResultUtils.success(vo);
    }

    /**
     * 检查MD5是否已存在（前端预查）
     */
    @GetMapping("/check-md5")
    @Operation(summary = "MD5查重", description = "检查当前用户是否已上传过相同MD5的文件")
    public BaseResponse<Boolean> checkMd5(@RequestParam String md5) {
        Long userId = getLoginUserId();
        boolean exists = documentService.getByMd5(userId, md5) != null;
        return ResultUtils.success(exists);
    }

    /**
     * 获取单个文档
     */
    @GetMapping("/{id:\\d+}")
    @Operation(summary = "获取文档详情", description = "根据ID获取文档信息")
    public BaseResponse<DocumentVO> getById(@PathVariable Long id) {
        Long userId = getLoginUserId();
        DocumentVO vo = documentService.getDocumentVO(id, userId);
        return ResultUtils.success(vo);
    }

    @GetMapping("/{id:\\d+}/preview")
    @Operation(summary = "预览文档", description = "返回当前用户文档的原始文件流")
    public ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable Long id) {
        return buildFileResponse(id, true);
    }

    @GetMapping("/{id:\\d+}/download")
    @Operation(summary = "下载文档", description = "下载当前用户文档")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
        return buildFileResponse(id, false);
    }

    /**
     * 获取当前用户所有文档
     */
    @GetMapping("/list")
    @Operation(summary = "文档列表", description = "获取当前用户的所有文档")
    public BaseResponse<List<DocumentVO>> list() {
        Long userId = getLoginUserId();
        List<DocumentVO> list = documentService.listByUser(userId);
        return ResultUtils.success(list);
    }

    /**
     * 更新文档信息
     */
    @PutMapping("/{id:\\d+}")
    @Operation(summary = "更新文档", description = "更新文档名称和描述")
    public BaseResponse<DocumentVO> update(@PathVariable Long id,
                                           @RequestBody DocumentUpdateRequest updateRequest) {
        Long userId = getLoginUserId();
        updateRequest.setId(id);
        DocumentVO vo = documentService.update(updateRequest, userId);
        return ResultUtils.success(vo);
    }

    /**
     * 删除文档（逻辑删除）
     */
    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "删除文档", description = "逻辑删除文档")
    public BaseResponse<Boolean> delete(@PathVariable Long id) {
        Long userId = getLoginUserId();
        documentService.delete(id, userId);
        return ResultUtils.success(true);
    }

    /**
     * 从Session获取当前登录用户ID
     */
    private Long getLoginUserId() {
        Object userObj = request.getSession().getAttribute("USER_LOGIN_STATE");
        if (userObj instanceof User currentUser) {
            return currentUser.getId();
        }
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }

    private ResponseEntity<org.springframework.core.io.Resource> buildFileResponse(Long id, boolean inline) {
        Long userId = getLoginUserId();
        Document doc = documentService.getById(id);
        if (doc == null || doc.getDeleteFlag() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        if (!doc.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该文档");
        }
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档文件不存在");
        }

        try {
            Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(doc.getFilePath()).normalize();
            if (!filePath.startsWith(baseDir) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档文件不存在");
            }

            UrlResource resource = new UrlResource(filePath.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(doc.getName(), StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档文件读取失败: docId={}, userId={}", id, userId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档文件读取失败");
        }
    }
}
