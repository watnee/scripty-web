package com.scripty.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.lowagie.text.PageSize;
import org.junit.jupiter.api.Test;

class PageSetupTest {

    @Test
    void parsesPaperAndMarginsCaseInsensitively() {
        PageSetup setup = PageSetup.of("  A4 ", "NARROW");

        assertSame(PageSetup.Paper.A4, setup.paper());
        assertSame(PageSetup.Margins.NARROW, setup.margins());
    }

    @Test
    void unknownOrMissingValuesFallBackToLetterAndStandard() {
        for (PageSetup setup : new PageSetup[] {
                PageSetup.of(null, null),
                PageSetup.of("tabloid", "hairline"),
                PageSetup.of("", "")
        }) {
            assertSame(PageSetup.Paper.LETTER, setup.paper());
            assertSame(PageSetup.Margins.STANDARD, setup.margins());
        }
    }

    @Test
    void nullComponentsAreNormalisedByTheCanonicalConstructor() {
        PageSetup setup = new PageSetup(null, null);

        assertSame(PageSetup.Paper.LETTER, setup.paper());
        assertSame(PageSetup.Margins.STANDARD, setup.margins());
        assertEquals(PageSetup.DEFAULT, setup);
    }

    @Test
    void paperMapsToTheMatchingPdfRectangle() {
        assertEquals(PageSize.LETTER.getWidth(), PageSetup.Paper.LETTER.rectangle().getWidth(), 0.01f);
        assertEquals(PageSize.A4.getWidth(), PageSetup.Paper.A4.rectangle().getWidth(), 0.01f);
        assertEquals(PageSize.A4.getHeight(), PageSetup.Paper.A4.rectangle().getHeight(), 0.01f);
    }

    /** Screenplay convention: the binding edge always carries the extra width. */
    @Test
    void everyMarginPresetKeepsAWiderBindingEdge() {
        for (PageSetup.Margins margins : PageSetup.Margins.values()) {
            assertEquals(
                    36f,
                    margins.left() - margins.right(),
                    0.01f,
                    margins + " should keep a half-inch binding offset");
        }
    }

    @Test
    void standardMarginsMatchTheOneInchScreenplayDefault() {
        PageSetup.Margins standard = PageSetup.Margins.STANDARD;

        assertEquals(108f, standard.left(), 0.01f);   // 1.5in
        assertEquals(72f, standard.right(), 0.01f);   // 1in
        assertEquals(72f, standard.top(), 0.01f);
        assertEquals(72f, standard.bottom(), 0.01f);
    }
}
