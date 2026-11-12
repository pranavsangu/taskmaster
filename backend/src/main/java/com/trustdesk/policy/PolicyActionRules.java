package com.trustdesk.policy;

import java.util.Optional;

/**
 * Policy rules for money and replacement actions, decided in Java from {@link PolicyFacts}.
 * The tool catalog says which tools a category may use; these rules say whether the policy
 * supports using them for this particular order on this ticket date.
 */
public final class PolicyActionRules {

    private PolicyActionRules() {
    }

    /** Why the policy does not support this action, or empty if it does (or the tool has no policy rule). */
    public static Optional<String> blockReason(String toolName, String category, PolicyFacts facts) {
        return switch (toolName) {
            // Duplicate charges are a payment problem, not a return: no return window applies.
            case "start_refund_review" -> "billing".equals(category) || facts.returnEligible()
                    ? Optional.empty()
                    : Optional.of("refund review is not supported by the return policy for this order ("
                                  + returnSummary(facts) + ")");
            case "create_replacement_order" -> facts.returnEligible() || facts.warrantyActive()
                    ? Optional.empty()
                    : Optional.of("replacement needs an open return window or active warranty ("
                                  + returnSummary(facts) + ")");
            default -> Optional.empty();
        };
    }

    private static String returnSummary(PolicyFacts facts) {
        String items = facts.items().stream()
                .filter(i -> !i.returnable())
                .map(i -> i.name() + ": " + i.notReturnableReason())
                .reduce((a, b) -> a + "; " + b)
                .map(s -> "; " + s)
                .orElse("");
        return "return window " + facts.returnWindow() + items;
    }
}