package com.salestracker.agent;

import com.salestracker.auth.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class ImageUploadTest {
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};

    private static MockMultipartFile file(byte[] bytes, String claimedType) {
        return new MockMultipartFile("image", "shot", claimedType, bytes);
    }

    @Test
    void theTypeComesFromTheBytesNotTheClaim() {
        assertEquals("image/jpeg", ImageUpload.read(file(JPEG, "text/plain")).mimeType());
        assertEquals("image/png", ImageUpload.read(file(PNG, "image/jpeg")).mimeType());
        assertEquals("image/webp", ImageUpload.read(file(WEBP, "application/octet-stream")).mimeType());
        assertArrayEquals(JPEG, ImageUpload.read(file(JPEG, "image/jpeg")).bytes());
    }

    @Test
    void otherFilesAreRefused() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        ApiException e = assertThrows(ApiException.class, () -> ImageUpload.read(file(html, "image/png")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertThrows(ApiException.class, () -> ImageUpload.read(file(new byte[0], "image/png")));
        assertThrows(ApiException.class, () -> ImageUpload.read(null));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> ImageUpload.read(file(new byte[]{(byte) 0xFF, (byte) 0xD8}, "image/jpeg"))).getStatus()); // too short to be a JPEG
    }

    @Test
    void anOversizedImageIsRefused() {
        byte[] big = new byte[ImageUpload.MAX_BYTES + 1];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(ApiException.class, () -> ImageUpload.read(file(big, "image/jpeg"))).getStatus());
    }
}
