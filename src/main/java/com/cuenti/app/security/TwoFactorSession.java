package com.cuenti.app.security;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

/**
 * Records in the HTTP session that its user passed the second factor. A new
 * sign-in gets a new session (session fixation protection), so the mark never
 * carries over to the next sign-in.
 */
public final class TwoFactorSession {

    static final String VERIFIED = TwoFactorSession.class.getName() + ".verified";

    private TwoFactorSession() {}

    public static boolean isVerified(String username) {
        WrappedSession session = session();
        return session != null && username.equals(session.getAttribute(VERIFIED));
    }

    public static void markVerified(String username) {
        WrappedSession session = session();
        if (session != null) {
            session.setAttribute(VERIFIED, username);
        }
    }

    private static WrappedSession session() {
        VaadinSession vaadin = VaadinSession.getCurrent();
        return vaadin != null ? vaadin.getSession() : null;
    }
}
