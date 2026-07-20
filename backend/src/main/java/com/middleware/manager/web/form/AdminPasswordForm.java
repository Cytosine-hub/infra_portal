package com.middleware.manager.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdminPasswordForm {

    @NotBlank(message = "请输入当前密码")
    private String currentPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 128, message = "新密码长度必须在 8 到 128 位之间")
    private String newPassword;

    @NotBlank(message = "请再次输入新密码")
    private String confirmPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
