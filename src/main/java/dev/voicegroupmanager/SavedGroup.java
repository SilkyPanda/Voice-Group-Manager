package dev.voicegroupmanager;

import java.time.Instant;
import java.util.UUID;

/** A persistent group as saved in persistent-groups.yml. {@code created} decides the order in that file. */
record SavedGroup(UUID id, String name, String password, String type, boolean hidden, Instant created) {
    SavedGroup withName(String newName) {
        return new SavedGroup(id, newName, password, type, hidden, created);
    }

    SavedGroup withType(String newType) {
        return new SavedGroup(id, name, password, newType, hidden, created);
    }

    SavedGroup withHidden(boolean newHidden) {
        return new SavedGroup(id, name, password, type, newHidden, created);
    }

    SavedGroup withPassword(String newPassword) {
        return new SavedGroup(id, name, newPassword, type, hidden, created);
    }
}
