package com.scripty.viewmodel.project.versionhistory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionChangeSummaryTest {

    @Test
    void compactSummaryReportsNoChangesWhenEmpty() {
        VersionChangeSummary summary = new VersionChangeSummary();
        assertFalse(summary.hasChanges());
        assertEquals("No changes", summary.getCompactSummary());
    }

    @Test
    void compactSummaryJoinsCounts() {
        VersionChangeSummary summary = new VersionChangeSummary();
        summary.setBlocksAdded(2);
        summary.setBlocksEdited(1);
        summary.setScenesRemoved(1);
        summary.setCharactersAdded(1);
        summary.setProjectMetadataChanged(true);

        assertTrue(summary.hasChanges());
        assertEquals(
                "+2 blocks, 1 block edit, -1 scene, +1 character, project details",
                summary.getCompactSummary());
    }

    @Test
    void visibleDetailsCapsAtFivePlusOverflow() {
        VersionChangeSummary summary = new VersionChangeSummary();
        for (int i = 1; i <= 7; i++) {
            summary.addDetail("change " + i);
        }
        assertEquals(6, summary.getVisibleDetails().size());
        assertEquals("and 2 more changes", summary.getVisibleDetails().get(5));
    }
}
