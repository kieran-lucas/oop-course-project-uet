package com.auction.dto;

import java.math.BigDecimal;

/** DTO nạp tiền vào tài khoản — nhận số tiền muốn nạp từ client */
public class DepositRequest {

  private BigDecimal amount;

  public DepositRequest() {}

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }
}
