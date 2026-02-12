package com.bablu.upilite.service;

import com.bablu.upilite.entity.OtpPurpose;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OtpNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OtpNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.otp.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.otp.email.from:no-reply@upilite.local}")
    private String fromEmail;

    public void sendOtpEmail(String recipientEmail, String recipientName, String otp, OtpPurpose purpose) {
        String safeName = StringUtils.hasText(recipientName) ? recipientName.trim() : "User";
        String subject = purpose == OtpPurpose.LOGIN
                ? "Your UPI Lite Login OTP"
                : "Your UPI Lite Password Reset OTP";
        String body = buildBody(safeName, otp, purpose);

        if (!emailEnabled) {
            LOGGER.info("OTP generated in local mode for {} [{}]: {}", recipientEmail, purpose, otp);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (StringUtils.hasText(fromEmail)) {
                message.setFrom(fromEmail.trim());
            }
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            LOGGER.info("OTP email sent successfully to {} [{}]", recipientEmail, purpose);
        } catch (Exception exception) {
            LOGGER.error("OTP email send failed for {} [{}]: {}", recipientEmail, purpose, exception.getMessage(),
                    exception);
            throw new InvalidTransferRequestException("Unable to send OTP right now. Please try again.");
        }
    }

    private String buildBody(String name, String otp, OtpPurpose purpose) {
        String action = purpose == OtpPurpose.LOGIN ? "login" : "password reset";
        return "Hello " + name + ",\n\n"
                + "Your OTP for UPI Lite " + action + " is: " + otp + "\n"
                + "This OTP is valid for 5 minutes.\n\n"
                + "If you did not request this, please ignore this email.\n\n"
                + "UPI Lite Team";
    }
}
