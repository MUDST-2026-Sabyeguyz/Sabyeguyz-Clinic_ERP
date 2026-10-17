package com.sabyeguyz.clinic.treatment;
public class TreatmentNotFoundException extends RuntimeException {
    public TreatmentNotFoundException(long id) { super("Treatment not found: "+id); }
}
