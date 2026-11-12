package com.trustdesk.action;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a tool. TrustDesk has no real order, payment or carrier systems, so every tool is
 * <b>simulated</b>: it returns a reference ID as a real system would, and changes nothing else.
 * This is the single place to plug in real integrations later.
 */
@Component
public class ToolExecutor {

    public Map<String, Object> execute(ToolActionRequest action) {
        Map<String, Object> result = new LinkedHashMap<>();
        switch (action.getToolName()) {
            case "create_replacement_order" -> result.put("replacement_order_id", reference("rpl"));
            case "start_refund_review" -> result.put("refund_review_id", reference("rfr"));
            case "issue_coupon" -> result.put("coupon_code", reference("cpn").toUpperCase());
            case "open_carrier_investigation" -> result.put("investigation_id", reference("inv"));
            case "escalate_to_human" -> result.put("handoff_id", reference("hnd"));
            case "lock_account" -> result.put("lock_id", reference("lck"));
            default -> throw new IllegalStateException("No executor for tool " + action.getToolName());
        }
        result.put("simulated", true);
        return result;
    }

    private static String reference(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
