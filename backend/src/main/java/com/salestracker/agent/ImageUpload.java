package com.salestracker.agent;

import com.salestracker.auth.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Checks an uploaded screenshot. The type comes from the file's own first bytes, not from what the browser claims. */
final class ImageUpload {
    private ImageUpload() {}

    static final int MAX_BYTES = 6 * 1024 * 1024;

    static AiClient.Image read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No image was sent");
        if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "That image is too large");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The image could not be read");
        }
        String type = sniff(bytes);
        if (type == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG or WebP images can be read");
        return new AiClient.Image(type, bytes);
    }

    /** image/jpeg, image/png or image/webp by magic number; null for anything else. */
    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
