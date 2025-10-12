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

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String rawProvider = userRequest.getClientRegistration().getRegistrationId();
        String provider = rawProvider == null ? "unknown" : rawProvider.toLowerCase(Locale.ROOT);

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());

        String providerUserId = null;
        if ("google".equals(provider)) {
            Object sub = attributes.get("sub");
            providerUserId = sub == null ? null : String.valueOf(sub);
        } else if ("github".equals(provider)) {
            Object id = attributes.get("id");
            providerUserId = id == null ? null : String.valueOf(id);
        } else {
            Object id = attributes.get("id");
            providerUserId = id == null ? null : String.valueOf(id);
        }

        String email = null;
        if (attributes.get("email") != null) {
            email = String.valueOf(attributes.get("email"));
        }

        if ("github".equals(provider) && (email == null || email.isBlank())) {
            String token = userRequest.getAccessToken().getTokenValue();
            String fetched = fetchPrimaryEmailFromGithub(token);
            if (fetched != null && !fetched.isBlank()) {
                email = fetched;
                attributes.put("email", email);
            }
        }

        if (email == null || email.isBlank()) {
            if (providerUserId != null && !providerUserId.isBlank()) {
                email = provider + "_" + providerUserId + "@no-email.local";
            } else {
                email = provider + "_unknown_" + UUID.randomUUID() + "@no-email.local";
            }
            attributes.put("email", email);
        }

        String providerKey = provider.toUpperCase(Locale.ROOT);
        String providerUserIdKey = providerUserId == null ? "" : providerUserId;

        Optional<AuthProvider> authProviderOpt = authProviderRepository
                .findByProviderAndProviderUserId(providerKey, providerUserIdKey);

        User user;

        if (authProviderOpt.isPresent()) {
            AuthProvider existingAuth = authProviderOpt.get();
            user = existingAuth.getUser();

            if (email != null && !email.equals(existingAuth.getProviderEmail())) {
                existingAuth.setProviderEmail(email);
                authProviderRepository.save(existingAuth);
            }

            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                userRepository.save(user);
            }

        } else {
            Optional<User> userByEmailOpt = userRepository.findByEmail(email);

            if (userByEmailOpt.isPresent()) {
                user = userByEmailOpt.get();

                Optional<AuthProvider> existingForUserProvider = authProviderRepository.findByUserAndProvider(user, providerKey);
                if (existingForUserProvider.isEmpty()) {
                    AuthProvider linked = new AuthProvider();
                    linked.setProvider(providerKey);
                    linked.setProviderUserId(providerUserIdKey);
                    linked.setProviderEmail(email);
                    linked.setUser(user);
                    authProviderRepository.save(linked);
                } else {
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
                User newUser = new User();
                newUser.setEmail(email);

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

                String avatarUrl = null;
                if (attributes.get("picture") != null && !String.valueOf(attributes.get("picture")).isBlank()) {
                    avatarUrl = String.valueOf(attributes.get("picture"));
                } else if (attributes.get("avatar_url") != null && !String.valueOf(attributes.get("avatar_url")).isBlank()) {
                    avatarUrl = String.valueOf(attributes.get("avatar_url"));
                }
                newUser.setAvatarUrl(avatarUrl);

                user = userRepository.save(newUser);

                AuthProvider authProvider = new AuthProvider();
                authProvider.setProvider(providerKey);
                authProvider.setProviderUserId(providerUserIdKey);
                authProvider.setProviderEmail(email);
                authProvider.setUser(user);
                authProviderRepository.save(authProvider);
            }
        }

        Map<String, Object> principalAttrs = new HashMap<>(attributes);
        principalAttrs.put("email", user.getEmail());            // ensure DB email is authoritative
        principalAttrs.put("name", user.getDisplayName());      // show persisted displayName
        principalAttrs.put("avatar_url", user.getAvatarUrl());  // show persisted avatar

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                principalAttrs,
                "email"
        );
    }

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

                for (Map<String, Object> m : emails) {
                    Object verifiedObj = m.get("verified");
                    Object emailObj = m.get("email");
                    boolean verified = verifiedObj instanceof Boolean && (Boolean) verifiedObj;
                    if (verified && emailObj != null) {
                        return String.valueOf(emailObj);
                    }
                }

                if (!emails.isEmpty()) {
                    Object emailObj = emails.get(0).get("email");
                    if (emailObj != null) {
                        return String.valueOf(emailObj);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to fetch GitHub emails: {}", ex.getMessage());
            log.debug("Full exception fetching GitHub emails", ex);
        }
        return null;
    }
}
