package com.bablu.upilite.controller;


import com.bablu.upilite.dto.UserRegistrationDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.repository.UserRepository;
import com.bablu.upilite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody UserRegistrationDto dto){
        User createdUser = userService.registerUser(dto);
        return ResponseEntity.ok(createdUser);
    }

    @GetMapping
    public List<User> getAll(){
        return userRepository.findAll();
    }
}
