package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Block;
import com.scripty.dto.User;
import org.junit.jupiter.api.Test;

class CapitalizationPreferencesTest {

    @Test
    void allUppercasesEveryCapitalizedType() {
        CapitalizationPreferences caps = CapitalizationPreferences.ALL;

        assertEquals("INT. KITCHEN", caps.apply("int. kitchen", Block.TYPE_SCENE));
        assertEquals("JANE", caps.apply("Jane", Block.TYPE_CHARACTER));
        assertEquals("JANE", caps.apply("Jane", Block.TYPE_DUAL_DIALOGUE));
        assertEquals("CUT TO:", caps.apply("cut to:", Block.TYPE_TRANSITION));
        assertEquals("CLOSE ON", caps.apply("close on", Block.TYPE_SHOT));
    }

    @Test
    void leavesTypesThatWereNeverUppercased() {
        assertEquals("Jane enters.", CapitalizationPreferences.ALL.apply("Jane enters.", Block.TYPE_ACTION));
        assertEquals("Hello.", CapitalizationPreferences.ALL.apply("Hello.", Block.TYPE_DIALOGUE));
    }

    @Test
    void disabledTypeKeepsTypedCaseWhileOthersStayUppercase() {
        CapitalizationPreferences caps = new CapitalizationPreferences(false, true, true, false);

        assertEquals("int. kitchen", caps.apply("int. kitchen", Block.TYPE_SCENE));
        assertEquals("close on", caps.apply("close on", Block.TYPE_SHOT));
        assertEquals("JANE", caps.apply("Jane", Block.TYPE_CHARACTER));
        assertEquals("CUT TO:", caps.apply("cut to:", Block.TYPE_TRANSITION));
    }

    @Test
    void characterFlagCoversDualDialogue() {
        CapitalizationPreferences caps = new CapitalizationPreferences(true, false, true, true);

        assertFalse(caps.appliesTo(Block.TYPE_CHARACTER));
        assertFalse(caps.appliesTo(Block.TYPE_DUAL_DIALOGUE));
    }

    @Test
    void toleratesNullText() {
        assertNull(CapitalizationPreferences.ALL.apply(null, Block.TYPE_SCENE));
    }

    @Test
    void nullUserFallsBackToHistoricBehavior() {
        assertEquals(CapitalizationPreferences.ALL, CapitalizationPreferences.from(null));
    }

    @Test
    void readsFlagsFromUser() {
        User user = new User();
        user.setAutoCapsScene(false);
        user.setAutoCapsCharacter(true);
        user.setAutoCapsTransition(false);
        user.setAutoCapsShot(true);

        CapitalizationPreferences caps = CapitalizationPreferences.from(user);

        assertEquals(new CapitalizationPreferences(false, true, false, true), caps);
    }

    @Test
    void htmlClassesNameOnlyTheDisabledTypes() {
        assertEquals("", CapitalizationPreferences.ALL.htmlClasses());
        assertEquals("scripty-no-caps-scene scripty-no-caps-shot",
                new CapitalizationPreferences(false, true, true, false).htmlClasses());
        assertTrue(new CapitalizationPreferences(false, false, false, false).htmlClasses()
                .contains("scripty-no-caps-character"));
    }
}
