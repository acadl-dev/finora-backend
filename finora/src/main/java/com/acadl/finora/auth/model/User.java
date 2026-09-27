// User.java
package com.acadl.finora.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Data
@ToString(exclude = "credential")            // evita loop infinito no toString
@EqualsAndHashCode(exclude = "credential")   // evita loop infinito no equals/hashCode
@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Credential credential;

    public User(String name) {
        this.name = name;
    }
}