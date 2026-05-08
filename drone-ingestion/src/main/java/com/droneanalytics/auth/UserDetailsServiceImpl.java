package com.droneanalytics.auth;

import com.droneanalytics.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a {@link com.droneanalytics.entity.User} by email for Spring Security.
 *
 * Because {@code User} itself implements {@code UserDetails}, no adapter is needed —
 * we just return the entity directly.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
            .orElseThrow(() ->
                new UsernameNotFoundException("No user found with email: " + email));
    }
}
