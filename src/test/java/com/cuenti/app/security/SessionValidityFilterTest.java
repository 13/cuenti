package com.cuenti.app.security;

import com.cuenti.app.model.User;
import com.cuenti.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionValidityFilterTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final SessionValidityFilter filter = new SessionValidityFilter(repository);

    private static User user(boolean enabled, String hash) {
        return User.builder().username("demo").password(hash).enabled(enabled).build();
    }

    private static MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of())));
        return session;
    }

    private void run(MockHttpSession session) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
        request.setSession(session);
        // bypass the throttle so every call re-checks
        session.removeAttribute(SessionValidityFilter.LAST_CHECK);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void validSessionSurvives() throws Exception {
        when(repository.findByUsername("demo")).thenReturn(Optional.of(user(true, "h1")));
        MockHttpSession session = session();
        run(session);
        run(session);
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void disabledAccountEndsSession() throws Exception {
        when(repository.findByUsername("demo")).thenReturn(Optional.of(user(false, "h1")));
        MockHttpSession session = session();
        run(session);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void deletedAccountEndsSession() throws Exception {
        when(repository.findByUsername("demo")).thenReturn(Optional.empty());
        MockHttpSession session = session();
        run(session);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void passwordChangedElsewhereEndsSession() throws Exception {
        when(repository.findByUsername("demo")).thenReturn(Optional.of(user(true, "h1")));
        MockHttpSession session = session();
        run(session);
        when(repository.findByUsername("demo")).thenReturn(Optional.of(user(true, "h2")));
        run(session);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void checksAreThrottled() throws Exception {
        when(repository.findByUsername("demo")).thenReturn(Optional.of(user(true, "h1")));
        MockHttpSession session = session();
        run(session);
        when(repository.findByUsername("demo")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
        request.setSession(session);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(session.isInvalid()).isFalse();
    }
}
