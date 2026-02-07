package com.bablu.upilite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users") // 'user' reserved keyword hota hai postgres mein, isliye 'users'
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails { // 👈 Changed here

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String mobile;

    private String password; // Ye ab Encrypted store hoga

    @Column(unique = true, nullable = false)
    private String upiId;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Wallet wallet;

    // --- UserDetails Methods (Security ke liye) ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Filhal sabko "USER" role dete hain
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getUsername() {
        return this.email; // Hum Email ko as Username use karenge
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}