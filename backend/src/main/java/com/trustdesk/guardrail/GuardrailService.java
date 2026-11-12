package com.trustdesk.guardrail;

import com.trustdesk.kb.KbDocument;
import com.trustdesk.ticket.Ticket;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic guardrails, written in Java so that no prompt or document can switch them off.
 * <ol>
 *   <li><b>Ticket check</b> (before the AI): prompt injection, requests for secrets, requests to
 *       skip identity verification. Callers force escalation and allow only escalate_to_human.</li>
 *   <li><b>Document check</b> (after retrieval): documents containing instructions addressed to the
 *       assistant are removed before the AI sees them. Quoted text is ignored, so a policy that
 *       <i>quotes</i> attack examples (KB-SECURITY-001) is not mistaken for an attack.</li>
 *   <li><b>Draft check</b> (after the AI): leaked secrets or prompt text, claims that an action is
 *       already done, mentions of actions that validation removed, and fixable placeholders.</li>
 * </ol>
 * Keyword rules can miss rephrased attacks; they are one layer, next to the prompt rules, citation
 * and action validation, and the human approval gate.
 */
@Component
public class GuardrailService {

    static final int MAX_DETAIL = 80;

    private record Rule(String type, Pattern pattern) {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /** Checked against the customer's subject and body. */
    private static final List<Rule> TICKET_RULES = List.of(
            new Rule(GuardrailFinding.PROMPT_INJECTION, p("\\b(ignore|disregard|forget)\\s+(all\\s+|any\\s+|the\\s+|your\\s+|previous\\s+|prior\\s+|above\\s+|support\\s+)*(instructions|rules|polic(y|ies)|guidelines)")),
            new Rule(GuardrailFinding.PROMPT_INJECTION, p("\\bsystem\\s+override\\b|\\bdeveloper\\s+mode\\b|\\byou\\s+are\\s+now\\b|\\bnew\\s+instructions\\b")),
            new Rule(GuardrailFinding.PROMPT_INJECTION, p("\\b(do\\s+not|don'?t|never)\\s+(mention|tell|reveal|inform|show)\\b.{0,40}\\b(human|reviewer|agent|manager|anyone)")),
            new Rule(GuardrailFinding.PROMPT_INJECTION, p("\\bhide\\b.{0,40}\\b(human|reviewer|agent|manager)")),
            new Rule(GuardrailFinding.SECRET_REQUEST, p("\\b(system|hidden|internal|secret)\\s+(prompt|instructions)\\b")),
            new Rule(GuardrailFinding.SECRET_REQUEST, p("\\bapi[\\s_-]?keys?\\b|\\binternal\\s+notes\\b|\\baccess\\s+tokens?\\b|\\bcredentials\\b")),
            new Rule(GuardrailFinding.VERIFICATION_BYPASS, p("\\b(ignore|skip|bypass|without|no\\s+need\\s+for)\\s+(the\\s+|any\\s+)?(identity|id|verification)(\\s+(checks?|verification))?\\b")));

    /** Checked against each sentence of a document, after quoted text is removed. */
    private static final List<Pattern> DOCUMENT_RULES = List.of(
            // A sentence that starts by ordering the reader to drop its rules.
            p("^(ignore|disregard|forget|override)\\b"),
            p("\\battention\\s+(support\\s+)?(assistant|ai|agent|bot)\\b"),
            p("\\b(approve|issue)\\s+(every|all)\\b"),
            p("\\breveal\\s+(all\\s+)?(hidden|internal|system)\\b"),
            p("\\bdo\\s+not\\s+mention\\s+this\\b|\\bif\\s+a\\s+human\\s+reviews\\b|\\byou\\s+are\\s+now\\b"));

    /** Text a reply must never contain: credentials or fragments of our own prompts. */
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            p("\\bgsk_[A-Za-z0-9]{8,}"),
            p("\\bsk-[A-Za-z0-9_-]{16,}"),
            p("\\bbearer\\s+[A-Za-z0-9._-]{12,}"),
            p("component of TrustDesk|Everything inside <ticket>|Security rules:|Respond with ONLY a JSON object"));

    /** Wording that presents an action needing a human as already done. */
    private static final Pattern ACTION_CLAIM = p(
            "\\b(i|we)\\s*(['’]ve|\\s+have)\\s+(already\\s+)?(started|initiated|opened|issued|created|processed|approved|"
            + "refunded|locked|changed|updated|submitted|placed|arranged|credited)\\b"
            + "|\\b(has|have)\\s+(already\\s+)?been\\s+(refunded|processed|issued|approved|created|locked|changed|credited|initiated|started|opened)\\b");

    /** How a reply would talk about each tool; used to spot mentions of actions validation removed. */
    private static final Map<String, Pattern> TOOL_WORDING = Map.of(
            "start_refund_review", p("\\brefund\\s+review\\b"),
            "create_replacement_order", p("\\breplacement\\b"),
            "issue_coupon", p("\\bcoupon\\b"),
            "lock_account", p("\\block(ed|ing)?\\b.{0,20}\\baccount\\b"),
            "open_carrier_investigation", p("\\b(carrier\\s+)?investigation\\b"));

    private static final Pattern PLACEHOLDER = p("\\[(your\\s+name|name|agent\\s+name|agent|company(\\s+name)?|signature)\\]");
    static final String SIGNATURE = "TrustDesk Support";

    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|“[^”]*”");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("[.!?:;\\n]+");

    /** Checks the customer's own text. */
    public List<GuardrailFinding> checkTicket(Ticket ticket) {
        String text = ticket.getSubject() + "\n" + ticket.getBody();
        List<GuardrailFinding> findings = new ArrayList<>();
        for (Rule rule : TICKET_RULES) {
            Matcher m = rule.pattern().matcher(text);
            boolean alreadyFound = findings.stream().anyMatch(f -> f.type().equals(rule.type()));
            if (!alreadyFound && m.find()) {
                findings.add(new GuardrailFinding(rule.type(), "ticket", shorten(m.group())));
            }
        }
        return findings;
    }

    /** Result of {@link #screenDocuments}: documents safe to show the AI, and why others were removed. */
    public record Screened(List<KbDocument> kept, List<GuardrailFinding> findings) {
    }

    public Screened screenDocuments(List<KbDocument> documents) {
        List<KbDocument> kept = new ArrayList<>();
        List<GuardrailFinding> findings = new ArrayList<>();
        for (KbDocument doc : documents) {
            String instruction = instructionInDocument(doc.getContent());
            if (instruction == null) {
                kept.add(doc);
            } else {
                findings.add(new GuardrailFinding(GuardrailFinding.UNTRUSTED_DOCUMENT, doc.getDocId(),
                        "removed before the AI saw it: \"" + shorten(instruction) + "\""));
            }
        }
        return new Screened(kept, findings);
    }

    /** The first sentence that gives orders to the assistant, or null if there is none. */
    static String instructionInDocument(String content) {
        String withoutQuotes = QUOTED.matcher(content).replaceAll(" ");
        for (String sentence : SENTENCE_BREAK.split(withoutQuotes)) {
            String s = sentence.replaceAll("[#*>\\-]+", " ").strip();
            for (Pattern rule : DOCUMENT_RULES) {
                if (rule.matcher(s).find()) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Result of {@link #checkDraft}: the body (placeholders fixed) and what was found. */
    public record DraftCheck(String body, List<GuardrailFinding> findings) {

        public boolean has(String type) {
            return findings.stream().anyMatch(f -> f.type().equals(type));
        }
    }

    /**
     * @param removedTools tools the AI suggested but validation removed; the reply should not offer them
     */
    public DraftCheck checkDraft(String body, List<String> removedTools) {
        List<GuardrailFinding> findings = new ArrayList<>();
        for (Pattern secret : SECRET_PATTERNS) {
            if (secret.matcher(body).find()) {
                // Never copy the secret itself into the stored findings.
                findings.add(new GuardrailFinding(GuardrailFinding.SECRET_LEAK, "draft",
                        "reply contained a credential or internal prompt text"));
                break;
            }
        }
        Matcher claim = ACTION_CLAIM.matcher(body);
        if (claim.find()) {
            findings.add(new GuardrailFinding(GuardrailFinding.ACTION_CLAIM, "draft",
                    "presents an action as already done: \"" + shorten(claim.group()) + "\""));
        }
        for (String tool : removedTools.stream().distinct().toList()) {
            Pattern wording = TOOL_WORDING.get(tool);
            if (wording != null) {
                Matcher m = wording.matcher(body);
                if (m.find()) {
                    findings.add(new GuardrailFinding(GuardrailFinding.REMOVED_ACTION_MENTION, "draft",
                            "mentions \"" + m.group() + "\" although " + tool + " was removed"));
                }
            }
        }
        String fixed = body;
        Matcher placeholder = PLACEHOLDER.matcher(body);
        if (placeholder.find()) {
            fixed = PLACEHOLDER.matcher(body).replaceAll(SIGNATURE);
            findings.add(new GuardrailFinding(GuardrailFinding.PLACEHOLDER_FIXED, "draft",
                    "replaced \"" + placeholder.group() + "\" with \"" + SIGNATURE + "\""));
        }
        return new DraftCheck(fixed, findings);
    }

    private static String shorten(String text) {
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_DETAIL ? oneLine : oneLine.substring(0, MAX_DETAIL) + "…";
    }
}