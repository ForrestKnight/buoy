package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.AppUser;
import io.github.forrestknight.buoy.persistence.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser create(String username, String password, String displayName, boolean instanceAdmin) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateKeyException("User", username);
        }
        return userRepository.save(new AppUser(username, passwordEncoder.encode(password),
                displayName, instanceAdmin));
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AppUser get(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User", username));
    }

    public void delete(String username) {
        userRepository.delete(get(username));
    }
}
