package com.kanade.backend.service;

import com.mybatisflex.core.service.IService;
import com.kanade.backend.dto.user.LoginVO;
import com.kanade.backend.dto.user.OperationLogVO;
import com.kanade.backend.dto.user.PasswordUpdateRequest;
import com.kanade.backend.dto.user.StorageSummaryVO;
import com.kanade.backend.dto.user.UserLoginRequest;
import com.kanade.backend.dto.user.UserProfileUpdateRequest;
import com.kanade.backend.dto.user.UserQueryRequest;
import com.kanade.backend.dto.user.UserRegisterRequest;
import com.kanade.backend.dto.user.UserVO;
import com.kanade.backend.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户表 服务层。
 *
 * @author kanade
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param registerRequest 注册请求
     * @return 用户ID
     */
    Long userRegister(UserRegisterRequest registerRequest);

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求
     * @return 登录信息（包含用户信息和token）
     */
    LoginVO userLogin(UserLoginRequest loginRequest);

    /**
     * 获取用户信息（脱敏）
     *
     * @param queryRequest 查询请求
     * @return 用户信息
     */
    UserVO getUserVO(UserQueryRequest queryRequest);

    /**
     * 获取当前登录用户信息（脱敏）
     *
     * @return 用户信息
     */
    UserVO getCurrentUserVO();

    /**
     * 更新当前登录用户资料。
     */
    UserVO updateCurrentUserProfile(UserProfileUpdateRequest request, HttpServletRequest httpRequest);

    /**
     * 修改当前登录用户密码。
     */
    boolean updateCurrentUserPassword(PasswordUpdateRequest request, HttpServletRequest httpRequest);

    /**
     * 获取当前用户操作日志摘要。
     */
    List<OperationLogVO> listCurrentUserOperationLogs(HttpServletRequest request);

    /**
     * 获取当前用户文档存储摘要。
     */
    StorageSummaryVO getCurrentUserStorageSummary(HttpServletRequest request);

    /**
     * 获取当前登录用户实体
     *
     * @param request HTTP请求
     * @return 当前用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取脱敏后的登录用户信息
     *
     * @param user 用户实体
     * @return 登录用户VO
     */
    LoginVO getLoginUserVO(User user);

    /**
     * 用户注销
     */
    void userLogout();
}
