package com.techgiant.notification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This is the external user ID from the main application (e.g. "tushar_123")
    @Column(nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 150)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Device> devices;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
