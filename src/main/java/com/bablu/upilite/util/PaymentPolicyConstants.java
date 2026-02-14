package com.bablu.upilite.util;

import java.math.BigDecimal;

public final class PaymentPolicyConstants {

    private PaymentPolicyConstants() {
    }

    public static final BigDecimal MAX_WALLET_BALANCE = BigDecimal.valueOf(2000);
    public static final BigDecimal PIN_REQUIRED_THRESHOLD = BigDecimal.valueOf(500);

    public static final BigDecimal SCAM_HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(1000);
    public static final BigDecimal SCAM_FIRST_TIME_BENEFICIARY_AMOUNT_THRESHOLD = BigDecimal.valueOf(200);
    public static final int SCAM_VELOCITY_WINDOW_MINUTES = 10;
    public static final long SCAM_VELOCITY_THRESHOLD = 3;
    public static final int SCAM_CHALLENGE_THRESHOLD_SCORE = 50;
    public static final int SCAM_BLOCK_THRESHOLD_SCORE = 80;
}
