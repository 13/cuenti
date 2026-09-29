package com.cuenti.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IdleSessionFilterTest {

    private final SecurityAuditListener audit = mock(SecurityAuditListener.class);

    private final IdleSessionFilter filter = new IdleSessionFilter(new IdleTimeoutSettings(Duration.ofMinutes(15)), audit);

    private static MockHttpSession authenticatedSession(long lastActivity) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of())));
        session.setAttribute(IdleSessionFilter.LAST_ACTIVITY, lastActivity);
        return session;
    }

    private static MockHttpServletRequest request(MockHttpSession session, String vr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
        request.setSession(session);
        if (vr != null) {
            request.setQueryString("v-r=" + vr);
            request.setParameter("v-r", vr);
        }
        return request;
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void heartbeatAndPushDoNotCountAsActivity() throws Exception {
        long old = System.currentTimeMillis() - 60_000;
        MockHttpSession session = authenticatedSession(old);

        run(request(session, "heartbeat"));
        run(request(session, "push"));

        assertThat(session.getAttribute(IdleSessionFilter.LAST_ACTIVITY)).isEqualTo(old);
    }

    @Test
    void uidlRequestCountsAsActivity() throws Exception {
        long old = System.currentTimeMillis() - 60_000;
        MockHttpSession session = authenticatedSession(old);

        run(request(session, "uidl"));

        assertThat((Long) session.getAttribute(IdleSessionFilter.LAST_ACTIVITY)).isGreaterThan(old);
    }

    @Test
    void authenticatedSessionIdlePastTimeoutPlusGraceIsInvalidated() throws Exception {
        MockHttpSession session = authenticatedSession(System.currentTimeMillis() - Duration.ofMinutes(17).toMillis());

        run(request(session, "heartbeat"));

        assertThat(session.isInvalid()).isTrue();
        verify(audit).idleLogout("demo", "server");
    }

    @Test
    void withinGraceSessionSurvives() throws Exception {
        MockHttpSession session = authenticatedSession(System.currentTimeMillis() - Duration.ofSeconds(15 * 60 + 30).toMillis());

        run(request(session, "heartbeat"));

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void anonymousSessionIsNeverInvalidated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(IdleSessionFilter.LAST_ACTIVITY, 0L);

        run(request(session, null));

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void disabledTimeoutSkipsFilter() throws Exception {
        IdleSessionFilter disabled = new IdleSessionFilter(new IdleTimeoutSettings(Duration.ZERO), audit);
        MockHttpSession session = authenticatedSession(0L);

        disabled.doFilter(request(session, null), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void warningIsCappedAtHalfTheTimeout() {
        assertThat(new IdleTimeoutSettings(Duration.ofMinutes(15)).getWarning()).isEqualTo(Duration.ofSeconds(60));
        assertThat(new IdleTimeoutSettings(Duration.ofSeconds(40)).getWarning()).isEqualTo(Duration.ofSeconds(20));
    }
}
