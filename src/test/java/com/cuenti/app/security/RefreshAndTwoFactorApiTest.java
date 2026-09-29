package com.cuenti.app.security;

import com.cuenti.app.model.User;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Refresh-token rotation and two-factor sign-in over the REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RefreshAndTwoFactorApiTest {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired LoginRateLimitFilter loginRateLimitFilter;
    @Autowired UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        loginRateLimitFilter.reset();
        String name = "rt" + System.nanoTime();
        user = userService.registerUser(name, name + "@x.com", PASSWORD, "Re", "Fresh");
        userService.updateApiEnabled(user, true);
    }

    private MvcResult login(String extra) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"" + extra + "}"))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}")).andReturn();
    }

    private int profileStatus(String token) throws Exception {
        return mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private String currentCode(String secret) {
        return Totp.code(Totp.base32Decode(secret), Totp.currentStep());
    }

    @Test
    void legacyLoginGetsOneLongLivedTokenAndNoRefreshToken() throws Exception {
        JsonNode body = json(login(""));
        assertThat(body.get("refreshToken").isNull()).isTrue();
        assertThat(body.get("expiresIn").asLong()).isEqualTo(86400);
    }

    @Test
    void refreshRotatesAndTheNewAccessTokenWorks() throws Exception {
        JsonNode first = json(login(",\"refresh\":true"));
        assertThat(first.get("expiresIn").asLong()).isEqualTo(900);
        String refreshToken = first.get("refreshToken").asString();

        MvcResult rotated = refresh(refreshToken);
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        JsonNode next = json(rotated);
        assertThat(next.get("refreshToken").asString()).isNotEqualTo(refreshToken);
        assertThat(profileStatus(next.get("token").asString())).isEqualTo(200);
    }

    @Test
    void reusingARefreshTokenRevokesTheWholeChain() throws Exception {
        String original = json(login(",\"refresh\":true")).get("refreshToken").asString();
        String successor = json(refresh(original)).get("refreshToken").asString();

        // a copy of the original shows up again
        assertThat(refresh(original).getResponse().getStatus()).isEqualTo(401);
        // ... and the legitimate successor is dead too
        assertThat(refresh(successor).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void revokingTokensAlsoRevokesRefreshTokens() throws Exception {
        String refreshToken = json(login(",\"refresh\":true")).get("refreshToken").asString();
        userService.revokeTokens(user, "LOGOUT_ALL");
        assertThat(refresh(refreshToken).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshFailsOnceApiAccessIsWithdrawn() throws Exception {
        String refreshToken = json(login(",\"refresh\":true")).get("refreshToken").asString();
        userService.updateApiEnabled(user, false);
        assertThat(refresh(refreshToken).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unknownRefreshTokenIs401() throws Exception {
        assertThat(refresh("nope").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void passwordChangeHandsRefreshClientsANewPair() throws Exception {
        JsonNode first = json(login(",\"refresh\":true"));
        MvcResult changed = mockMvc.perform(put("/api/user/password")
                        .header("Authorization", "Bearer " + first.get("token").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another-horse-2\",\"refresh\":true}"))
                .andReturn();
        JsonNode pair = json(changed);
        assertThat(refresh(first.get("refreshToken").asString()).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(pair.get("refreshToken").asString()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void twoFactorLoginNeedsAValidFreshCode() throws Exception {
        UserService.TotpSetup setup = userService.startTotpSetup(user);
        List<String> recovery = userService.enableTotp(user, setup.secret(), currentCode(setup.secret()));
        assertThat(recovery).hasSize(10);

        MvcResult noCode = login("");
        assertThat(noCode.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(noCode).get("error").asString()).isEqualTo("two_factor_required");

        MvcResult wrong = login(",\"code\":\"000000\"");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(wrong).get("error").asString()).isEqualTo("invalid_code");

        // the code used to enable is already consumed: replay is refused
        assertThat(login(",\"code\":\"" + currentCode(setup.secret()) + "\"").getResponse().getStatus())
                .isEqualTo(401);

        // a recovery code works exactly once
        assertThat(login(",\"code\":\"" + recovery.get(0).toLowerCase() + "\"").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(login(",\"code\":\"" + recovery.get(0) + "\"").getResponse().getStatus()).isEqualTo(401);
        assertThat(userService.remainingRecoveryCodes(userRepository.findById(user.getId()).orElseThrow()))
                .isEqualTo(9);
    }

    @Test
    void enablingNeedsACodeForTheNewSecret() {
        UserService.TotpSetup setup = userService.startTotpSetup(user);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userService.enableTotp(user, setup.secret(), "123456"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isTotpEnabled()).isFalse();
    }

    @Test
    void adminResetTurnsTwoFactorOff() throws Exception {
        UserService.TotpSetup setup = userService.startTotpSetup(user);
        userService.enableTotp(user, setup.secret(), currentCode(setup.secret()));
        userService.resetTotp(userRepository.findById(user.getId()).orElseThrow());
        assertThat(login("").getResponse().getStatus()).isEqualTo(200);
    }
}
