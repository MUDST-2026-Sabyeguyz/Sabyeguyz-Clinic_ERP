package com.sabyeguyz.clinic.commission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.ZoneId;
import com.sabyeguyz.clinic.sale.Sale;
import com.sabyeguyz.clinic.sale.SaleRepository;
import com.sabyeguyz.clinic.course.CoursePurchaseRepository;
import com.sabyeguyz.clinic.course.CoursePurchase;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionService {
    private final CommissionSchemeRepository schemes;
    private final CommissionTierRepository tiers;
    private final SaleRepository sales;
    private final CoursePurchaseRepository purchases;
    private final CommissionClosingRepository closings;
    private final CommissionAllocationLedgerRepository allocationLedger;

    public CommissionService(CommissionSchemeRepository schemes, CommissionTierRepository tiers, SaleRepository sales, CoursePurchaseRepository purchases, CommissionClosingRepository closings, CommissionAllocationLedgerRepository allocationLedger) {
        this.schemes = schemes; this.tiers = tiers; this.sales = sales; this.purchases=purchases; this.closings=closings; this.allocationLedger = allocationLedger;
    }

    @Transactional(readOnly = true)
    public List<CommissionScheme> listSchemes() { return schemes.findByOrderByEffectiveFromDesc(); }

    @Transactional(readOnly = true)
    public List<CommissionTier> tiers(long schemeId) { return tiers.findBySchemeIdAndActiveTrueOrderByTierOrder(schemeId); }

    @Transactional
    public CommissionScheme create(String name, String version, LocalDate from, LocalDate to, List<TierInput> inputs) {
        if (name == null || name.isBlank() || version == null || version.isBlank() || from == null) throw new IllegalArgumentException("scheme metadata is required");
        if (to != null && !to.isAfter(from)) throw new IllegalArgumentException("effective_to must be after effective_from");
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("at least one commission tier is required");
        validateTiers(inputs);
        CommissionScheme scheme = schemes.save(new CommissionScheme(name.trim(), version.trim(), from, to));
        inputs.stream().sorted(Comparator.comparingInt(TierInput::order)).map(i -> new CommissionTier(scheme, i.minimum(), i.maximum(), i.rate(), i.order())).forEach(tiers::save);
        return scheme;
    }

    @Transactional
    public CommissionScheme update(long id, String name, String version, LocalDate from, LocalDate to, List<TierInput> inputs) {
        validateMetadata(name, version, from, to, inputs); validateTiers(inputs);
        CommissionScheme scheme=schemes.findById(id).orElseThrow(()->new IllegalArgumentException("commission scheme not found"));
        scheme.update(name.trim(),version.trim(),from,to); tiers.deleteBySchemeId(id); tiers.flush();
        inputs.stream().sorted(Comparator.comparingInt(TierInput::order)).map(i->new CommissionTier(scheme,i.minimum(),i.maximum(),i.rate(),i.order())).forEach(tiers::save);
        return schemes.save(scheme);
    }
    @Transactional
    public List<CommissionClosing> closeMonth(LocalDate month) {
        List<CoursePurchase> all = purchases.findBySaleMonthBetween(month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1).minusDays(1));
        Map<Long,List<com.sabyeguyz.clinic.course.CoursePurchase>> grouped=new HashMap<>(); all.stream().filter(p->p.getSeller()!=null).forEach(p->grouped.computeIfAbsent(p.getSeller().getId(),k->new java.util.ArrayList<>()).add(p));
        List<CommissionClosing> result=new java.util.ArrayList<>(); for(var group:grouped.values()){var seller=group.get(0).getSeller();var total=group.stream().map(com.sabyeguyz.clinic.course.CoursePurchase::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add);var tier=resolve(month.withDayOfMonth(1),total);var scheme=schemes.findByOrderByEffectiveFromDesc().stream().filter(CommissionScheme::isActive).filter(s->!s.getEffectiveFrom().isAfter(month.withDayOfMonth(1))).findFirst().orElseThrow();var closing=closings.findByMonthAndEmployeeId(month.withDayOfMonth(1),seller.getId()).orElseGet(()->new CommissionClosing(month.withDayOfMonth(1),seller,total,scheme,tier.getCommissionRate()));closing.close();result.add(closings.save(closing));group.forEach(p->{p.lockCommission(scheme.getId(),scheme.getVersion(),tier.getCommissionRate());purchases.save(p);});} return result;
    }
    @Transactional public CommissionScheme activate(long id){CommissionScheme s=find(id);s.activate();return schemes.save(s);}
    @Transactional public void deactivate(long id){CommissionScheme s=find(id);s.deactivate();schemes.save(s);}
    @Transactional public CommissionScheme setActive(long id, boolean active){CommissionScheme s=find(id);if(active)s.activate();else s.deactivate();return schemes.save(s);}

    @Transactional(readOnly=true)
    public List<ReportRow> report(LocalDate from, LocalDate to) {
        if(from==null||to==null||to.isBefore(from)) throw new IllegalArgumentException("invalid report date range");
        ZoneId zone=ZoneId.of("Asia/Bangkok"); List<Sale> rows=sales.findPaidForCommission(from.atStartOfDay(zone).toInstant(),to.plusDays(1).atStartOfDay(zone).toInstant());
        Map<String,List<Sale>> groups=new LinkedHashMap<>();
        for(Sale s:rows){String key=(s.getSalesperson()==null?"-":s.getSalesperson().getId())+"|"+s.getBranch().getBranchCode();groups.computeIfAbsent(key,k->new java.util.ArrayList<>()).add(s);}
        return groups.values().stream().map(g->{Sale first=g.get(0);BigDecimal base=g.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO,BigDecimal::add);CommissionTier tier=resolve(to,base);return new ReportRow(first.getSalesperson()==null?null:first.getSalesperson().getId(),first.getSalesperson()==null?"ไม่ระบุ":first.getSalesperson().getFullName(),first.getBranch().getBranchCode(),first.getBranch().getName(),g.size(),base,tier.getCommissionRate(),base.multiply(tier.getCommissionRate()));}).toList();
    }
    private CommissionScheme find(long id){return schemes.findById(id).orElseThrow(()->new IllegalArgumentException("commission scheme not found"));}
    private void validateMetadata(String name,String version,LocalDate from,LocalDate to,List<TierInput> inputs){if(name==null||name.isBlank()||version==null||version.isBlank()||from==null)throw new IllegalArgumentException("scheme metadata is required");if(to!=null&&!to.isAfter(from))throw new IllegalArgumentException("effective_to must be after effective_from");if(inputs==null||inputs.isEmpty())throw new IllegalArgumentException("at least one commission tier is required");}

    @Transactional(readOnly = true)
    public CommissionTier resolve(LocalDate date, BigDecimal monthlySales) {
        return schemes.findByOrderByEffectiveFromDesc().stream()
                .filter(s -> s.isActive() && !s.getEffectiveFrom().isAfter(date)
                        && (s.getEffectiveTo() == null || date.isBefore(s.getEffectiveTo())))
                .findFirst().flatMap(s -> tiers.findBySchemeIdAndActiveTrueOrderByTierOrder(s.getId()).stream()
                        .filter(t -> monthlySales.compareTo(t.getMinimumSales()) >= 0
                                && (t.getMaximumSales() == null || monthlySales.compareTo(t.getMaximumSales()) <= 0)).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("no commission tier covers the monthly sales amount"));
    }

    private void validateTiers(List<TierInput> inputs) {
        List<TierInput> ordered = inputs.stream().sorted(Comparator.comparingInt(TierInput::order)).toList();
        BigDecimal expectedMinimum = BigDecimal.ZERO;
        for (TierInput tier : ordered) {
            if (tier.order() < 1 || tier.minimum() == null || tier.minimum().compareTo(BigDecimal.ZERO) < 0
                    || tier.rate() == null || tier.rate().compareTo(BigDecimal.ZERO) < 0 || tier.rate().compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("invalid commission tier");
            if (tier.maximum() != null && tier.maximum().compareTo(tier.minimum()) < 0) throw new IllegalArgumentException("tier minimum exceeds maximum");
            if (tier.minimum().compareTo(expectedMinimum) != 0) throw new IllegalArgumentException("commission tier ranges must be contiguous and non-overlapping");
            if (tier.maximum() == null && tier != ordered.get(ordered.size() - 1)) throw new IllegalArgumentException("open-ended tier must be last");
            if (tier.maximum() != null) expectedMinimum = tier.maximum().add(new BigDecimal("0.01"));
        }
    }

    public record TierInput(BigDecimal minimum, BigDecimal maximum, BigDecimal rate, int order) {}
    public record ReportRow(Long staffId,String staffName,String branchCode,String branchName,int saleCount,BigDecimal base,BigDecimal rate,BigDecimal commission) {}
    @Transactional(readOnly = true)
    public List<TreatmentReportRow> treatmentReport(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("invalid report date range");
        return allocationLedger.findByVisitDateBetweenOrderByVisitDateDescIdDesc(from, to).stream().map(row -> {
            var purchase = row.getCoursePurchase();
            var patient = purchase.getPatient();
            var branch = purchase.getBranch();
            var treating = row.getTreating();
            var owner = row.getOwner();
            return new TreatmentReportRow(row.getId(), row.getVisitDate(), purchase.getId(), purchase.getCourseCode(),
                    patient.getId(), patient.getHn(), patient.getFullName(), branch.getBranchCode(), branch.getName(),
                    treating == null ? null : treating.getId(), treating == null ? "ไม่ระบุ" : treating.getFullName(),
                    owner == null ? null : owner.getId(), owner == null ? "ไม่ระบุ" : owner.getFullName(),
                    row.getGross(), row.getFee(), row.getOwnerNet());
        }).toList();
    }
    public record TreatmentReportRow(Long id, LocalDate visitDate, Long coursePurchaseId, String courseCode,
            Long patientId, String patientHn, String patientName, String branchCode, String branchName,
            Long treatingStaffId, String treatingStaffName, Long ownerStaffId, String ownerStaffName,
            BigDecimal gross, BigDecimal treatmentFee, BigDecimal ownerNet) {}
}
