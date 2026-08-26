package com.platform.storage;

/** Which image slot an upload is destined for. Also, the folder segment in the key. */
public enum ImageTarget {

    LOGO("logo"),
    COVER("cover"),
    EMPLOYEE_PHOTO("photo");

    private final String folder;

    ImageTarget(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}
