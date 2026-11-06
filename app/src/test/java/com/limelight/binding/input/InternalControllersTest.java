package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class InternalControllersTest {
    private static final Set<String> NONE = Collections.emptySet();

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    @Test
    public void handheldsAreListedByDefault() {
        List<String> entries = InternalControllers.entries(NONE, NONE);
        assertTrue(InternalControllers.isListed("Odin Controller", entries));
        assertTrue(InternalControllers.isListed("Retroid Pocket Controller", entries));
        assertTrue(InternalControllers.isListed("gpio-keys", entries));
        assertFalse(InternalControllers.isListed("Xbox Wireless Controller", entries));
    }

    @Test
    public void userAddsAndRemoves() {
        List<String> entries = InternalControllers.entries(set("Some Handheld Pad"), set("Odin Controller"));
        assertTrue(InternalControllers.isListed("Some Handheld Pad", entries));
        assertFalse(InternalControllers.isListed("Odin Controller", entries));
        assertTrue(InternalControllers.isListed("Retroid Pocket Controller", entries));
    }

    @Test
    public void matchingIgnoresCase() {
        assertTrue(InternalControllers.isListed("ODIN CONTROLLER", InternalControllers.entries(NONE, NONE)));
    }

    @Test
    public void choicesPutWhatIsConnectedFirst() {
        List<String> entries = InternalControllers.entries(NONE, NONE);
        List<String> choices = InternalControllers.choices(entries, set("Odin Controller", "Xbox Wireless Controller"));
        assertEquals(entries.size() + 1, choices.size());
        assertEquals("Odin Controller", choices.get(0));
        assertEquals("Xbox Wireless Controller", choices.get(1));
    }

    @Test
    public void savingWhatWasChosenRestoresIt() {
        List<String> chosen = InternalControllers.entries(set("Some Handheld Pad"), set("Odin Controller"));
        Set<String> added = InternalControllers.added(chosen);
        Set<String> removed = InternalControllers.removed(chosen);
        assertEquals(set("Some Handheld Pad"), added);
        assertEquals(set("Odin Controller"), removed);
        assertEquals(chosen, InternalControllers.entries(added, removed));
    }
}
