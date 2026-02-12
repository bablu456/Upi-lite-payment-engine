package com.bablu.upilite.util;

import java.math.BigDecimal;

public final class PaymentPolicyConstants {

    private PaymentPolicyConstants() {
    }

    public static final BigDecimal MAX_WALLET_BALANCE = BigDecimal.valueOf(2000);
    public static final BigDecimal PIN_REQUIRED_THRESHOLD = BigDecimal.valueOf(500);
}
