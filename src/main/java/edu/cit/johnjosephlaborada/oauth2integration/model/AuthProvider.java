package edu.cit.johnjosephlaborada.oauth2integration.model;

import jakarta.persistence.*;

/**
 * AuthProvider entity links an OAuth provider (Google/GitHub)
 * to a single User account.
 *
 * This allows:
 * - One user to have multiple login methods
 * - Merge Google and GitHub using same email
 */
@Entity
@Table(name = "auth_providers")
public class AuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The OAuth provider name: "GOOGLE" or "GITHUB".
     */
    @Column(nullable = false)
    private String provider;

    /**
     * The unique user ID from the provider (Google's 'sub', GitHub's 'id').
     */
    @Column(nullable = false)
    private String providerUserId;

    /**
     * The email reported by the provider. (Might differ from main user email.)
     */
    private String providerEmail;

    /**
     * Many providers (Google, GitHub) can map to ONE user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderUserId() { return providerUserId; }
    public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }

    public String getProviderEmail() { return providerEmail; }
    public void setProviderEmail(String providerEmail) { this.providerEmail = providerEmail; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
