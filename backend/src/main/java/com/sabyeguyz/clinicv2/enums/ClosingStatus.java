package com.sabyeguyz.clinicv2.enums;

/** Open = being recorded, Closed = tier calculated and waiting for payment (Manager can reopen), Finalized = paid and locked. */
public enum ClosingStatus {
    Open,
    Closed,
    Finalized
}
