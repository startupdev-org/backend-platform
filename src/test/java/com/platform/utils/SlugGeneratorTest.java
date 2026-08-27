package com.platform.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link SlugGenerator} produces the public URL identifier of a business, so its output is
 * effectively permanent: changing the algorithm changes every link already handed out.
 * These tests pin the current behaviour rather than the intended behaviour where the two
 * differ - see {@code generate_stripsSpacesInsteadOfHyphenating}.
 */
class SlugGeneratorTest {

    // ── Romanian diacritics ───────────────────────────────────────────────────
    // Business names in Moldova will carry these, so folding them is the case that
    // actually matters. NFD decomposition splits the base letter from its mark and the
    // non-word filter then drops the mark, which is why "ș" survives as "s".

    @ParameterizedTest
    @CsvSource({
            "Bărbați,      barbati",
            "Ștefan,       stefan",
            "Țăndărei,     tandarei",
            "ÎNĂLȚIME,     inaltime",
            "Așteptare,    asteptare",
            "Câmpina,      campina"
    })
    void generate_foldsRomanianDiacriticsToAscii(String input, String expected) {
        assertEquals(expected, SlugGenerator.generate(input));
    }

    @Test
    void generate_foldsOtherLatinDiacriticsToo() {
        assertEquals("cafe", SlugGenerator.generate("Café"));
        assertEquals("munchen", SlugGenerator.generate("München"));
    }

    @Test
    void generate_dropsCharactersWithNoAsciiBase() {
        // Non-Latin scripts decompose to nothing this filter keeps, so the slug empties out.
        // Worth knowing: such a name yields "" and every one of them collides.
        assertEquals("", SlugGenerator.generate("салон"));
        assertEquals("", SlugGenerator.generate("美容室"));
    }

    // ── Casing, punctuation, edges ────────────────────────────────────────────

    @Test
    void generate_lowercasesOutput() {
        assertEquals("barbershop", SlugGenerator.generate("BarberShop"));
    }

    @Test
    void generate_dropsPunctuation() {
        assertEquals("cafebar", SlugGenerator.generate("Café & Bar!"));
        assertEquals("l0real", SlugGenerator.generate("L'0real"));
    }

    @Test
    void generate_keepsHyphensAndUnderscores() {
        assertEquals("already-a-slug", SlugGenerator.generate("already-a-slug"));
        assertEquals("under_score", SlugGenerator.generate("under_score"));
    }

    @Test
    void generate_trimsLeadingAndTrailingHyphens() {
        assertEquals("trim", SlugGenerator.generate("-Trim-"));
    }

    @Test
    void generate_returnsEmptyForNullOrEmpty() {
        assertEquals("", SlugGenerator.generate(null));
        assertEquals("", SlugGenerator.generate(""));
    }

    // ── Known defect, pinned deliberately ─────────────────────────────────────

    /**
     * Spaces are removed rather than turned into hyphens, so word boundaries are lost.
     *
     * <p>The {@code WHITESPACE} rule that would hyphenate them is dead code: the
     * {@code [^\w-]} filter runs first and whitespace is neither a word character nor a
     * hyphen, so it is already gone by the time that rule is applied.
     *
     * <p>Pinned as-is on purpose. Fixing it would rewrite the slug of every business
     * created so far and break links already published, so it needs a migration decision,
     * not a quiet one-line change. When that lands, this test should flip to the
     * hyphenated form.
     */
    @Test
    void generate_stripsSpacesInsteadOfHyphenating() {
        assertEquals("helloworld", SlugGenerator.generate("Hello World"));
        assertEquals("salonstefancelmare", SlugGenerator.generate("Salon Ștefan cel Mare"));
        assertEquals("multispace", SlugGenerator.generate("multi   space"));

        assertNotEquals("hello-world", SlugGenerator.generate("Hello World"));
    }

    // ── Collisions ────────────────────────────────────────────────────────────

    /**
     * The generator is a pure function with no uniqueness logic: two businesses with the
     * same name produce the same slug. Uniqueness is the database's job - the unique
     * constraint on {@code businesses.slug} turns the second insert into a 409 - so this
     * test exists to make sure nobody assumes a de-duplicating suffix lives here.
     */
    @Test
    void generate_isDeterministicAndDoesNotDeduplicate() {
        assertEquals(SlugGenerator.generate("Salon Central"), SlugGenerator.generate("Salon Central"));
        assertEquals(SlugGenerator.generate("Salon Central"), SlugGenerator.generate("salon central"));
    }

    /**
     * Names that differ only by punctuation or spacing collapse to the same slug, which is
     * a collision the caller cannot see coming from the names alone.
     */
    @Test
    void generate_differentNamesCanCollide() {
        assertEquals(SlugGenerator.generate("Hair & Care"), SlugGenerator.generate("Hair Care"));
    }
}
