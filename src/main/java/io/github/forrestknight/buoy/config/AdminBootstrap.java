package io.github.forrestknight.buoy.config;

import io.github.forrestknight.buoy.domain.AppUser;
import io.github.forrestknight.buoy.persistence.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Creates the first instance admin when the user table is empty. Credentials come
 * from {@code buoy.bootstrap.*} (env: BUOY_BOOTSTRAP_USERNAME / BUOY_BOOTSTRAP_PASSWORD);
 * without a configured password a random one is generated and printed to the log
 * exactly once, Jenkins-style.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BuoyBootstrapProperties properties;

    public AdminBootstrap(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                          BuoyBootstrapProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        boolean generated = properties.password() == null || properties.password().isBlank();
        String password = generated ? generatePassword() : properties.password();
        userRepository.save(new AppUser(properties.username(),
                passwordEncoder.encode(password), "Administrator", true));
        if (generated) {
            log.warn("""

                    *************************************************************
                    Buoy initial admin created.

                    username: {}
                    password: {}

                    This password is shown once and is not recoverable.
                    Log in and change it, or set BUOY_BOOTSTRAP_PASSWORD
                    before first startup to choose your own.
                    *************************************************************
                    """, properties.username(), password);
        } else {
            log.info("Buoy initial admin '{}' created from configured credentials", properties.username());
        }
    }

    private String generatePassword() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
