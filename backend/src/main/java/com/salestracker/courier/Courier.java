package com.salestracker.courier;

import com.salestracker.tenant.TenantOwned;
import jakarta.persistence.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** A delivery service the business ships with. Each business keeps its own list, so any courier can be added. */
@Entity
@Table(name = "couriers")
public class Courier implements TenantOwned {
    /** Where the tracking number goes in a tracking link template. */
    public static final String TRACKING_PLACEHOLDER = "{tracking}";

    /** The couriers a new workspace starts with; the owner can rename, remove or add to them. */
    public static final List<String> DEFAULTS =
            List.of("Pathao", "Steadfast", "RedX", "Paperfly", "Sundarban Courier");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "tracking_url")
    private String trackingUrl;

    @Column(nullable = false)
    private boolean active = true;

    protected Courier() {}

    public Courier(Long tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
    }

    public void apply(String name, String trackingUrl) {
        this.name = name;
        this.trackingUrl = trackingUrl;
    }

    public void deactivate() { this.active = false; }
    public void reactivate() { this.active = true; }

    /**
     * The tracking page for one parcel: the tracking value itself when it is already a web link, else the courier's
     * link template filled in; null when there is neither.
     */
    public String trackingLink(String trackingRef) {
        if (trackingRef == null) return null;
        if (isWebLink(trackingRef)) return trackingRef;
        if (trackingUrl == null) return null;
        String encoded = URLEncoder.encode(trackingRef, StandardCharsets.UTF_8);
        return trackingUrl.contains(TRACKING_PLACEHOLDER)
                ? trackingUrl.replace(TRACKING_PLACEHOLDER, encoded)
                : trackingUrl;
    }

    /** Only http(s) links are ever handed back as links, so a pasted "javascript:..." stays plain text. */
    static boolean isWebLink(String s) {
        String lower = s.toLowerCase();
        return (lower.startsWith("https://") || lower.startsWith("http://")) && s.chars().noneMatch(Character::isWhitespace);
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getTrackingUrl() { return trackingUrl; }
    public boolean isActive() { return active; }
}
