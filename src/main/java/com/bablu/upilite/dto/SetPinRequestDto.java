package com.bablu.upilite.dto;

import lombok.Data;

@Data
public class SetPinRequestDto {
    private String pin;
    private String confirmPin;
}
