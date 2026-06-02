package com.kanade.backend.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户密码修改请求。
 */
@Data
public class PasswordUpdateRequest implements Serializable {

    private String currentPassword;

    private String newPassword;

    private String confirmPassword;
}
