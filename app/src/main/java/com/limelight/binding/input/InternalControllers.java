package com.limelight.binding.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Controllers built into their device, though Android may report them as external (a USB gamepad, usually). A listed
 * controller is player 1 and leaves the physical back button to the system, so it can open the menu.
 * The user's changes are kept apart from the defaults, so a default added in a later version still arrives.
 */
public final class InternalControllers {
    public static final String ADDED_PREF = "internal_controllers_added";
    public static final String REMOVED_PREF = "internal_controllers_removed";

    // An entry matches any device name containing it, ignoring case
    static final List<String> DEFAULTS = Collections.unmodifiableList(Arrays.asList(
            "gpio", // The back button on Shield portable consoles
            "joy_key", // The gamepad buttons on the Archos Gamepad 2
            "keypad", // The gamepad buttons on the Xperia Play
            "NVIDIA Corporation NVIDIA Controller v01.01", // Shield Portable
            "NVIDIA Corporation NVIDIA Controller v01.02", // Shield Portable (?)
            "GR0006", // Logitech G Cloud
            "Retroid Pocket Controller", // Retroid Pocket handhelds
            "Odin Controller" // AYN Odin handhelds
    ));

    private InternalControllers() {}

    public static List<String> entries(Set<String> added, Set<String> removed) {
        Set<String> entries = new LinkedHashSet<>(DEFAULTS);
        entries.removeAll(removed);
        entries.addAll(new TreeSet<>(added));
        return new ArrayList<>(entries);
    }

    public static boolean isListed(String deviceName, Collection<String> entries) {
        String name = deviceName.toLowerCase(Locale.ROOT);
        for (String entry : entries) {
            if (name.contains(entry.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** What the settings offer: what is connected first, listed or not, then the other entries. */
    public static List<String> choices(Collection<String> entries, Collection<String> connected) {
        Set<String> choices = new LinkedHashSet<>();
        for (String name : new TreeSet<>(connected)) {
            boolean covered = false;
            for (String entry : entries) {
                if (isListed(name, Collections.singletonList(entry))) {
                    choices.add(entry);
                    covered = true;
                }
            }
            if (!covered) {
                choices.add(name);
            }
        }
        choices.addAll(entries);
        return new ArrayList<>(choices);
    }

    public static Set<String> added(Collection<String> chosen) {
        Set<String> added = new TreeSet<>(chosen);
        added.removeAll(DEFAULTS);
        return added;
    }

    public static Set<String> removed(Collection<String> chosen) {
        Set<String> removed = new TreeSet<>(DEFAULTS);
        removed.removeAll(chosen);
        return removed;
    }
}
