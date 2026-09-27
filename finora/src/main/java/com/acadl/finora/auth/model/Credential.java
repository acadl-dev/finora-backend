// Credential.java
package com.acadl.finora.auth.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
@ToString(exclude = "user")            // evita loop infinito no toString
@EqualsAndHashCode(exclude = "user")   // evita loop infinito no equals/hashCode
@Entity
@Table(name = "credentials")
@AllArgsConstructor
@NoArgsConstructor
public class Credential implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "Email é um campo obrigatório!")
    @Column(nullable = false)
    private String email;

    @NotBlank(message = "A senha é um campo obrigatório!")
    @Column(nullable = false)
    private String hashPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true; // campo de verdade da entidade

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Credential(String email, String hashPassword) {
        this.email = email;
        this.hashPassword = hashPassword;
    }

    // ---- Métodos exigidos pela interface UserDetails ----

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return hashPassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}