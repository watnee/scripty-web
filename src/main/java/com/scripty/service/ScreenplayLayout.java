package com.scripty.service;

/**
 * Single source of truth for industry-standard screenplay page geometry.
 *
 * <p>Every exporter that emits real layout — PDF, DOCX, and the style hints in
 * FDX — reads its numbers from here, so the on-screen page view, the printed
 * page, and each downloaded file agree. They previously each hardcoded their
 * own constants and had drifted apart (differing parenthetical widths, a 6x
 * discrepancy in inter-element spacing, an FDX body shifted a quarter inch
 * left). Change a value here and every geometric exporter follows.
 *
 * <p>The matching CSS lives in {@code scripty.css} under the "Page view" and
 * {@code @media print} blocks. It expresses these same measurements as
 * percentages of the 6in text column, since the on-screen sheet scales; see
 * {@link #TEXT_WIDTH_IN} for the divisor.
 *
 * <p>Indents are measured <em>from the left margin</em>, not the page edge.
 * FDX is the exception and wants page-edge coordinates, which is what
 * {@link #fromPageEdge(double)} is for.
 */
public final class ScreenplayLayout {

    private ScreenplayLayout() {
    }

    /** Points per inch, the PostScript/CSS convention used by PDF and FDX. */
    public static final double POINTS_PER_INCH = 72.0;
    /** Twips per inch, the OOXML unit used by DOCX. */
    public static final long TWIPS_PER_INCH = 1440L;

    // --- Page ---------------------------------------------------------------

    public static final double PAGE_WIDTH_IN = 8.5;
    public static final double PAGE_HEIGHT_IN = 11.0;
    /** Wider than the others: the gutter absorbs the brad holes on a bound script. */
    public static final double MARGIN_LEFT_IN = 1.5;
    public static final double MARGIN_RIGHT_IN = 1.0;
    public static final double MARGIN_TOP_IN = 1.0;
    public static final double MARGIN_BOTTOM_IN = 1.0;
    /** The usable text column: 8.5 - 1.5 - 1.0. */
    public static final double TEXT_WIDTH_IN = PAGE_WIDTH_IN - MARGIN_LEFT_IN - MARGIN_RIGHT_IN;

    // --- Element indents, from the left margin ------------------------------

    /** Scene headings, action, and transitions run the full column. */
    public static final double ACTION_INDENT_IN = 0.0;
    public static final double ACTION_WIDTH_IN = TEXT_WIDTH_IN;

    public static final double CHARACTER_INDENT_IN = 2.2;

    public static final double PARENTHETICAL_INDENT_IN = 1.5;
    public static final double PARENTHETICAL_WIDTH_IN = 2.0;

    public static final double DIALOGUE_INDENT_IN = 1.0;
    public static final double DIALOGUE_WIDTH_IN = 3.5;

    // --- Type ---------------------------------------------------------------

    public static final double FONT_SIZE_PT = 12.0;
    /** Leading equal to the font size, i.e. CSS line-height 1 — one line is 12pt tall. */
    public static final double LINE_HEIGHT_PT = 12.0;

    // --- Vertical rhythm ----------------------------------------------------

    /**
     * One blank line between elements. Carried entirely as space <em>before</em>
     * an element so it composes predictably: the gap between any two elements is
     * decided by the second one, exactly like the CSS {@code margin-top} rules.
     */
    public static final double ELEMENT_SPACING_PT = LINE_HEIGHT_PT;
    /** A fuller break before a scene heading marks the new scene. */
    public static final double SCENE_SPACING_PT = 2 * LINE_HEIGHT_PT;
    /**
     * No gap inside a speech group — a parenthetical and its dialogue hug the
     * character cue above them.
     */
    public static final double SPEECH_GROUP_SPACING_PT = 0.0;

    // --- Unit conversion ----------------------------------------------------

    /** Inches to points, for PDF and FDX. */
    public static float pt(double inches) {
        return (float) (inches * POINTS_PER_INCH);
    }

    /** Inches to twips, for DOCX page and indent settings. */
    public static int twips(double inches) {
        return (int) Math.round(inches * TWIPS_PER_INCH);
    }

    /** Points to twips, for DOCX paragraph spacing (which is twips, not points). */
    public static int twipsFromPoints(double points) {
        return (int) Math.round(points / POINTS_PER_INCH * TWIPS_PER_INCH);
    }

    /**
     * Converts an indent measured from the left margin into one measured from
     * the page's left edge. Final Draft's {@code ElementSettings} use page-edge
     * coordinates rather than margin-relative ones.
     */
    public static double fromPageEdge(double indentFromMargin) {
        return MARGIN_LEFT_IN + indentFromMargin;
    }

    /** The page-edge coordinate of the right edge of the text column. */
    public static double rightTextEdgeFromPageEdge() {
        return MARGIN_LEFT_IN + TEXT_WIDTH_IN;
    }
}
