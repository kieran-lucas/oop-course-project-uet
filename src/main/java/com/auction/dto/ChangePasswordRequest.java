package com.auction.dto;

/** DTO đổi mật khẩu — nhận mật khẩu hiện tại và mật khẩu mới từ client */
public class ChangePasswordRequest {

  private String currentPassword;
  private String newPassword;

  public ChangePasswordRequest() {}

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
}
