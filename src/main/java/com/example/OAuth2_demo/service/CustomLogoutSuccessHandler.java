package com.example.OAuth2_demo.service;

import com.example.OAuth2_demo.entity.User;
import com.example.OAuth2_demo.exception.TokenExpiredException;
import com.example.OAuth2_demo.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

@Component
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomLogoutSuccessHandler.class);

    private UserRepository userRepository;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.github.client-secret}")
    private String clientSecret;

    @Autowired
    public CustomLogoutSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {

        if (authentication != null && authentication.getDetails() != null) {
            try {
                OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                String email = oauthToken.getPrincipal().getAttribute("email");
                User user = userRepository.findByEmail(email).orElseThrow();
                if (user == null || user.getAccessToken() == null) {
                    logger.error("Токен доступа отсутствует у пользователя с email: {}", email);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Access token is missing.");
                    return;
                }
                String accessToken = user.getAccessToken();
                revokeToken(accessToken);
            } catch (TokenExpiredException e) {
                logger.error("Токен истек: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has expired. Please login again.");
                return;
            } catch (Exception e) {
                logger.error("Ошибка при отзыве токена: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to revoke token.");
                return;
            }
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.sendRedirect("/");

        logger.info("Пользователь {} вышел из системы", authentication != null ?
                authentication.getName() : "неизвестный пользователь");
    }

    private void revokeToken(String accessToken) throws IOException {
        logger.info("Начинаем отзыв токена: {}", accessToken);

        URL url = new URL("https://api.github.com/applications/" + clientId + "/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Basic " +
                Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes()));
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String requestBody = "{\"access_token\":\"" + accessToken + "\"}";
        logger.info("Отправляем запрос: {}", requestBody);

        conn.getOutputStream().write(requestBody.getBytes());

        int responseCode = conn.getResponseCode();
        logger.info("Получен код ответа: {}", responseCode);

        if (responseCode == 401) {
            throw new TokenExpiredException("Token has expired.");
        } else if (responseCode != 204) {
            logger.error("Не удалось отозвать токен. Код ответа: {}", responseCode);
            throw new IOException("Failed to revoke token");
        }
        logger.info("Токен успешно отозван");
    }
}