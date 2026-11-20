package kr.budget.claims;

public enum ApprovalRoute {
    MANAGER, MANAGER_FINANCE;

    public static ApprovalRoute forSupport(long supported) {
        return supported >= 300000 ? MANAGER_FINANCE : MANAGER;
    }
}
