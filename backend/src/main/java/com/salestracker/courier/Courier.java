package com.salestracker.courier;

import com.salestracker.tenant.TenantOwned;
import jakarta.persistence.*;

import java.math.BigDecimal;
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

    /** What the courier charges to deliver one parcel inside / outside Dhaka (0 = nothing charged automatically). */
    @Column(name = "fee_inside", nullable = false)
    private BigDecimal feeInside = BigDecimal.ZERO;

    @Column(name = "fee_outside", nullable = false)
    private BigDecimal feeOutside = BigDecimal.ZERO;

    /** The courier's COD charge: this percentage of the cash it collects after its delivery fee. */
    @Column(name = "cod_pct", nullable = false)
    private BigDecimal codPct = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    protected Courier() {}

    public Courier(Long tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
        // Steadfast's COD charge is 1%; any other courier starts with none until the owner enters it.
        if ("Steadfast".equalsIgnoreCase(name)) this.codPct = BigDecimal.ONE;
    }

    public void apply(String name, String trackingUrl) {
        this.name = name;
        this.trackingUrl = trackingUrl;
    }

    public void applyCharges(BigDecimal feeInside, BigDecimal feeOutside, BigDecimal codPct) {
        this.feeInside = feeInside == null ? BigDecimal.ZERO : feeInside;
        this.feeOutside = feeOutside == null ? BigDecimal.ZERO : feeOutside;
        this.codPct = codPct == null ? BigDecimal.ZERO : codPct;
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
    public BigDecimal getFeeInside() { return feeInside; }
    public BigDecimal getFeeOutside() { return feeOutside; }
    public BigDecimal getCodPct() { return codPct; }
}
