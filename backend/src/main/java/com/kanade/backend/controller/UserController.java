package com.kanade.backend.controller;

import com.kanade.backend.common.BaseResponse;
import com.kanade.backend.common.ResultUtils;
import com.kanade.backend.dto.user.LoginVO;
import com.kanade.backend.dto.user.OperationLogVO;
import com.kanade.backend.dto.user.PasswordUpdateRequest;
import com.kanade.backend.dto.user.StorageSummaryVO;
import com.kanade.backend.dto.user.UserLoginRequest;
import com.kanade.backend.dto.user.UserProfileUpdateRequest;
import com.kanade.backend.dto.user.UserQueryRequest;
import com.kanade.backend.dto.user.UserRegisterRequest;
import com.kanade.backend.dto.user.UserVO;
import com.kanade.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "提供用户注册、登录、查询等功能")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     *
     * @param registerRequest 注册请求
     * @return 用户ID
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册账号")
    public BaseResponse<Long> userRegister(@Valid @RequestBody UserRegisterRequest registerRequest) {
        Long userId = userService.userRegister(registerRequest);
        return ResultUtils.success(userId);
    }

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求
     * @return 登录信息（包含用户信息和token）
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名/邮箱和密码登录")
    public BaseResponse<LoginVO> userLogin(@Valid @RequestBody UserLoginRequest loginRequest) {
        LoginVO loginVO = userService.userLogin(loginRequest);
        return ResultUtils.success(loginVO);
    }

    /**
     * 用户注销
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    @Operation(summary = "用户注销", description = "退出当前登录会话")
    public BaseResponse<Boolean> userLogout() {
        userService.userLogout();
        return ResultUtils.success(true);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户信息
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public BaseResponse<UserVO> getCurrentUser() {
        UserVO userVO = userService.getCurrentUserVO();
        return ResultUtils.success(userVO);
    }

    /**
     * 更新当前登录用户资料。
     */
    @PutMapping("/profile")
    @Operation(summary = "更新当前用户资料", description = "更新当前登录用户的用户名、邮箱和昵称")
    public BaseResponse<UserVO> updateProfile(@RequestBody UserProfileUpdateRequest updateRequest,
                                              HttpServletRequest request) {
        return ResultUtils.success(userService.updateCurrentUserProfile(updateRequest, request));
    }

    /**
     * 修改当前登录用户密码。
     */
    @PutMapping("/password")
    @Operation(summary = "修改当前用户密码", description = "校验当前密码后修改登录密码")
    public BaseResponse<Boolean> updatePassword(@RequestBody PasswordUpdateRequest updateRequest,
                                                HttpServletRequest request) {
        return ResultUtils.success(userService.updateCurrentUserPassword(updateRequest, request));
    }

    /**
     * 获取当前用户操作日志摘要。
     */
    @GetMapping("/logs")
    @Operation(summary = "获取当前用户操作日志", description = "根据真实业务数据生成操作日志摘要")
    public BaseResponse<List<OperationLogVO>> listOperationLogs(HttpServletRequest request) {
        return ResultUtils.success(userService.listCurrentUserOperationLogs(request));
    }

    /**
     * 获取当前用户存储摘要。
     */
    @GetMapping("/storage")
    @Operation(summary = "获取当前用户存储摘要", description = "统计当前用户文档数量和存储占用")
    public BaseResponse<StorageSummaryVO> getStorageSummary(HttpServletRequest request) {
        return ResultUtils.success(userService.getCurrentUserStorageSummary(request));
    }

    /**
     * 根据ID获取用户信息
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取用户信息", description = "根据用户ID获取用户详细信息")
    public BaseResponse<UserVO> getUserById(@PathVariable Long id) {
        UserQueryRequest queryRequest = new UserQueryRequest();
        queryRequest.setId(id);
        UserVO userVO = userService.getUserVO(queryRequest);
        return ResultUtils.success(userVO);
    }
}
