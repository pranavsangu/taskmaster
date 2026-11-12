package com.trustdesk.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, offline stand-in for a real model: simple keyword rules over the ticket text
 * for triage, and fixed templates for drafts.
 * Used by default and in tests, so results are repeatable and no API key is needed.
 * It is not "AI" and makes no claim to be accurate on unseen tickets.
 */
@Component
@ConditionalOnProperty(name = "trustdesk.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    private static final List<String> SAFETY = List.of(
            "swelling", "swollen", "overheat", "burning", "smoke", "fire", "electric shock", "exposed wire");
    private static final List<String> UNSAFE_REQUEST = List.of(
            "ignore all", "ignore previous", "ignore the", "ignore identity", "skip identity", "system override",
            "system prompt", "api key", "internal notes", "hidden instructions", "do not mention");
    private static final List<String> ACCOUNT_SECURITY = List.of(
            "account email", "password", "login", "log in", "sign in", "identity", "hacked",
            "system prompt", "api key", "internal notes");
    private static final List<String> BILLING = List.of(
            "charge", "charged", "payment", "invoice", "billing", "debited");
    private static final List<String> WARRANTY = List.of(
            "warranty", "battery", "stopped working", "defect", "overheat", "swelling");
    private static final List<String> REFUND = List.of(
            "refund", "return", "damaged", "cracked", "broken", "replacement", "money back");
    private static final List<String> SHIPPING = List.of(
            "tracking", "not moved", "no movement", "delivery", "shipping", "courier", "lost package", "in transit");
    private static final List<String> HIGH_PRIORITY = List.of(
            "two charges", "double charge", "charged twice", "fraud", "password", "account email",
            "api key", "system prompt", "travel", "urgent", "asap", "quickly", "immediately");
    private static final List<String> LOW_PRIORITY = List.of("changed my mind", "no longer need");
    private static final List<String> FRUSTRATED = List.of(
            "damaged", "cracked", "broken", "no movement", "not moved", "two charges", "fix this", "again");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        return "keyword-rules-v1";
    }

    @Override
    public String promptVersion() {
        return "mock-templates-v1";
    }

    @Override
    public TriageSuggestion triage(TriageInput input) {
        String text = (input.subject() + " " + input.body()).toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();

        String category = firstMatchingCategory(text, reasons);

        String priority = "medium";
        if (containsAny(text, SAFETY)) {
            priority = "urgent";
            reasons.add("safety issue");
        } else if (containsAny(text, HIGH_PRIORITY)) {
            priority = "high";
        } else if (containsAny(text, LOW_PRIORITY)) {
            priority = "low";
        }

        String sentiment = containsAny(text, SAFETY) ? "worried"
                : containsAny(text, FRUSTRATED) ? "frustrated"
                : "neutral";

        boolean escalate = containsAny(text, SAFETY) || containsAny(text, UNSAFE_REQUEST);
        if (containsAny(text, UNSAFE_REQUEST)) {
            reasons.add("request to bypass policy or reveal internal information");
        }

        String reason = "Mock triage (keyword rules): " + (reasons.isEmpty() ? "no specific keywords" : String.join(", ", reasons)) + ".";
        return new TriageSuggestion(category, priority, sentiment, escalate, reason);
    }

    /**
     * Template reply per category. Cites the top-ranked policy, and recommends the obvious tool
     * for the category if the catalog offers it. Escalation simply follows the triage.
     */
    @Override
    public DraftSuggestion draft(DraftInput input) {
        String text = (input.subject() + " " + input.body()).toLowerCase(Locale.ROOT);
        String greeting = "Hi " + (input.customerFirstName() == null ? "there" : input.customerFirstName()) + ",\n\n";
        String closing = "\n\nBest regards,\nTrustDesk Support";

        if (input.triageEscalate()) {
            String reason = containsAny(text, SAFETY) ? "Possible product safety issue."
                    : "Request needs review by a human specialist.";
            String body = containsAny(text, SAFETY)
                    ? "Thank you for letting us know. For your safety, please stop using the device. "
                      + "We have passed your case to a specialist, who will contact you shortly."
                    : "Thank you for contacting us. We have passed your request to a specialist, "
                      + "who will review it and get back to you.";
            return new DraftSuggestion(greeting + body + closing, topPolicy(input),
                    List.of(new DraftSuggestion.ActionSuggestion("escalate_to_human", reason)), true, reason);
        }

        String body;
        List<DraftSuggestion.ActionSuggestion> actions = new ArrayList<>();
        switch (input.category()) {
            case "refund" -> {
                if (containsAny(text, List.of("damaged", "cracked", "broken", "defect"))) {
                    body = "I am sorry your item arrived damaged. Under our return policy we can arrange a "
                           + "replacement once a team member approves it. A photo of the damage will help.";
                    addIfOffered(input, actions, "create_replacement_order", "Damaged item reported.");
                } else if (input.policyFacts().returnEligible()) {
                    body = "Thank you for your message. Your order is within the return window, so we can start "
                           + "a refund review once a team member approves it.";
                    addIfOffered(input, actions, "start_refund_review", "Return requested within the window.");
                } else {
                    body = "Thank you for your message. Unfortunately this purchase is not eligible for a refund "
                           + "under our refund policy (for example, software licenses and final-sale items cannot "
                           + "be refunded after purchase).";
                }
            }
            case "shipping" -> {
                body = "I am sorry your package has not moved. The next step is a carrier investigation, "
                       + "and we will update you as soon as the carrier responds.";
                addIfOffered(input, actions, "open_carrier_investigation", "Tracking has not moved.");
            }
            case "billing" -> {
                body = "I am sorry about the duplicate charge. We will start a billing review; we cannot confirm "
                       + "a refund until our payments team has checked the transaction.";
                addIfOffered(input, actions, "start_refund_review", "Duplicate charge reported.");
            }
            default -> body = "Thank you for contacting us. A team member will review your request.";
        }
        return new DraftSuggestion(greeting + body + closing, topPolicy(input), actions, false, null);
    }

    private static List<String> topPolicy(DraftInput input) {
        return input.policies().isEmpty() ? List.of() : List.of(input.policies().get(0).docId());
    }

    private static void addIfOffered(DraftInput input, List<DraftSuggestion.ActionSuggestion> actions,
                                     String toolName, String reason) {
        if (input.tools().stream().anyMatch(t -> t.toolName().equals(toolName))) {
            actions.add(new DraftSuggestion.ActionSuggestion(toolName, reason));
        }
    }

    private static String firstMatchingCategory(String text, List<String> reasons) {
        // Order matters: the most specific / sensitive categories are checked first.
        if (containsAny(text, ACCOUNT_SECURITY)) {
            reasons.add("account or security keywords");
            return "account_security";
        }
        if (containsAny(text, BILLING)) {
            reasons.add("billing keywords");
            return "billing";
        }
        if (containsAny(text, WARRANTY)) {
            reasons.add("warranty keywords");
            return "warranty";
        }
        if (containsAny(text, REFUND)) {
            reasons.add("refund or damage keywords");
            return "refund";
        }
        if (containsAny(text, SHIPPING)) {
            reasons.add("shipping keywords");
            return "shipping";
        }
        return "general";
    }

    private static boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }
}