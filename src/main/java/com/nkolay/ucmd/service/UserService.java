package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.entity.User;
import com.nkolay.ucmd.model.dto.LoginResponse;
import org.springframework.stereotype.Service;

@Service
public interface UserService {

    LoginResponse authenticate(String username, String password);
    User getUserFromToken(String token);
    User createUser(String username, String password, String email, String role);
    java.util.Collection<User> getAllUsers();
    void deleteUser(String username);


}
