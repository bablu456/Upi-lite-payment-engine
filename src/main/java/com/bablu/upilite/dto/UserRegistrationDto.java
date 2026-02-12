package com.bablu.upilite.dto;

import lombok.Data;

@Data
public class UserRegistrationDto {
    private String username;
    private String email;
    private String mobile;
    private String password;
}
