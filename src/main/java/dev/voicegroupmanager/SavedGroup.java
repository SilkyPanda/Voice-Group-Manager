package dev.voicegroupmanager;

import java.util.UUID;

/** A persistent group as saved in persistent-groups.yml. */
record SavedGroup(UUID id, String name, String password, String type, boolean hidden) {
    SavedGroup withName(String newName) {
        return new SavedGroup(id, newName, password, type, hidden);
    }

    SavedGroup withType(String newType) {
        return new SavedGroup(id, name, password, newType, hidden);
    }

    SavedGroup withHidden(boolean newHidden) {
        return new SavedGroup(id, name, password, type, newHidden);
    }

    SavedGroup withPassword(String newPassword) {
        return new SavedGroup(id, name, newPassword, type, hidden);
    }
}
