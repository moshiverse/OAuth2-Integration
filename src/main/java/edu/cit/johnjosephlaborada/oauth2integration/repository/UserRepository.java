package edu.cit.johnjosephlaborada.oauth2integration.repository;

import edu.cit.johnjosephlaborada.oauth2integration.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Finds users by email (central for merging Google & GitHub)
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
