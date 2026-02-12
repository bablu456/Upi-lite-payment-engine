package com.bablu.upilite.service;

import com.bablu.upilite.dto.QrCodeResponseDto;
import com.bablu.upilite.entity.User;
import com.bablu.upilite.exception.InvalidTransferRequestException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrService {

    public QrCodeResponseDto generateUpiQr(User user, BigDecimal amount, String note) {
        if (user == null || user.getUpiId() == null || user.getUpiId().trim().isEmpty()) {
            throw new InvalidTransferRequestException("UPI ID is not configured for this user.");
        }

        if (amount != null && amount.signum() <= 0) {
            throw new InvalidTransferRequestException("QR amount must be greater than 0.");
        }

        String payload = buildUpiPayload(user, amount, note);
        String dataUri = toQrDataUri(payload);

        return QrCodeResponseDto.builder()
                .upiId(user.getUpiId())
                .qrPayload(payload)
                .qrImageDataUri(dataUri)
                .build();
    }

    private String buildUpiPayload(User user, BigDecimal amount, String note) {
        StringBuilder payload = new StringBuilder("upi://pay");
        payload.append("?pa=").append(urlEncode(user.getUpiId()));
        payload.append("&pn=").append(urlEncode(user.getName() == null ? "UPI Lite User" : user.getName()));
        payload.append("&cu=INR");

        if (amount != null && amount.signum() > 0) {
            payload.append("&am=").append(amount.stripTrailingZeros().toPlainString());
        }

        if (note != null && !note.trim().isEmpty()) {
            payload.append("&tn=").append(urlEncode(note.trim()));
        }

        return payload.toString();
    }

    private String toQrDataUri(String payload) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());

            BitMatrix bitMatrix = qrCodeWriter.encode(payload, BarcodeFormat.QR_CODE, 300, 300, hints);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", output);

            String base64 = Base64.getEncoder().encodeToString(output.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate QR code.", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
