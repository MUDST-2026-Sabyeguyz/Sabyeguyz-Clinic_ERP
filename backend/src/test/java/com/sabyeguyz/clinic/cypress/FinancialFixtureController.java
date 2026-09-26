package com.sabyeguyz.clinic.cypress;

import com.sabyeguyz.clinic.auth.*;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.course.CourseMovementRepository;
import com.sabyeguyz.clinic.sale.*;
import com.sabyeguyz.clinic.staff.*;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Narrow fixture mutations and persisted row observations; all assertions live in Cypress. */
@RestController
@Profile("cypress")
@PreAuthorize("hasRole('MANAGER')")
@RequestMapping("/__cypress/financial-fixtures")
public class FinancialFixtureController {
    private final EntityManager em;
    private final ServiceItemRepository services;
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final SaleItemRepository items;
    private final SalePaymentRepository payments;
    private final CourseMovementRepository movements;

    public FinancialFixtureController(EntityManager em, ServiceItemRepository services,
            AppUserRepository users, StaffRepository staff, SaleItemRepository items,
            SalePaymentRepository payments, CourseMovementRepository movements) {
        this.em = em; this.services = services; this.users = users; this.staff = staff;
        this.items = items; this.payments = payments; this.movements = movements;
    }

    @PostMapping("/unpriced-service") @Transactional
    public Map<String, Long> unpricedService() {
        String code = "NP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        em.createNativeQuery("insert into services(code, name, default_duration_minutes, price, active) values (?1, ?2, 30, 0, true)")
                .setParameter(1, code).setParameter(2, "บริการที่ยังไม่ตั้งราคา").executeUpdate();
        em.flush(); em.clear();
        return Map.of("id", services.findByCode(code).orElseThrow().getId());
    }

    @PostMapping("/salesperson") @Transactional
    public Map<String, Long> salesperson() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = users.save(new AppUser("sale-person-" + suffix, "hash", Role.THERAPIST));
        return Map.of("id", staff.save(new Staff(user, "SP" + suffix, "พนักงานขาย ทดสอบ", "0800000013")).getId());
    }

    @PostMapping("/purchases/{id}/expire") @Transactional
    public void expire(@PathVariable long id) {
        Instant now = Instant.now();
        em.createNativeQuery("update course_purchases set purchased_at = ?1, expires_at = ?2 where id = ?3")
                .setParameter(1, now.minus(Duration.ofDays(10)))
                .setParameter(2, now.minus(Duration.ofDays(9))).setParameter(3, id).executeUpdate();
        em.flush(); em.clear();
    }

    public record PaymentRow(Long id, SalePaymentType type, Long reversesPaymentId) {}
    public record SaleRows(List<Long> itemIds, List<PaymentRow> payments) {}

    @GetMapping("/sales/{id}/rows") @Transactional(readOnly = true)
    public SaleRows saleRows(@PathVariable long id) {
        return new SaleRows(items.findBySaleIdOrderByIdAsc(id).stream().map(SaleItem::getId).toList(),
                payments.findBySaleIdOrderByIdAsc(id).stream().map(p -> new PaymentRow(p.getId(), p.getType(),
                        p.getReversesPayment() == null ? null : p.getReversesPayment().getId())).toList());
    }

    @GetMapping("/purchases/{id}/movement-ids") @Transactional(readOnly = true)
    public List<Long> movementIds(@PathVariable long id) {
        return movements.findByCoursePurchaseIdOrderByIdAsc(id).stream().map(m -> m.getId()).toList();
    }
}
