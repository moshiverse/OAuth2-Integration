package edu.cit.johnjosephlaborada.oauth2integration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.johnjosephlaborada.oauth2integration.model.AuthProvider;
import edu.cit.johnjosephlaborada.oauth2integration.model.User;
import edu.cit.johnjosephlaborada.oauth2integration.repository.AuthProviderRepository;
import edu.cit.johnjosephlaborada.oauth2integration.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * CustomOAuth2UserService - full, commented "learning" edition.
 *
 * Responsibilities:
 *  - Load provider attributes (Google / GitHub)
 *  - Normalize and obtain an email (including GitHub's /user/emails if needed)
 *  - If an AuthProvider exists for (provider, providerUserId) -> return linked user
 *  - Else if a User exists with same email -> MERGE: create AuthProvider linking to that User
 *  - Else -> create new User and AuthProvider (first-time registration)
 *
 * Important behavior notes:
 *  - Provider values are normalized to uppercase in AuthProvider records ("GOOGLE", "GITHUB").
 *  - When provider reports email changes, the auth record and (optionally) the user email are updated.
 *  - The method is transactional to avoid partial writes in merge/creation flows.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserRepository userRepository;
    private final AuthProviderRepository authProviderRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomOAuth2UserService(UserRepository userRepository, AuthProviderRepository authProviderRepository) {
        this.userRepository = userRepository;
        this.authProviderRepository = authProviderRepository;
    }

    /**
     * Primary entry point called by Spring Security during OAuth2 login.
     * Marked @Transactional so creation/merge of user + authProvider happens atomically.
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        // 1) Delegate to the default service to fetch provider attributes
        OAuth2User oauth2User = super.loadUser(userRequest);

        // registrationId is configured in application.properties (google | github)
        String rawProvider = userRequest.getClientRegistration().getRegistrationId();
        String provider = rawProvider == null ? "unknown" : rawProvider.toLowerCase(Locale.ROOT);

        // Make a mutable copy of attributes so we can enrich/normalize them before returning principal
        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());

        // 2) Extract provider user id (provider-specific attribute names)
        //    Google: "sub", GitHub: "id"
        String providerUserId = null;
        if ("google".equals(provider)) {
            Object sub = attributes.get("sub");
            providerUserId = sub == null ? null : String.valueOf(sub);
        } else if ("github".equals(provider)) {
            Object id = attributes.get("id");
            providerUserId = id == null ? null : String.valueOf(id);
        } else {
            // Fallback: try common attributes (not expected in this app)
            Object id = attributes.get("id");
            providerUserId = id == null ? null : String.valueOf(id);
        }

        // 3) Obtain email: providers usually include it, but GitHub may not in /user
        String email = null;
        if (attributes.get("email") != null) {
            email = String.valueOf(attributes.get("email"));
        }

        // 4) For GitHub, if email missing, call /user/emails to find a primary/verified email
        if ("github".equals(provider) && (email == null || email.isBlank())) {
            String token = userRequest.getAccessToken().getTokenValue();
            String fetched = fetchPrimaryEmailFromGithub(token);
            if (fetched != null && !fetched.isBlank()) {
                email = fetched;
                // also place it into attributes so the principal can show it immediately
                attributes.put("email", email);
            }
        }

        // 5) Deterministic fallback email to keep DB unique constraints happy if provider gives no email
        if (email == null || email.isBlank()) {
            if (providerUserId != null && !providerUserId.isBlank()) {
                email = provider + "_" + providerUserId + "@no-email.local";
            } else {
                email = provider + "_unknown_" + UUID.randomUUID() + "@no-email.local";
            }
            attributes.put("email", email);
        }

        // Normalize provider names used in DB (AuthProvider.provider) for consistency
        String providerKey = provider.toUpperCase(Locale.ROOT);
        String providerUserIdKey = providerUserId == null ? "" : providerUserId;

        // 6) Attempt to find an existing AuthProvider mapping for (provider, providerUserId)
        Optional<AuthProvider> authProviderOpt = authProviderRepository
                .findByProviderAndProviderUserId(providerKey, providerUserIdKey);

        User user;

        if (authProviderOpt.isPresent()) {
            // Case A: provider account already linked to a user
            AuthProvider existingAuth = authProviderOpt.get();
            user = existingAuth.getUser();

            // If provider supplied an email that's different from stored providerEmail, update it
            if (email != null && !email.equals(existingAuth.getProviderEmail())) {
                existingAuth.setProviderEmail(email);
                authProviderRepository.save(existingAuth);
            }

            // Optionally keep User.email in sync if provider's email changed:
            // - If the user's email differs and the provider email is more "authoritative", update it.
            // - This decision depends on your business rules. Here we update user.email if different.
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                userRepository.save(user);
            }

        } else {
            // Case B: no existing provider mapping for this provider account
            // Try to find a local User by email. If found -> MERGE (attach new AuthProvider)
            Optional<User> userByEmailOpt = userRepository.findByEmail(email);

            if (userByEmailOpt.isPresent()) {
                // MERGE: existing user with same email found -> link provider account
                user = userByEmailOpt.get();

                // Avoid creating duplicate AuthProvider entries for same user+provider:
                Optional<AuthProvider> existingForUserProvider = authProviderRepository.findByUserAndProvider(user, providerKey);
                if (existingForUserProvider.isEmpty()) {
                    AuthProvider linked = new AuthProvider();
                    linked.setProvider(providerKey);
                    linked.setProviderUserId(providerUserIdKey);
                    linked.setProviderEmail(email);
                    linked.setUser(user);
                    authProviderRepository.save(linked);
                } else {
                    // There is already an auth record for this user and provider (rare if previous was by userId),
                    // ensure providerUserId and providerEmail are set/updated
                    AuthProvider existing = existingForUserProvider.get();
                    boolean changed = false;
                    if ((existing.getProviderUserId() == null || existing.getProviderUserId().isBlank())
                            && !providerUserIdKey.isBlank()) {
                        existing.setProviderUserId(providerUserIdKey);
                        changed = true;
                    }
                    if (email != null && !email.equals(existing.getProviderEmail())) {
                        existing.setProviderEmail(email);
                        changed = true;
                    }
                    if (changed) authProviderRepository.save(existing);
                }

            } else {
                // Case C: brand new user (first-time sign in with this provider)
                User newUser = new User();
                newUser.setEmail(email);

                // Determine displayName priority:
                // 1) attributes.name (Google usually)
                // 2) attributes.login (GitHub username)
                // 3) fallback readable provider label
                String displayName;
                if (attributes.get("name") != null && !String.valueOf(attributes.get("name")).isBlank()) {
                    displayName = String.valueOf(attributes.get("name"));
                } else if (attributes.get("login") != null && !String.valueOf(attributes.get("login")).isBlank()) {
                    displayName = String.valueOf(attributes.get("login"));
                } else {
                    displayName = provider.substring(0, 1).toUpperCase(Locale.ROOT)
                            + provider.substring(1) + " User";
                }
                newUser.setDisplayName(displayName);

                // Avatar priority: picture (Google) -> avatar_url (GitHub)
                String avatarUrl = null;
                if (attributes.get("picture") != null && !String.valueOf(attributes.get("picture")).isBlank()) {
                    avatarUrl = String.valueOf(attributes.get("picture"));
                } else if (attributes.get("avatar_url") != null && !String.valueOf(attributes.get("avatar_url")).isBlank()) {
                    avatarUrl = String.valueOf(attributes.get("avatar_url"));
                }
                newUser.setAvatarUrl(avatarUrl);

                // Persist user first (so AuthProvider can point to a persisted user)
                user = userRepository.save(newUser);

                // Persist AuthProvider linking this provider account to the newly created user
                AuthProvider authProvider = new AuthProvider();
                authProvider.setProvider(providerKey);
                authProvider.setProviderUserId(providerUserIdKey);
                authProvider.setProviderEmail(email);
                authProvider.setUser(user);
                authProviderRepository.save(authProvider);
            }
        }

        // 7) Build principal attributes from DB-backed user to ensure page displays persisted values
        Map<String, Object> principalAttrs = new HashMap<>(attributes);
        principalAttrs.put("email", user.getEmail());            // ensure DB email is authoritative
        principalAttrs.put("name", user.getDisplayName());      // show persisted displayName
        principalAttrs.put("avatar_url", user.getAvatarUrl());  // show persisted avatar

        // 8) Return a DefaultOAuth2User with ROLE_USER and "email" as the name attribute
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                principalAttrs,
                "email"
        );
    }

    /**
     * GitHub may not return an email in the /user response (user might keep email private).
     * This helper calls the /user/emails endpoint and returns:
     *  - primary && verified email if present
     *  - else first verified email
     *  - else first available email
     *
     * If anything fails we return null (caller will fall back to deterministic email).
     *
     * Note: in production you'd want to paginate and handle rate limits / errors properly.
     */
    private String fetchPrimaryEmailFromGithub(String accessToken) {
        try {
            String url = "https://api.github.com/user/emails";
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                List<Map<String, Object>> emails = objectMapper.readValue(resp.getBody(),
                        new TypeReference<List<Map<String, Object>>>() {
                        });

                // 1) prefer primary + verified
                for (Map<String, Object> m : emails) {
                    Object primaryObj = m.get("primary");
                    Object verifiedObj = m.get("verified");
                    Object emailObj = m.get("email");
                    boolean primary = primaryObj instanceof Boolean && (Boolean) primaryObj;
                    boolean verified = verifiedObj instanceof Boolean && (Boolean) verifiedObj;
                    if (primary && verified && emailObj != null) {
                        return String.valueOf(emailObj);
                    }
                }

                // 2) prefer any verified
                for (Map<String, Object> m : emails) {
                    Object verifiedObj = m.get("verified");
                    Object emailObj = m.get("email");
                    boolean verified = verifiedObj instanceof Boolean && (Boolean) verifiedObj;
                    if (verified && emailObj != null) {
                        return String.valueOf(emailObj);
                    }
                }

                // 3) fallback to first available
                if (!emails.isEmpty()) {
                    Object emailObj = emails.get(0).get("email");
                    if (emailObj != null) {
                        return String.valueOf(emailObj);
                    }
                }
            }
        } catch (Exception ex) {
            // Log the exception; return null and let caller use fallback strategy
            log.warn("Unable to fetch GitHub emails: {}", ex.getMessage());
            log.debug("Full exception fetching GitHub emails", ex);
        }
        return null;
    }
}
