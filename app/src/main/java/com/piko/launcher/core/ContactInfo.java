package com.piko.launcher.core;

/** Immutable descriptor of one phone contact, used for unified app+contact search. */
public final class ContactInfo {

    public final long contactId;
    public final String name;
    public final String nameLower;
    public final String phoneNumber;

    public ContactInfo(long contactId, String name, String phoneNumber) {
        this.contactId = contactId;
        this.name = name == null ? "" : name;
        this.nameLower = this.name.toLowerCase();
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContactInfo)) return false;
        return contactId == ((ContactInfo) o).contactId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(contactId);
    }
}
