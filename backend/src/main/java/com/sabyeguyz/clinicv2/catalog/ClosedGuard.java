package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.sale.PaymentMethod;

/**
 * A branch, service, course or payment method that has been closed (ปิดใช้งาน) is closed for good: nothing new
 * can be done with it until it is opened again. History that already exists is left as it is.
 */
public final class ClosedGuard {
    private ClosedGuard() {}

    public static Service open(Service service) {
        if (!service.isServiceStatus()) {
            throw new IllegalArgumentException("บริการ/คอร์ส " + service.getServiceName() + " ปิดใช้งาน ไม่สามารถใช้งานได้");
        }

        return service;
    }

    public static Branch open(Branch branch) {
        if (!branch.isBranchStatus()) {
            throw new IllegalArgumentException("สาขา " + branch.getBranchName() + " ปิดใช้งาน ไม่สามารถทำรายการที่สาขานี้ได้");
        }

        return branch;
    }

    public static PaymentMethod open(PaymentMethod method) {
        if (!method.isPaymentMethodStatus()) {
            throw new IllegalArgumentException("ช่องทางชำระเงิน " + method.getMethodName() + " ปิดใช้งาน ไม่สามารถใช้งานได้");
        }

        return method;
    }
}
