package com.kanade.backend.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户资料更新请求。
 */
@Data
public class UserProfileUpdateRequest implements Serializable {

    private String username;

    private String email;

    private String nickname;
}
