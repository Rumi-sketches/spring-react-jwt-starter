package com.jwtdemo.jwt_starter.backend.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridge between Spring Security's authentication infrastructure and the application's
 * user database.
 *
 * <p>Spring Security calls {@link #loadUserByUsername} in two different places:
 * <ol>
 *   <li><b>During login</b>: the {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}
 *       calls this method to load the user, then compares the submitted password against
 *       {@link User#getPassword()} using BCrypt.</li>
 *   <li><b>On every authenticated request</b>: {@link com.jwtdemo.jwt_starter.backend.common.security.JwtAuthFilter}
 *       calls this method after extracting the email from the JWT to verify the account
 *       still exists and is still active.</li>
 * </ol>
 *
 * <p>Note: "username" in Spring Security terminology maps to "email" in this application.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a {@link User} by their email address.
     *
     * <p>Throws {@link UsernameNotFoundException} if no user is found. Spring Security
     * catches this exception during login and converts it to a generic
     * "bad credentials" error — the caller never learns whether the email or the password
     * was wrong, which prevents user-enumeration attacks.
     *
     * @param email the email address to look up (called "username" by the Spring Security API)
     * @return the matching {@link User}, which also implements {@link UserDetails}
     * @throws UsernameNotFoundException if no user with that email exists
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for email: " + email));
    }
}
