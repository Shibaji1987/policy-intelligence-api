package com.acme.policyintelligence.security;

public final class GovernanceContextHolder {

    private static final ThreadLocal<GovernanceContext> HOLDER =
            ThreadLocal.withInitial(GovernanceContext::anonymous);

    private GovernanceContextHolder() {
    }

    public static GovernanceContext current() {
        return HOLDER.get();
    }

    public static void set(GovernanceContext context) {
        HOLDER.set(context == null ? GovernanceContext.anonymous() : context);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
