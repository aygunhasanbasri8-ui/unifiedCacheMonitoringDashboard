package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.config.BootstrapUserConfig;
import com.nkolay.ucmd.model.entity.User;
import com.nkolay.ucmd.model.dto.LoginResponse;
import com.nkolay.ucmd.repository.UserRepository;
import com.nkolay.ucmd.security.JwtTokenProvider;
import com.nkolay.ucmd.service.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapUserConfig bootstrapUser;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           BootstrapUserConfig bootstrapUser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUser = bootstrapUser;
    }

    /** İlk açılışta yapılandırmadaki tek yönetici hesabını oluşturur.
     *  Hesap zaten varsa parolası değiştirilmez. */
    @PostConstruct
    public void initBootstrapUser() {
        if ("production".equalsIgnoreCase(System.getenv("APP_ENVIRONMENT"))) {
            return;
        }

        if (!bootstrapUser.isConfigured()) {
            throw new IllegalStateException(
                    "Bootstrap kullanıcısı yapılandırılmamış. .env dosyasında " +
                            "UCMD_BOOTSTRAP_USERNAME ve UCMD_BOOTSTRAP_PASSWORD tanımlayın.");
        }

        if (userRepository.existsByUsername(bootstrapUser.getUsername())) {
            return;
        }

        userRepository.save(new User(
                bootstrapUser.getUsername(),
                passwordEncoder.encode(bootstrapUser.getPassword()),
                bootstrapUser.getRole()));
    }

    @Override
    public LoginResponse authenticate(String username, String password) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isActive()) {
            return null;
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }

        String token = JwtTokenProvider.generateToken(user);
        user.setLastLoginAt(System.currentTimeMillis());
        userRepository.save(user);

        return new LoginResponse(token, user.getUsername(), user.getRole(), 24 * 60 * 60);
    }

    @Override
    public User getUserFromToken(String token) {
        String username = JwtTokenProvider.getUsernameFromToken(token);
        if (username == null) return null;
        return userRepository.findByUsername(username).orElse(null);
    }

    @Override
    public User createUser(String username, String password, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Kullanıcı adı zaten mevcut: " + username);
        }
        User user = new User(username,
                passwordEncoder.encode(password),
                 role);
        return userRepository.save(user);
    }

    @Override
    public Collection<User> getAllUsers() {
        return userRepository.findAll();
    }


    @Override
    public void deleteUser(String username) {
        // Erişimi bootstrap eden hesap silinemez — aksi halde sisteme girilemez hale gelir
        if (username != null && username.equalsIgnoreCase(bootstrapUser.getUsername())) {
            throw new IllegalArgumentException(
                    "Varsayılan '" + bootstrapUser.getUsername() + "' kullanıcısı silinemez");
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("Kullanıcı bulunamadı: " + username);
        }
        userRepository.delete(user);
    }
}