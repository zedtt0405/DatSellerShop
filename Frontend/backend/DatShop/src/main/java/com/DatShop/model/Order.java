package com.DatShop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "ShopOrder")
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
public class Order extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Size(max = 40)
    @Column(name = "voucher_code", length = 40)
    private String voucherCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", length = 20)
    private VoucherType voucherType;

    @Column(name = "voucher_discount_value", precision = 15, scale = 2)
    private BigDecimal voucherDiscountValue;

    @NotBlank
    @Size(max = 100)
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @NotBlank
    @Size(max = 20)
    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @NotBlank
    @Size(max = 100)
    @Column(name = "shipping_province", nullable = false, length = 100)
    private String shippingProvince;

    @NotBlank
    @Size(max = 100)
    @Column(name = "shipping_district", nullable = false, length = 100)
    private String shippingDistrict;

    @NotBlank
    @Size(max = 100)
    @Column(name = "shipping_ward", nullable = false, length = 100)
    private String shippingWard;

    @NotBlank
    @Size(max = 255)
    @Column(name = "shipping_address_line", nullable = false, length = 255)
    private String shippingAddressLine;

    @Size(max = 500)
    @Column(name = "customer_note", length = 500)
    private String customerNote;
}
