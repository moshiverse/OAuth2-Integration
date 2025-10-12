package edu.cit.johnjosephlaborada.oauth2integration.repository;

import edu.cit.johnjosephlaborada.oauth2integration.model.AuthProvider;
import edu.cit.johnjosephlaborada.oauth2integration.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Handles lookup of OAuth provider accounts (Google / GitHub).
 * Used to check if a provider is already linked to a user.
 */
public interface AuthProviderRepository extends JpaRepository<AuthProvider, Long> {

    Optional<AuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<AuthProvider> findByUserAndProvider(User user, String provider);
}