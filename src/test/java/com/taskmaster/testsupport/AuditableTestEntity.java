package com.taskmaster.testsupport;

import com.taskmaster.domain.AuditableEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;

@Entity
public class AuditableTestEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_value")
    private String value;

    protected AuditableTestEntity() {
    }

    public AuditableTestEntity(String value) {
        this.value = value;
    }
}
