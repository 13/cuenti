package com.cuenti.app.views.components;

import com.cuenti.app.security.IdleTimeoutSettings;
import com.cuenti.app.views.ThemePreference;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Signs the user out after {@link IdleTimeoutSettings#getTimeout()} without
 * interaction. The browser tracks clicks, keys, scrolling and touches, shares
 * the last-activity time across tabs via localStorage (working in one tab
 * keeps the others signed in), shows a warning shortly before the deadline and
 * then hides the page and redirects to {@code login?timeout}.
 * <p>
 * Local activity is reported to the server at most every 30 s so the
 * server-side backstop ({@code IdleSessionFilter}) sees it too.
 */
@Tag("cuenti-idle-guard")
public class IdleLogoutGuard extends Component {

    static final String LOGIN_TIMEOUT_URL = "login?timeout";

    private static final String SCRIPT = """
            const el = this, timeout = $0, warn = $1, KEY = 'cuenti-last-activity', OUT = 'cuenti-idle-logout';
            if (el.__idleCleanup) el.__idleCleanup();
            const get = k => { try { return +localStorage.getItem(k) || 0; } catch (e) { return 0; } };
            const set = (k, v) => { try { localStorage.setItem(k, String(v)); } catch (e) {} };
            const read = () => get(KEY);
            const write = t => set(KEY, t);
            let last = Date.now(), lastPing = last, warned = false, done = false, timer;
            write(last);
            const idle = () => Date.now() - Math.max(last, read());
            const leave = () => {
              done = true;
              cleanup();
              document.body.style.visibility = 'hidden';
            };
            const redirect = () => location.assign(new URL($2, document.baseURI).href);
            const logout = () => {
              leave();
              // another tab already ended the shared session: just follow it
              if (Date.now() - get(OUT) < 10000) { redirect(); return; }
              set(OUT, Date.now());
              try { el.$server.logout(); } catch (e) {}
              // fallback when the server round trip fails
              setTimeout(redirect, 1500);
            };
            const check = () => {
              if (done) return;
              if (!el.isConnected) { cleanup(); return; }
              const i = idle();
              if (i >= timeout) logout();
              else if (i >= timeout - warn) { if (!warned) { warned = true; el.$server.warn(); } }
              else if (warned) { warned = false; lastPing = Date.now(); el.$server.active(); }
            };
            const activity = () => {
              if (done) return;
              if (idle() >= timeout) { logout(); return; }
              const now = Date.now();
              if (now - last < 1000) return;
              last = now;
              write(now);
              if (warned || now - lastPing > 30000) { warned = false; lastPing = now; el.$server.active(); }
            };
            const events = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll'];
            const onStorage = e => {
              if (e.key === KEY) check();
              else if (e.key === OUT && e.newValue && !done) { leave(); redirect(); }
            };
            function cleanup() {
              events.forEach(t => document.removeEventListener(t, activity, true));
              window.removeEventListener('storage', onStorage);
              document.removeEventListener('visibilitychange', check);
              clearInterval(timer);
              delete el.__idleCleanup;
            }
            events.forEach(t => document.addEventListener(t, activity, {capture: true, passive: true}));
            window.addEventListener('storage', onStorage);
            document.addEventListener('visibilitychange', check);
            timer = setInterval(check, 5000);
            el.__idleCleanup = cleanup;
            """;

    private final IdleTimeoutSettings settings;
    private Dialog warning;

    public IdleLogoutGuard(IdleTimeoutSettings settings) {
        this.settings = settings;
        getStyle().set("display", "none");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (settings.isEnabled()) {
            // the redirect may happen without a server round trip; keep the login page in the user's language
            if (attachEvent.getUI().getLocale() != null) {
                ThemePreference.persistLocaleCookie(attachEvent.getUI(), attachEvent.getUI().getLocale().toLanguageTag());
            }
            getElement().executeJs(SCRIPT, settings.getTimeout().toMillis(),
                    settings.getWarning().toMillis(), LOGIN_TIMEOUT_URL);
        }
    }

    /** Called shortly before the deadline. */
    @ClientCallable
    void warn() {
        if (warning != null && warning.isOpened()) {
            return;
        }
        warning = new Dialog();
        warning.setHeaderTitle(getTranslation("idle.warning.title"));
        warning.add(new Paragraph(getTranslation("idle.warning.message",
                Math.max(1, settings.getWarning().toSeconds()))));
        warning.setCloseOnOutsideClick(false);

        // The click itself counts as activity in the browser; this closes the dialog
        Button stay = new Button(getTranslation("idle.warning.stay"), e -> active());
        stay.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button signOut = new Button(getTranslation("idle.warning.logout"), e -> logout());
        warning.getFooter().add(signOut, stay);
        warning.addDialogCloseActionListener(e -> active());
        warning.open();
        stay.focus();
    }

    /** Called when the user was active; the server round trip itself resets the server-side idle clock. */
    @ClientCallable
    void active() {
        if (warning != null) {
            warning.close();
            warning = null;
        }
    }

    @ClientCallable
    void logout() {
        getUI().ifPresent(ui -> ui.getPage().setLocation(LOGIN_TIMEOUT_URL));
        SecurityContextHolder.clearContext();
        VaadinSession session = VaadinSession.getCurrent();
        WrappedSession wrapped = session != null ? session.getSession() : null;
        if (wrapped != null) {
            wrapped.invalidate();
        }
    }
}
