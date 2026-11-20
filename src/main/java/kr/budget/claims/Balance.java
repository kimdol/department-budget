package kr.budget.claims;

public record Balance(long allocation, long committed, long spent) {
    public long getAvailable() {
        return allocation - committed - spent;
    }
}
