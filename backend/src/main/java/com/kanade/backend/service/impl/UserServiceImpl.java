package com.kanade.backend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.kanade.backend.dto.user.LoginVO;
import com.kanade.backend.dto.user.OperationLogVO;
import com.kanade.backend.dto.user.PasswordUpdateRequest;
import com.kanade.backend.dto.user.StorageSummaryVO;
import com.kanade.backend.dto.user.UserLoginRequest;
import com.kanade.backend.dto.user.UserProfileUpdateRequest;
import com.kanade.backend.dto.user.UserQueryRequest;
import com.kanade.backend.dto.user.UserRegisterRequest;
import com.kanade.backend.dto.user.UserVO;
import com.kanade.backend.entity.Document;
import com.kanade.backend.entity.GraphTask;
import com.kanade.backend.entity.QaSession;
import com.kanade.backend.entity.User;
import com.kanade.backend.exception.BusinessException;
import com.kanade.backend.exception.ErrorCode;
import com.kanade.backend.mapper.GraphTaskMapper;
import com.kanade.backend.mapper.UserMapper;
import com.kanade.backend.service.DocumentService;
import com.kanade.backend.service.QaSessionService;
import com.kanade.backend.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 用户表 服务层实现。
 *
 * @author kanade
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，用于密码加密混淆
     */
    private static final String SALT = "kanade_mokadoc";
    private static final long DEFAULT_STORAGE_LIMIT_BYTES = 1024L * 1024L * 1024L;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Resource
    private DocumentService documentService;

    @Resource
    private QaSessionService qaSessionService;

    @Resource
    private GraphTaskMapper graphTaskMapper;

    @Override
    public Long userRegister(UserRegisterRequest registerRequest) {
        // 1. 校验参数
        String username = registerRequest.getUsername();
        String email = registerRequest.getEmail();
        String password = registerRequest.getPassword();
        String nickname = registerRequest.getNickname();

        if (StrUtil.hasBlank(username, email, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名、邮箱和密码不能为空");
        }
        if (username.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名长度过短（至少4位）");
        }
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短（至少6位）");
        }

        // 2. 查询用户是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("username", username);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已存在");
        }

        // 检查邮箱是否已存在
        queryWrapper = new QueryWrapper();
        queryWrapper.eq("email", email);
        count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱已被注册");
        }

        // 3. 加密密码
        String encryptPassword = getEncryptPassword(password);

        // 4. 创建用户，插入数据库
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encryptPassword);
        user.setNickname(StrUtil.isNotBlank(nickname) ? nickname : username);
        user.setAvatar("");
        //user.setUserRole("user"); // 默认角色为普通用户
        user.setStatus(1);
        user.setRegisterTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleteFlag(0);

        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }

        log.info("用户注册成功: username={}, email={}", username, email);
        return user.getId();
    }

    @Override
    public LoginVO userLogin(UserLoginRequest loginRequest) {
        // 1. 校验参数
        String account = loginRequest.getAccount();
        String password = loginRequest.getPassword();

        if (StrUtil.hasBlank(account, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号和密码不能为空");
        }

        // 2. 加密密码
        String encryptPassword = getEncryptPassword(password);
        System.out.println(encryptPassword);
        // 3. 查询用户是否存在（支持用户名或邮箱登录）
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.where("(username = ? OR email = ?) AND password = ?", account, account, encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        System.out.println(user);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // 4. 检查用户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号已被禁用");
        }

        // 5. 记录用户的登录态
        HttpServletRequest request = getRequest();
        request.getSession().setAttribute("USER_LOGIN_STATE", user);
        log.info("用户登录成功: userId={}, account={}", user.getId(), account);

        // 6. 返回脱敏的用户信息
        UserVO userVO = getUserVO(user);
        return LoginVO.builder()
                .user(userVO)
                .token(request.getSession().getId())
                .build();
    }

    @Override
    public UserVO getUserVO(UserQueryRequest queryRequest) {
        if (queryRequest == null || queryRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        User user = this.getById(queryRequest.getId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return getUserVO(user);
    }

    @Override
    public UserVO getCurrentUserVO() {
        HttpServletRequest request = getRequest();
        User currentUser = getLoginUser(request);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        return getUserVO(currentUser);
    }

    @Override
    public UserVO updateCurrentUserProfile(UserProfileUpdateRequest updateRequest, HttpServletRequest httpRequest) {
        if (updateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        User currentUser = getLoginUser(httpRequest);
        String username = StrUtil.trim(updateRequest.getUsername());
        String email = StrUtil.trim(updateRequest.getEmail());
        String nickname = StrUtil.trim(updateRequest.getNickname());

        if (StrUtil.hasBlank(username, email)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名和邮箱不能为空");
        }
        if (username.length() < 4 || username.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名长度需为4-50位");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        }
        if (StrUtil.isNotBlank(nickname) && nickname.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称不能超过50位");
        }

        if (existsOtherUser("username", username, currentUser.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已存在");
        }
        if (existsOtherUser("email", email, currentUser.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱已被使用");
        }

        currentUser.setUsername(username);
        currentUser.setEmail(email);
        currentUser.setNickname(StrUtil.isNotBlank(nickname) ? nickname : username);
        currentUser.setUpdateTime(LocalDateTime.now());

        boolean updated = this.updateById(currentUser);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存用户信息失败");
        }

        httpRequest.getSession().setAttribute("USER_LOGIN_STATE", currentUser);
        log.info("用户资料更新成功: userId={}, username={}, email={}", currentUser.getId(), username, email);
        return getUserVO(currentUser);
    }

    @Override
    public boolean updateCurrentUserPassword(PasswordUpdateRequest updateRequest, HttpServletRequest httpRequest) {
        if (updateRequest == null || StrUtil.hasBlank(
                updateRequest.getCurrentPassword(),
                updateRequest.getNewPassword(),
                updateRequest.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码参数不能为空");
        }
        if (!Objects.equals(updateRequest.getNewPassword(), updateRequest.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的新密码不一致");
        }
        if (updateRequest.getNewPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码长度不能少于6位");
        }

        User currentUser = getLoginUser(httpRequest);
        String currentEncrypted = getEncryptPassword(updateRequest.getCurrentPassword());
        if (!Objects.equals(currentEncrypted, currentUser.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前密码不正确");
        }

        currentUser.setPassword(getEncryptPassword(updateRequest.getNewPassword()));
        currentUser.setUpdateTime(LocalDateTime.now());
        boolean updated = this.updateById(currentUser);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "密码修改失败");
        }
        httpRequest.getSession().setAttribute("USER_LOGIN_STATE", currentUser);
        log.info("用户密码修改成功: userId={}", currentUser.getId());
        return true;
    }

    @Override
    public List<OperationLogVO> listCurrentUserOperationLogs(HttpServletRequest request) {
        User currentUser = getLoginUser(request);
        Long userId = currentUser.getId();
        AtomicLong idGenerator = new AtomicLong(1);
        List<OperationLogVO> logs = new ArrayList<>();

        addLog(logs, idGenerator, currentUser.getRegisterTime(), "注册账号：" + currentUser.getUsername(), "用户");
        addLog(logs, idGenerator, currentUser.getUpdateTime(), "更新个人资料", "用户");

        QueryWrapper docQuery = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("delete_flag", 0)
                .orderBy("upload_time desc")
                .limit(10);
        List<Document> documents = documentService.list(docQuery);
        for (Document doc : documents) {
            addLog(logs, idGenerator, doc.getUploadTime(), "上传文档：" + doc.getName(), "文档");
            addLog(logs, idGenerator, doc.getUpdateTime(), "更新文档信息：" + doc.getName(), "文档");
        }

        QueryWrapper sessionQuery = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("delete_flag", 0)
                .orderBy("create_time desc")
                .limit(10);
        List<QaSession> sessions = qaSessionService.list(sessionQuery);
        for (QaSession session : sessions) {
            addLog(logs, idGenerator, session.getCreateTime(), "创建问答会话：" + session.getSessionName(), "问答");
            addLog(logs, idGenerator, session.getUpdateTime(), "更新问答会话：" + session.getSessionName(), "问答");
        }

        QueryWrapper graphTaskQuery = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("delete_flag", 0)
                .orderBy("create_time desc")
                .limit(10);
        List<GraphTask> graphTasks = graphTaskMapper.selectListByQuery(graphTaskQuery);
        for (GraphTask task : graphTasks) {
            addLog(logs, idGenerator, task.getCreateTime(), "启动图谱构建任务 #" + task.getId(), "图谱");
            addLog(logs, idGenerator, task.getUpdateTime(), "图谱构建状态：" + graphStatusText(task.getStatus()), "图谱");
        }

        return logs.stream()
                .filter(log -> log.getTime() != null)
                .sorted(Comparator.comparing(OperationLogVO::getTime).reversed())
                .limit(30)
                .toList();
    }

    @Override
    public StorageSummaryVO getCurrentUserStorageSummary(HttpServletRequest request) {
        User currentUser = getLoginUser(request);
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("user_id", currentUser.getId())
                .eq("delete_flag", 0);
        List<Document> documents = documentService.list(queryWrapper);

        long usedBytes = documents.stream()
                .map(Document::getFileSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        int documentCount = documents.size();
        long averageBytes = documentCount == 0 ? 0 : usedBytes / documentCount;
        int usagePercent = (int) Math.min(100, Math.round(usedBytes * 100.0 / DEFAULT_STORAGE_LIMIT_BYTES));

        return StorageSummaryVO.builder()
                .usedBytes(usedBytes)
                .totalBytes(DEFAULT_STORAGE_LIMIT_BYTES)
                .usagePercent(usagePercent)
                .documentCount(documentCount)
                .averageBytes(averageBytes)
                .build();
    }

    @Override
    public void userLogout() {
        HttpServletRequest request = getRequest();
        Object userObj = request.getSession().getAttribute("USER_LOGIN_STATE");
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        request.getSession().removeAttribute("USER_LOGIN_STATE");
        log.info("用户注销成功");
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        try {
            // 先判断用户是否登录
            Object userObj = request.getSession().getAttribute("USER_LOGIN_STATE");
            User currentUser = (User) userObj;
            if (currentUser == null || currentUser.getId() == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            // 从数据库查询当前用户信息（保证数据最新）
            long userId = currentUser.getId();
            currentUser = this.getById(userId);
            if (currentUser == null
                    || Integer.valueOf(1).equals(currentUser.getDeleteFlag())
                    || Integer.valueOf(0).equals(currentUser.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return currentUser;
        } catch (Exception e) {
            log.warn("获取当前用户失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }

    @Override
    public LoginVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginVO loginUserVO = new LoginVO();
        UserVO userVO = getUserVO(user);
        loginUserVO.setUser(userVO);
        return loginUserVO;
    }

    private boolean existsOtherUser(String field, String value, Long currentUserId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(field + " = ? AND id <> ?", value, currentUserId);
        return this.mapper.selectCountByQuery(queryWrapper) > 0;
    }

    private void addLog(List<OperationLogVO> logs,
                        AtomicLong idGenerator,
                        LocalDateTime time,
                        String action,
                        String source) {
        if (time == null || StrUtil.isBlank(action)) {
            return;
        }
        logs.add(OperationLogVO.builder()
                .id(idGenerator.getAndIncrement())
                .time(time)
                .action(action)
                .source(source)
                .build());
    }

    private String graphStatusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "PENDING" -> "待处理";
            case "PROCESSING" -> "处理中";
            case "COMPLETED" -> "已完成";
            case "FAILED" -> "失败";
            default -> status;
        };
    }

    /**
     * 获取HttpServletRequest
     *
     * @return HttpServletRequest
     */
    private HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getRequest();
    }

    /**
     * 获取脱敏后的用户信息
     *
     * @param user 用户实体
     * @return 用户VO
     */
    private UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 加密密码
     *
     * @param password 原始密码
     * @return 加密后的密码
     */
    private String getEncryptPassword(String password) {
        return DigestUtils.md5DigestAsHex((password + SALT).getBytes(StandardCharsets.UTF_8));
    }
}
