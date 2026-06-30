package com.km.gateway.dto;

/**
 * super-biz-agent /api/auth/me 响应模型。
 *
 * <p>必改 1：响应字段是 <code>id</code>，<b>不是</b> <code>userId</code>。
 * Gateway 过滤器读 response.id 注入 X-User-Id。
 */
public class AuthUserResponse {

    private String id;
    private String username;
    private String displayName;
    private String status;

    public AuthUserResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
