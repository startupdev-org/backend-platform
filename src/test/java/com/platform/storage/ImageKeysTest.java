package com.platform.storage;

import com.platform.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link ImageKeys#requirePrefix} is the tenant boundary of the two-step upload flow: it is
 * the only thing stopping an attach request from pointing at another business's object.
 * It is a pure function guarding a cross-tenant hole, so it gets the most attention here.
 */
class ImageKeysTest {

    private static final UUID BUSINESS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BUSINESS = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EMPLOYEE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    // ── requirePrefix: the tenant boundary ────────────────────────────────────

    @Test
    void requirePrefix_acceptsAKeyThisBusinessAndSlotWouldProduce() {
        String prefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);
        String key = ImageKeys.generate(prefix, "image/png");

        assertDoesNotThrow(() -> ImageKeys.requirePrefix(key, prefix));
    }

    @Test
    void requirePrefix_rejectsAnotherBusinessesKey() {
        String mine = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);
        String theirKey = ImageKeys.generate(
                ImageKeys.businessPrefix(OTHER_BUSINESS, ImageTarget.LOGO), "image/png");

        assertThrows(BadRequestException.class, () -> ImageKeys.requirePrefix(theirKey, mine));
    }

    @Test
    void requirePrefix_rejectsAKeyFromAnotherSlotOfTheSameBusiness() {
        String logoPrefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);
        String coverKey = ImageKeys.generate(
                ImageKeys.businessPrefix(BUSINESS, ImageTarget.COVER), "image/png");

        assertThrows(BadRequestException.class, () -> ImageKeys.requirePrefix(coverKey, logoPrefix));
    }

    @Test
    void requirePrefix_rejectsTheBarePrefixWithNoFileName() {
        // startsWith() alone would accept the prefix itself, which names a folder, not an object.
        String prefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);

        assertThrows(BadRequestException.class, () -> ImageKeys.requirePrefix(prefix, prefix));
    }

    @Test
    void requirePrefix_rejectsNull() {
        String prefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);

        assertThrows(BadRequestException.class, () -> ImageKeys.requirePrefix(null, prefix));
    }

    @Test
    void requirePrefix_rejectsAKeyThatOnlyLooksLikeThePrefix() {
        // "business/{id}/logo-evil/..." starts with "business/{id}/logo" but not with the
        // trailing slash the real prefix ends in, which is why the prefix carries one.
        String prefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);
        String lookalike = "business/" + BUSINESS + "/logo-evil/x.png";

        assertThrows(BadRequestException.class, () -> ImageKeys.requirePrefix(lookalike, prefix));
    }

    @Test
    void requirePrefix_rejectsPathTraversalOutOfThePrefix() {
        String prefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);

        assertThrows(BadRequestException.class,
                () -> ImageKeys.requirePrefix("../../etc/passwd", prefix));
        assertThrows(BadRequestException.class,
                () -> ImageKeys.requirePrefix("business/" + OTHER_BUSINESS + "/logo/../../x.png", prefix));
    }

    @Test
    void requirePrefix_acceptsAnEmployeePhotoKeyForThatEmployeeOnly() {
        String prefix = ImageKeys.employeePhotoPrefix(BUSINESS, EMPLOYEE);
        String key = ImageKeys.generate(prefix, "image/webp");

        assertDoesNotThrow(() -> ImageKeys.requirePrefix(key, prefix));

        String otherEmployeePrefix = ImageKeys.employeePhotoPrefix(BUSINESS, UUID.randomUUID());
        assertThrows(BadRequestException.class, () -> ImageKeys.requirePrefix(key, otherEmployeePrefix));
    }

    // ── Prefix shape ──────────────────────────────────────────────────────────

    @Test
    void businessPrefix_isNamespacedPerBusinessAndSlot() {
        assertEquals("business/" + BUSINESS + "/logo/",
                ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO));
        assertEquals("business/" + BUSINESS + "/cover/",
                ImageKeys.businessPrefix(BUSINESS, ImageTarget.COVER));
    }

    @Test
    void employeePhotoPrefix_nestsUnderTheOwningBusiness() {
        assertTrue(ImageKeys.employeePhotoPrefix(BUSINESS, EMPLOYEE)
                .startsWith("business/" + BUSINESS + "/employee/" + EMPLOYEE + "/"));
    }

    // ── Key generation ────────────────────────────────────────────────────────

    @Test
    void generate_putsARandomNameUnderThePrefixWithTheRightExtension() {
        String prefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);

        String key = ImageKeys.generate(prefix, "image/jpeg");

        assertTrue(key.startsWith(prefix), key);
        assertTrue(key.endsWith(".jpg"), key);
        // The name is a UUID, so the client cannot influence or guess it.
        String name = key.substring(prefix.length(), key.length() - ".jpg".length());
        assertDoesNotThrow(() -> UUID.fromString(name));
    }

    @Test
    void generate_neverRepeatsAKey() {
        String prefix = ImageKeys.businessPrefix(BUSINESS, ImageTarget.LOGO);

        assertFalse(ImageKeys.generate(prefix, "image/png")
                .equals(ImageKeys.generate(prefix, "image/png")));
    }

    // ── Content types ─────────────────────────────────────────────────────────

    @Test
    void extensionFor_mapsEachAcceptedType() {
        assertEquals("jpg", ImageKeys.extensionFor("image/jpeg"));
        assertEquals("png", ImageKeys.extensionFor("image/png"));
        assertEquals("webp", ImageKeys.extensionFor("image/webp"));
    }

    @Test
    void extensionFor_ignoresParametersAndCasing() {
        // Browsers send things like "image/jpeg; charset=binary".
        assertEquals("jpg", ImageKeys.extensionFor("image/jpeg; charset=binary"));
        assertEquals("png", ImageKeys.extensionFor("IMAGE/PNG"));
        assertEquals("webp", ImageKeys.extensionFor("  image/webp  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "image/svg+xml", "application/pdf", "text/html", "", "image"})
    void extensionFor_rejectsAnythingElse(String contentType) {
        assertThrows(BadRequestException.class, () -> ImageKeys.extensionFor(contentType));
    }

    @Test
    void extensionFor_rejectsNull() {
        assertThrows(BadRequestException.class, () -> ImageKeys.extensionFor(null));
    }

    @Test
    void isAllowedContentType_agreesWithExtensionFor() {
        assertTrue(ImageKeys.isAllowedContentType("image/jpeg"));
        assertTrue(ImageKeys.isAllowedContentType("image/png; charset=binary"));
        assertFalse(ImageKeys.isAllowedContentType("image/svg+xml"));
        assertFalse(ImageKeys.isAllowedContentType(null));
    }
}
