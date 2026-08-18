package com.api.generator.auth;

import com.api.generator.account.AppUser;
import com.api.generator.account.repo.AppUserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@Primary
public class DbUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public DbUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username == null ? "" : username.trim().toLowerCase();
        AppUser u = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // If password is null (GOOGLE), prevent password login
        String password = (u.getPasswordHash() == null) ? "{noop}__GOOGLE__" : u.getPasswordHash();

        var auths = Arrays.stream(u.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(password)
                .authorities(auths)
                .accountLocked(false)
                .disabled(!u.isEnabled() || !u.isEmailVerified())
                .build();
    }
}
