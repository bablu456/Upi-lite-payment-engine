package com.bablu.upilite.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletCreditRequestDto {
    private BigDecimal amount;
}
