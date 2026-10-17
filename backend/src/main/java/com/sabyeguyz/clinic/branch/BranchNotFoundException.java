package com.sabyeguyz.clinic.branch;

public class BranchNotFoundException extends RuntimeException {
    public BranchNotFoundException() { super("ไม่พบสาขา"); }
}
