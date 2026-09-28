package com.salestracker.tenant;

import jakarta.persistence.*;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** ISO 4217 code; only affects how amounts are displayed. */
    @Column(nullable = false, length = 3)
    private String currency = "BDT";

    /** Printed on invoices and packing slips; all optional. */
    private String phone;
    private String address;

    @Column(name = "invoice_note")
    private String invoiceNote;

    protected Tenant() {}

    public Tenant(String name) {
        this.name = name;
    }

    public void rename(String name) { this.name = name; }
    public void changeCurrency(String currency) { this.currency = currency; }

    public void changeContact(String phone, String address, String invoiceNote) {
        this.phone = phone;
        this.address = address;
        this.invoiceNote = invoiceNote;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getInvoiceNote() { return invoiceNote; }
}
