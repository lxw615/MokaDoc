package com.kanade.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.kanade.backend.dto.chat.ChatQueryRequest;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.kanade.backend.entity.QaMessage;
import com.kanade.backend.mapper.QaMessageMapper;
import com.kanade.backend.service.QaMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话消息表 服务层实现。
 *
 * @author kanade
 */
@Slf4j
@Service
public class QaMessageServiceImpl extends ServiceImpl<QaMessageMapper, QaMessage>  implements QaMessageService {

    @Override
    public Page<QaMessage> listSessionChatByPage(Long sessionId, int pageSize, LocalDateTime lastCreateTime, Long id) {
        ChatQueryRequest chatQueryRequest = new ChatQueryRequest();
        chatQueryRequest.setUserId(id);
        chatQueryRequest.setSessionId(sessionId);
        chatQueryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getChatQueryWrapper(chatQueryRequest);
        return this.page(Page.of(1,pageSize),queryWrapper);
    }

    private QueryWrapper getChatQueryWrapper(ChatQueryRequest chatSessionQueryDTO) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatSessionQueryDTO == null) {
            return queryWrapper;
        }
        Long userId = chatSessionQueryDTO.getUserId();
        Long sessionId = chatSessionQueryDTO.getSessionId();
        LocalDateTime lastCreateTime = chatSessionQueryDTO.getLastCreateTime();
        String sortField = chatSessionQueryDTO.getSortField();
        String sortOrder = chatSessionQueryDTO.getSortOrder();
        // 拼接查询条件
        queryWrapper
//                .eq("user_id", userId)
                .eq("session_id",sessionId)
                .eq("delete_flag", 0);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("create_time", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("create_time", false);
        }
        return queryWrapper;
    }

    @Override
    public List<QaMessage> listRecentMessages(Long sessionId, int limit) {
        // 查询该会话的最近N条消息，按时间升序（保证对话顺序）
        QueryWrapper queryWrapper = QueryWrapper.create()
            .eq("session_id", sessionId)
            .eq("delete_flag", 0)
            .orderBy("create_time asc")
            .limit(limit);
        
        List<QaMessage> messages = this.list(queryWrapper);
        log.debug("📖 [查询历史消息] sessionId={}, limit={}, actualCount={}", 
                  sessionId, limit, messages.size());
        
        return messages;
    }

    @Override
    @Transactional
    public void deleteBySessionId(Long sessionId) {
        // 逻辑删除该会话的所有消息
        QueryWrapper queryWrapper = QueryWrapper.create()
            .eq("session_id", sessionId);
        
        QaMessage updateEntity = new QaMessage();
        updateEntity.setDeleteFlag(1);
        
        boolean success = this.update(updateEntity, queryWrapper);
        log.info("🗑️ [删除会话消息] sessionId={}, success={}", sessionId, success);
    }
}
