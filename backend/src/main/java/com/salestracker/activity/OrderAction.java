package com.salestracker.activity;

/** What someone did to an order. PAID_OUT / PAYOUT_UNDONE are the courier paying out (or not) the cash it collected. */
public enum OrderAction { CREATED, EDITED, STATUS_CHANGED, DELETED, PAID_OUT, PAYOUT_UNDONE }
