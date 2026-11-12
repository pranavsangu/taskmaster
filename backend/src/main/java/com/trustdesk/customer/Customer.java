package com.trustdesk.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private String customerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private LocalDate createdAt;

    private boolean verified;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tags = new ArrayList<>();

    protected Customer() {
        // required by JPA
    }

    public Customer(String customerId, String name, String email, String tier, String country,
                    LocalDate createdAt, boolean verified, List<String> tags) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.country = country;
        this.createdAt = createdAt;
        this.verified = verified;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getTier() {
        return tier;
    }

    public String getCountry() {
        return country;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }
}






