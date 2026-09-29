package com.cuenti.app.security;

import com.cuenti.app.model.User;
import com.cuenti.app.repository.AuditLogRepository;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API access control: Bearer tokens only, and a token stops working as soon as
 * the account is disabled or deleted, API access is withdrawn, the password
 * changes or the user signs out all devices.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiAccessSecurityTest {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired JwtTokenProvider tokenProvider;

    private User user;

    @BeforeEach
    void setUp() {
        String name = "api" + System.nanoTime();
        user = userService.registerUser(name, name + "@x.com", PASSWORD, "Api", "User");
        userService.updateApiEnabled(user, true);
    }

    private String token() {
        User stored = userRepository.findById(user.getId()).orElseThrow();
        return tokenProvider.generateToken(stored.getUsername(), stored.getTokenVersion());
    }

    private int profileStatus(String token) throws Exception {
        return mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        assertThat(profileStatus(token())).isEqualTo(200);
    }

    @Test
    void httpBasicIsRejected() throws Exception {
        mockMvc.perform(get("/api/user/profile").with(httpBasic(user.getUsername(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawingApiAccessRejectsExistingToken() throws Exception {
        String token = token();
        userService.updateApiEnabled(user, false);
        assertThat(profileStatus(token)).isEqualTo(401);
    }

    @Test
    void disabledUserIsRejected() throws Exception {
        String token = token();
        userService.setUserEnabled(user, false);
        assertThat(profileStatus(token)).isEqualTo(401);
    }

    @Test
    void deletedUserGets401Not500() throws Exception {
        // a validly signed token whose user no longer exists
        assertThat(profileStatus(tokenProvider.generateToken("deleted-" + System.nanoTime(), 0))).isEqualTo(401);
    }

    @Test
    void passwordChangeRevokesOldTokenAndReturnsNewOne() throws Exception {
        String oldToken = token();
        String body = mockMvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another-horse-2\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newToken = objectMapper.readTree(body).get("token").asString();
        assertThat(profileStatus(oldToken)).isEqualTo(401);
        assertThat(profileStatus(newToken)).isEqualTo(200);
    }

    @Test
    void logoutAllRevokesTokens() throws Exception {
        String token = token();
        mockMvc.perform(post("/api/user/logout-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(profileStatus(token)).isEqualTo(401);
    }

    @Test
    void tokenWithoutVersionClaimStillWorksUntilFirstRevocation() throws Exception {
        // tokens issued before versioning carry no claim and count as version 0
        String legacy = io.jsonwebtoken.Jwts.builder().subject(user.getUsername())
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey()).compact();
        assertThat(profileStatus(legacy)).isEqualTo(200);
        userService.revokeTokens(user, "LOGOUT_ALL");
        assertThat(profileStatus(legacy)).isEqualTo(401);
    }

    @Test
    void shortPasswordsAreRejectedByTheApi() throws Exception {
        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"shorty\",\"email\":\"shorty@x.com\",\"password\":\"1234567\","
                                + "\"firstName\":\"S\",\"lastName\":\"P\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordPolicyInService() {
        assertThatThrownBy(() -> userService.updatePassword(user, "short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userService.updatePassword(user, "x".repeat(129))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleUserCopyDoesNotUndoAdminChanges() {
        User staleCopy = userRepository.findById(user.getId()).orElseThrow();
        userService.setUserEnabled(userRepository.findById(user.getId()).orElseThrow(), false);

        // the user's own tab still holds the copy from before and toggles a preference
        userService.updateDarkMode(staleCopy, false);

        User stored = userRepository.findById(user.getId()).orElseThrow();
        assertThat(stored.getEnabled()).isFalse();
        assertThat(stored.getTokenVersion()).isEqualTo(1);
        assertThat(stored.isDarkMode()).isFalse();
    }

    @Test
    void apiLoginsAreAudited() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        var actions = auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, 20)).stream()
                .filter(l -> l.getUsername().equals(user.getUsername()))
                .map(l -> l.getAction() + " " + l.getDetails())
                .toList();
        assertThat(actions).anyMatch(a -> a.startsWith("LOGIN api"));
        assertThat(actions).anyMatch(a -> a.startsWith("LOGIN_FAILED api"));
    }

    @Test
    void webResponsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    private javax.crypto.SecretKey signingKey() {
        try {
            var field = JwtTokenProvider.class.getDeclaredField("key");
            field.setAccessible(true);
            return (javax.crypto.SecretKey) field.get(tokenProvider);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
