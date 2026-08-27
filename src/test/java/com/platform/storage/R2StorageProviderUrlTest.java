package com.platform.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The {@link ImageUrlResolver} half of {@link R2StorageProvider}: pure string work, no
 * bucket call. Covered on its own because every response DTO renders an image through it,
 * and because the http pass-through is what keeps pre-V6 rows - which hold a full URL
 * rather than a key - rendering without a data backfill.
 */
@ExtendWith(MockitoExtension.class)
class R2StorageProviderUrlTest {

    private static final String BASE = "https://pub-abc123.r2.dev";

    @Mock private S3Client r2Client;
    @Mock private S3Presigner r2Presigner;

    private R2StorageProvider provider;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.getR2().setPublicUrlBase(BASE);
        provider = new R2StorageProvider(properties, r2Client, r2Presigner);
    }

    @Test
    void toPublicUrl_joinsTheBaseAndTheKey() {
        assertEquals(BASE + "/business/abc/logo/x.png",
                provider.toPublicUrl("business/abc/logo/x.png"));
    }

    @Test
    void toPublicUrl_passesThroughLegacyAbsoluteUrls() {
        // Rows written before V6 hold a full URL in the same column. Prefixing the base
        // onto one of those would produce a broken, doubled-up URL.
        String legacy = "https://old-bucket.supabase.co/storage/v1/object/public/logo.png";
        assertEquals(legacy, provider.toPublicUrl(legacy));

        String insecureLegacy = "http://old-bucket.example.com/logo.png";
        assertEquals(insecureLegacy, provider.toPublicUrl(insecureLegacy));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void toPublicUrl_returnsNullForBlank(String key) {
        assertNull(provider.toPublicUrl(key));
    }

    @Test
    void toPublicUrl_returnsNullForNull() {
        assertNull(provider.toPublicUrl(null));
    }

    @Test
    void toPublicUrl_neverTouchesTheBucket() {
        provider.toPublicUrl("business/abc/logo/x.png");
        provider.toPublicUrl(null);

        verifyNoInteractions(r2Client);
        verifyNoInteractions(r2Presigner);
    }
}
