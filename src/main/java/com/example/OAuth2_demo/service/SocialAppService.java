package com.example.OAuth2_demo.service;

import com.example.OAuth2_demo.entity.User;
import com.example.OAuth2_demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class SocialAppService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(SocialAppService.class);

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // Логика сохранения юзера, определения роли на основе аутентификации
        String email = oAuth2User.getAttribute("email");
        String accessToken = userRequest.getAccessToken().getTokenValue();
        String username = oAuth2User.getAttribute("name");
        String role = oAuth2User.getAttribute("role");

        if (role == null) {
            role = "USER";
        }

        String finalRole = role;
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User savedUser = new User();
            savedUser.setUsername(username);
            savedUser.setEmail(email);
            savedUser.setRole(finalRole);
            return userRepository.save(savedUser);
        });

        user.setAccessToken(accessToken);
        userRepository.save(user);

        return oAuth2User;
    }
}