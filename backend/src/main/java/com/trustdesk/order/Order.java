package com.trustdesk.order;

import com.trustdesk.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // "order" is a reserved SQL word
public class Order {

    @Id
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDate placedAt;

    private LocalDate deliveredAt;

    private LocalDate eligibleReturnUntil;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String paymentStatus;

    private String trackingNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // required by JPA
    }

    public Order(String orderId, Customer customer, String status, LocalDate placedAt,
                 LocalDate deliveredAt, LocalDate eligibleReturnUntil, BigDecimal total,
                 String currency, String paymentStatus, String trackingNumber, List<OrderItem> items) {
        this.orderId = orderId;
        this.customer = customer;
        this.status = status;
        this.placedAt = placedAt;
        this.deliveredAt = deliveredAt;
        this.eligibleReturnUntil = eligibleReturnUntil;
        this.total = total;
        this.currency = currency;
        this.paymentStatus = paymentStatus;
        this.trackingNumber = trackingNumber;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public String getOrderId() {
        return orderId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getPlacedAt() {
        return placedAt;
    }

    public LocalDate getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDate getEligibleReturnUntil() {
        return eligibleReturnUntil;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}