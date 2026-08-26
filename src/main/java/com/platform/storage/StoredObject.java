package com.platform.storage;

/** What the bucket reports about an object that is already there. */
public record StoredObject(long sizeBytes, String contentType) {
}
