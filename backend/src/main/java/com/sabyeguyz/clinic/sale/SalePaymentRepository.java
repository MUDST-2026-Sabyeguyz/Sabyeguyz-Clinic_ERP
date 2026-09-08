package com.sabyeguyz.clinic.sale;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {

    /** รายได้ที่รับจริงของใบเสร็จ = ผลรวมของทุกรายการ การคืนเงินเป็นยอดลบจึงหักกันเอง */
    @Query("select coalesce(sum(p.amount), 0) from SalePayment p where p.sale.id = :saleId")
    BigDecimal paidAmountOf(@Param("saleId") Long saleId);

    List<SalePayment> findBySaleIdOrderByIdAsc(Long saleId);

    boolean existsByReversesPaymentId(Long paymentId);
}
