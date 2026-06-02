package com.kanade.backend.controller;

import com.kanade.backend.common.BaseResponse;
import com.kanade.backend.common.ResultUtils;
import com.kanade.backend.dto.ChatSessionQueryDTO;
import com.kanade.backend.dto.UserQuestion;
import com.kanade.backend.dto.chat.CreateSessionRequest;
import com.kanade.backend.dto.chat.UpdateSessionRequest;
import com.kanade.backend.entity.QaMessage;
import com.kanade.backend.entity.QaSession;
import com.kanade.backend.entity.QaReference;
import com.kanade.backend.entity.User;
import com.kanade.backend.exception.BusinessException;
import com.kanade.backend.exception.ErrorCode;
import com.kanade.backend.service.QaMessageService;
import com.kanade.backend.service.QaSessionService;
import com.kanade.backend.service.QaReferenceService;
import com.kanade.backend.service.UserService;
import com.kanade.backend.sse.SseEmitterManager;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/chat")
@Tag(name = "聊天管理", description = "提供流式聊天功能")
public class ChatController {

    @Resource
    private QaSessionService qaSessionService;

    @Resource
    private UserService userService;

    @Resource
    private SseEmitterManager sseEmitterManager;
    @Autowired
    private QaMessageService qaMessageService;

    @Resource
    private QaReferenceService qaReferenceService;

    /**
     * 智能问答接口(自动创建会话)
     * 前端直接发送问题,后端自动创建会话并返回sessionId
     * 通过SSE流式返回AI响应
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "智能问答(自动创建会话)")
    public SseEmitter ask(@RequestBody UserQuestion question,
                          HttpServletRequest request) {
        // 1. 获取当前用户
        User currentUser = userService.getLoginUser(request);
        
        // 2. 自动创建会话(如果未指定sessionId)
        Long sessionId = question.getSessionId();
        boolean isNewSession = false;
        if (sessionId == null) {
            // 创建新会话
            sessionId = qaSessionService.createSession(currentUser.getId(), null);
            isNewSession = true;
            log.info("📝 [新会话] sessionId={}, userId={}", sessionId, currentUser.getId());
        } else {
            // 验证会话归属权
            QaSession session = qaSessionService.getById(sessionId);
            if (session == null || !session.getUserId().equals(currentUser.getId())) {
                throw new RuntimeException("无权访问此会话");
            }
            log.debug("🔄 [复用会话] sessionId={}, userId={}", sessionId, currentUser.getId());
        }

        // 3. 创建 SSE 连接(使用 sessionId 作为 key)
        final Long finalSessionId = sessionId; // 声明为final供lambda使用
        SseEmitter emitter = sseEmitterManager.createEmitter(finalSessionId.toString());
        log.debug("🔌 [SSE连接] taskId={}", finalSessionId);

        try {
            // 4. 发送初始消息(包含sessionId, JSON格式)
            String connectedData = qaSessionService.buildConnectedData(finalSessionId);
            emitter.send(SseEmitter.event()
                .name("message")
                .data(connectedData));

            // 5. 调用服务层处理消息(返回Flux<String>,已经是JSON格式)
            log.debug("💬 [发送消息] sessionId={}, messageLength={}", finalSessionId, question.getContent().length());
            
            qaSessionService.sendMessage(finalSessionId, question.getContent(), question.getDocumentIds())
                .doOnNext(jsonData -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(jsonData));
                    } catch (Exception e) {
                        log.warn("发送 SSE 消息失败: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    emitter.complete();
                    log.debug("✅ [响应完成] sessionId={}", finalSessionId);
                })
                .doOnError(error -> {
                    log.error("❌ [响应失败] sessionId={}, error={}", finalSessionId, error.getMessage());
                    emitter.complete();
                })
                .subscribe();

        } catch (IOException e) {
            log.error("初始化 SSE 连接失败, sessionId={}", finalSessionId, e);
            sseEmitterManager.complete(finalSessionId.toString());
        }

        return emitter;
    }

    /**
     * 获取用户的会话列表
     */
    @GetMapping("/session/list")
    @Operation(summary = "获取会话列表")
    public BaseResponse<Page<QaSession>> listSessions(@RequestParam(defaultValue = "5") int pageSize,
                                                                @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                                HttpServletRequest request) {

        User currentUser = userService.getLoginUser(request);
        Page<QaSession> sessions = qaSessionService.listAppChatHistoryByPage(currentUser.getId(), pageSize, lastCreateTime, request);
        return ResultUtils.success(sessions);
    }


    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "删除会话")
    public BaseResponse<Boolean> deleteSession(@PathVariable Long sessionId,
                                                HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        qaSessionService.deleteSession(sessionId, currentUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 创建会话
     */
    @PostMapping("/session")
    @Operation(summary = "创建会话")
    public BaseResponse<QaSession> createSession(@RequestBody CreateSessionRequest request,
                                                  HttpServletRequest httpRequest) {
        User currentUser = userService.getLoginUser(httpRequest);
        QaSession session = qaSessionService.createSessionWithDetails(
            currentUser.getId(), request.getSessionName(), request.getSummary());
        return ResultUtils.success(session);
    }

    /**
     * 更新会话
     */
    @PutMapping("/session/{sessionId}")
    @Operation(summary = "更新会话")
    public BaseResponse<QaSession> updateSession(@PathVariable Long sessionId,
                                                  @RequestBody UpdateSessionRequest request,
                                                  HttpServletRequest httpRequest) {
        User currentUser = userService.getLoginUser(httpRequest);
        QaSession session = qaSessionService.updateSessionInfo(
            sessionId, currentUser.getId(), request.getSessionName(), request.getSummary());
        return ResultUtils.success(session);
    }

    //获取当前会话的聊天记录
    @GetMapping("chat/list/{sessionId}")
    public BaseResponse<Page<QaMessage>> listChat(@PathVariable Long sessionId,@RequestParam(defaultValue = "5") int pageSize,
                                                  @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                  HttpServletRequest request){
        User currentUser = userService.getLoginUser(request);
        QaSession session = qaSessionService.getById(sessionId);
        if (session == null || Integer.valueOf(1).equals(session.getDeleteFlag())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        if (!session.getUserId().equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看此会话");
        }
        Page<QaMessage> sessions = qaMessageService.listSessionChatByPage(sessionId, pageSize, lastCreateTime, currentUser.getId());
        return ResultUtils.success(sessions);
    }
    /**
     * 查询某条 AI 消息的引用溯源。
     */
    @GetMapping("/message/{messageId}/references")
    @Operation(summary = "获取回答引用溯源")
    public BaseResponse<List<QaReference>> listReferences(@PathVariable Long messageId,
                                                           HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        return ResultUtils.success(qaReferenceService.listByMessageId(messageId, currentUser.getId()));
    }

}
