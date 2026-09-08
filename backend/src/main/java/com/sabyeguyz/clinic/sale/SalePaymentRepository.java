package com.sabyeguyz.clinic.sale;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {

    /** รายได้ที่รับจริงของใบเสร็จ = ผลรวมของทุกรายการ การคืนเงินเป็นยอดลบจึงหักกันเอง */
    @Query("select coalesce(sum(p.amount), 0) from SalePayment p where p.sale.id = :saleId")
    BigDecimal paidAmountOf(@Param("saleId") Long saleId);

    /** ยอดรับจริงของหลายใบพร้อมกัน ใช้ตอนลิสต์ เพื่อไม่ให้เกิด query ต่อหนึ่งใบ */
    @Query("""
            select new com.sabyeguyz.clinic.sale.SalePaidAmount(p.sale.id, coalesce(sum(p.amount), 0))
            from SalePayment p
            where p.sale.id in :saleIds
            group by p.sale.id
            """)
    List<SalePaidAmount> paidAmountsOf(@Param("saleIds") Collection<Long> saleIds);

    List<SalePayment> findBySaleIdOrderByIdAsc(Long saleId);

    boolean existsByReversesPaymentId(Long paymentId);
}
