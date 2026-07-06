package io.github.forrestknight.buoy.service;

import io.github.forrestknight.buoy.domain.AppUser;
import io.github.forrestknight.buoy.domain.AuditAction;
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
    private final AuditService auditService;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public AppUser create(String username, String password, String displayName, boolean instanceAdmin) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateKeyException("User", username);
        }
        AppUser user = userRepository.save(new AppUser(username, passwordEncoder.encode(password),
                displayName, instanceAdmin));
        auditService.record(AuditAction.CREATED, "USER", user.getId(), user.getUsername(),
                null, null, null, AuditSnapshots.of(user));
        return user;
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
        AppUser user = get(username);
        auditService.record(AuditAction.DELETED, "USER", user.getId(), user.getUsername(),
                null, null, AuditSnapshots.of(user), null);
        userRepository.delete(user);
    }
}
