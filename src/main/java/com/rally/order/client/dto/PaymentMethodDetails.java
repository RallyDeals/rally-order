package com.rally.order.client.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentMethodDetails {
    private UUID id;
    private UUID userId;
    private String type;
    private boolean isDefault;
    private String cardLast4;
    private String cardBrand;
    private String cardExpMonth;
    private String cardExpYear;
    private String cardFingerprint;
}
