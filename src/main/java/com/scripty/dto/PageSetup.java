package com.scripty.dto;

import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import java.util.Locale;

/**
 * Paper size and margin preset for exported documents.
 *
 * Mirrors the page-setup options offered in the editor (page-setup.js) so a PDF
 * export lands on the same sheet the writer has been looking at on screen.
 * Margins are expressed in PDF points (72pt = 1in).
 */
public record PageSetup(Paper paper, Margins margins) {

    public enum Paper {
        LETTER(PageSize.LETTER),
        A4(PageSize.A4);

        private final Rectangle rectangle;

        Paper(Rectangle rectangle) {
            this.rectangle = rectangle;
        }

        public Rectangle rectangle() {
            return rectangle;
        }
    }

    /**
     * Screenplay convention puts the extra half inch on the left for the binding,
     * so every preset keeps a wider left margin than right.
     */
    public enum Margins {
        STANDARD(108f, 72f, 72f, 72f),
        NARROW(72f, 36f, 36f, 36f),
        WIDE(126f, 90f, 90f, 90f);

        private final float left;
        private final float right;
        private final float top;
        private final float bottom;

        Margins(float left, float right, float top, float bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        public float left() {
            return left;
        }

        public float right() {
            return right;
        }

        public float top() {
            return top;
        }

        public float bottom() {
            return bottom;
        }
    }

    public static final PageSetup DEFAULT = new PageSetup(Paper.LETTER, Margins.STANDARD);

    public PageSetup {
        if (paper == null) {
            paper = Paper.LETTER;
        }
        if (margins == null) {
            margins = Margins.STANDARD;
        }
    }

    /** Lenient parse of the request params; anything unrecognised falls back to the default. */
    public static PageSetup of(String paper, String margins) {
        return new PageSetup(parsePaper(paper), parseMargins(margins));
    }

    private static Paper parsePaper(String value) {
        if (value == null) {
            return Paper.LETTER;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "a4" -> Paper.A4;
            default -> Paper.LETTER;
        };
    }

    private static Margins parseMargins(String value) {
        if (value == null) {
            return Margins.STANDARD;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "narrow" -> Margins.NARROW;
            case "wide" -> Margins.WIDE;
            default -> Margins.STANDARD;
        };
    }
}
