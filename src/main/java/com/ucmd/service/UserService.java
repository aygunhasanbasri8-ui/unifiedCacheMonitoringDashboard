package com.ucmd.service;

import com.ucmd.model.entity.User;
import com.ucmd.model.dto.LoginResponse;
import org.springframework.stereotype.Service;

@Service
public interface UserService {

    LoginResponse authenticate(String username, String password);
    User getUserFromToken(String token);
    User createUser(String username, String password, String role);
    java.util.Collection<User> getAllUsers();
    void deleteUser(String username);


}
