package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipts (Clinic ERP Workflow Brief §5).
 *
 * <p>Note: the data dictionary has no dedicated receipt-numbering sequence
 * table (unlike HN_sequence for patients), so receiptNo is caller-supplied
 * here. Before this goes live it needs the same kind of per-branch/month
 * running-number table and row-locked generator as HnGeneratorService —
 * flagging this as a gap to raise with the team.</p>
 */
@Service
public class ReceiptService {
    private final ReceiptRepository repository;

    public ReceiptService(ReceiptRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Receipt create(String receiptNo, Patient patient, Branch branch, LocalDate saleDate, Long actorId) {
        String no = requireText(receiptNo, "receiptNo");
        if (repository.findByReceiptNo(no).isPresent()) {
            throw new IllegalArgumentException("เลขที่ใบเสร็จซ้ำ");
        }
        if (patient == null) {
            throw new IllegalArgumentException("ต้องระบุผู้ป่วย");
        }
        if (branch == null) {
            throw new IllegalArgumentException("ต้องระบุสาขา");
        }
        Receipt receipt = new Receipt();
        receipt.setReceiptNo(no);
        receipt.setPatient(patient);
        receipt.setBranch(branch);
        receipt.setSaleDate(saleDate == null ? LocalDate.now() : saleDate);
        receipt.setReceiptCreateAt(Instant.now());
        receipt.setReceiptCreateBy(actorId);
        return repository.save(receipt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
