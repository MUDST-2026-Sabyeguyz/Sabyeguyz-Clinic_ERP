package com.sabyeguyz.clinicv2.enums;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    Confirmed, Arrived, InService, Completed, Cancelled, Rescheduled, NoShow;

    /** Statuses that release the staff's time slot, so a new booking there is not a conflict. */
    public static Set<AppointmentStatus> slotReleasing() {
        return EnumSet.of(Cancelled, NoShow);
    }

    /** Statuses that count as the patient actually attending (for visit/report counts). */
    public static Set<AppointmentStatus> attended() {
        return EnumSet.of(Arrived, InService, Completed);
    }
}
