package com.sabyeguyz.clinicv2.patient;

import java.io.Serializable;
import java.util.Objects;

public class HnSequenceId implements Serializable {
    private Long branch;
    private String yearMonth;

    public HnSequenceId() {}

    public HnSequenceId(
            Long branch,
            String yearMonth) {
        this.branch = branch;
        this.yearMonth = yearMonth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HnSequenceId that)) return false;

        return Objects.equals(branch, that.branch) && Objects.equals(yearMonth, that.yearMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(branch, yearMonth);
    }
}
