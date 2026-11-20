package kr.budget.claims;

public record Claim(String claimId, String title, String category, String status, long estimated,
                    int days, String formula, long policyGeneration, long approved, Long actual, long settled,
                    int reviewRound, String reviewNote) {}
