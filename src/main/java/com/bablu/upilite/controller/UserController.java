package com.bablu.upilite.controller;


import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody UserRegistrationDto dto){
        User createdUser = userService.registerUser(dto);
        return ResponseEntity.ok(createdUser);
    }
}
