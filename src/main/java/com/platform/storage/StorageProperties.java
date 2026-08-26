package com.platform.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** Selects the {@link StorageProvider} implementation. Currently only "r2". */
    private String provider = "r2";

    private String bucket;

    /** Lifetime of a presigned upload URL. Short on purpose: it limits orphan uploads. */
    private int signedUrlTtlSeconds = 60;

    /**
     * Advisory limit handed to the client and re-checked when the object is attached.
     * The enforceable limit lives on the bucket - see the storage notes in CLAUDE.md.
     */
    private long maxUploadBytes = 5L * 1024 * 1024;

    private final R2 r2 = new R2();

    @Getter
    @Setter
    public static class R2 {
        private String accountId;
        private String accessKeyId;
        private String secretAccessKey;

        /** Base URL objects are served from, e.g. the bucket's pub-&lt;hash&gt;.r2.dev subdomain. */
        private String publicUrlBase;
    }
}
