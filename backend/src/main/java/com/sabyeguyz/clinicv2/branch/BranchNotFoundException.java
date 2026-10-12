package com.sabyeguyz.clinicv2.branch;

public class BranchNotFoundException extends RuntimeException {
    public BranchNotFoundException() { super("ไม่พบสาขา"); }
}
