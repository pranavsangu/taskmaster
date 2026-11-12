package com.trustdesk.tool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of the tool catalog (tool_actions.json): what a tool is and the rules for using it.
 * Requests to actually run a tool are a separate concept.
 */
@Entity
@Table(name = "tool_catalog")
public class ToolDefinition {

    @Id
    private String toolName;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String riskLevel;

    private boolean requiresHumanApproval;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> allowedCategories = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> requiredFields = new ArrayList<>();

    private Integer maxAmountInr;

    protected ToolDefinition() {
        // required by JPA
    }

    public ToolDefinition(String toolName, String description, String riskLevel, boolean requiresHumanApproval,
                          List<String> allowedCategories, List<String> requiredFields, Integer maxAmountInr) {
        this.toolName = toolName;
        this.description = description;
        this.riskLevel = riskLevel;
        this.requiresHumanApproval = requiresHumanApproval;
        this.allowedCategories = allowedCategories == null ? new ArrayList<>() : new ArrayList<>(allowedCategories);
        this.requiredFields = requiredFields == null ? new ArrayList<>() : new ArrayList<>(requiredFields);
        this.maxAmountInr = maxAmountInr;
    }

    public String getToolName() {
        return toolName;
    }

    public String getDescription() {
        return description;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public boolean isRequiresHumanApproval() {
        return requiresHumanApproval;
    }

    public List<String> getAllowedCategories() {
        return List.copyOf(allowedCategories);
    }

    public List<String> getRequiredFields() {
        return List.copyOf(requiredFields);
    }

    public Integer getMaxAmountInr() {
        return maxAmountInr;
    }
}