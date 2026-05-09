package com.auction.dto;

/** DTO cho yêu cầu quên mật khẩu — chứa email cần đặt lại. */
public class ForgotPasswordRequest {

  private String email;

  public ForgotPasswordRequest() {}

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
