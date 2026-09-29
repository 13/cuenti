package com.cuenti.app.support;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Vaadin installs its own {@link SecurityContextHolderStrategy} bean and makes it
 * the JVM-wide static one. With several cached Spring test contexts the context
 * created last wins, and tests in an older context see two different security
 * contexts: the filter chain uses its context's bean, code calling
 * {@link SecurityContextHolder} statically uses the other one (lost
 * {@code @WithMockUser}, authenticated API requests answered with 401). In the
 * running app there is one context and the two are the same, so re-align them
 * before each test. Runs before {@code WithSecurityContextTestExecutionListener}.
 */
public class SecurityContextStrategyTestListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        return 9_000;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        testContext.getApplicationContext()
                .getBeanProvider(SecurityContextHolderStrategy.class)
                .ifUnique(SecurityContextHolder::setContextHolderStrategy);
    }
}
