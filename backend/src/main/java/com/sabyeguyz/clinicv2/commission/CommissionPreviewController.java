package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceRepository;
import com.sabyeguyz.clinicv2.sale.Discount;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;

/**
 * Commission estimate shown above the pay button on the sale screen. The
 * seller defaults to the logged-in user's linked Staff record. Overriding the
 * shown commission (admin-permissioned in the feedback PDF) is not part of
 * this endpoint.
 */
@Transactional
@RestController
@RequestMapping("/api/commission/preview")
public class CommissionPreviewController {
    private final CommissionPreviewService previewService;
    private final ServiceRepository services;
    private final StaffRepository staff;
    private final AppUserRepository users;
    private final CurrentActor actor;
    private final BranchAccessPolicy branchAccess;

    public CommissionPreviewController(
            CommissionPreviewService previewService,
            ServiceRepository services,
            StaffRepository staff,
            AppUserRepository users,
            CurrentActor actor,
            BranchAccessPolicy branchAccess) {
        this.previewService = previewService;
        this.services = services;
        this.staff = staff;
        this.users = users;
        this.actor = actor;
        this.branchAccess = branchAccess;
    }

    @GetMapping
    public CommissionPreviewService.Preview preview(
            @RequestParam Long courseId,
            @RequestParam String branchCode,
            @RequestParam(required = false) Long sellerStaffId,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) BigDecimal discountValue) {
        var branch = branchAccess.scope(branchCode);

        if (branch == null) {
            throw new IllegalArgumentException("ต้องระบุสาขาสำหรับคำนวณคอมมิชชัน");
        }

        Service course = services.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        Staff seller = sellerStaffId != null
                ? staff.findById(sellerStaffId)
                        .orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงานขาย"))
                : currentStaff();

        return previewService.previewCourse(
                course,
                seller,
                price,
                Discount.of(discountType, discountValue),
                null,
                branch.getId());
    }

    private Staff currentStaff() {
        var user = users.findById(actor.requireId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));

        if (user.getStaff() == null) {
            throw new IllegalArgumentException("บัญชีผู้ใช้นี้ไม่ได้ผูกกับข้อมูลพนักงาน ต้องระบุ sellerStaffId");
        }

        return user.getStaff();
    }
}
