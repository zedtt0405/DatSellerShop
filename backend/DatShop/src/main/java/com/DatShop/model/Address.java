package com.DatShop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
public class Address extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Size(max = 100)
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @NotBlank
    @Size(max = 20)
    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String province;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String district;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String ward;

    @NotBlank
    @Size(max = 255)
    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @NotNull
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}
