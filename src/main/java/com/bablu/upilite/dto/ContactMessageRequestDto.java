package com.bablu.upilite.dto;

import lombok.Data;

@Data
public class ContactMessageRequestDto {
    private String receiverUpiId;
    private String receiverMobile;
    private String message;
}
