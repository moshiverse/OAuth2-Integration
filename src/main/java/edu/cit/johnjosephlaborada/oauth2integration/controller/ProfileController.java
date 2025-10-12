package edu.cit.johnjosephlaborada.oauth2integration.controller;

import edu.cit.johnjosephlaborada.oauth2integration.model.User;
import edu.cit.johnjosephlaborada.oauth2integration.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) {
            return "redirect:/";
        }

        // We use the 'email' principal attribute (our CustomOAuth2UserService ensures it's present)
        String email = principal.getAttribute("email");
        if (email == null) {
            // fallback: some providers expose 'login' etc. but merging logic uses email so this is rare
            email = principal.getAttribute("login");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("bio", user.getBio());
            model.addAttribute("email", user.getEmail());
            model.addAttribute("picture", user.getAvatarUrl());
            model.addAttribute("name", user.getDisplayName());
        } else {
            // fallback to OAuth2 attributes if DB row missing (shouldn't happen after merging logic)
            model.addAttribute("displayName", principal.getAttribute("name"));
            model.addAttribute("bio", "");
            model.addAttribute("email", principal.getAttribute("email"));
            model.addAttribute("picture", principal.getAttribute("avatar_url"));
            model.addAttribute("name", principal.getAttribute("name"));
        }
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal OAuth2User principal,
                                @RequestParam String displayName,
                                @RequestParam(required = false) String bio) {
        if (principal == null) return "redirect:/";

        String email = principal.getAttribute("email");
        if (email == null) email = principal.getAttribute("login");

        userRepository.findByEmail(email).ifPresent(user -> {
            user.setDisplayName(displayName);
            user.setBio(bio);
            userRepository.save(user);
        });

        // Redirect to reload values from DB
        return "redirect:/profile";
    }
}
