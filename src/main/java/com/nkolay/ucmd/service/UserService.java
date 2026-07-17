package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.User;
import com.nkolay.ucmd.model.LoginResponse;
import com.nkolay.ucmd.security.SimplePasswordEncoder;
import com.nkolay.ucmd.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public interface UserService {

    LoginResponse authenticate(String username, String password);
    User getUserFromToken(String token);
    User createUser(String username, String password, String email, String role);
    java.util.Collection<User> getAllUsers();


}
