package com.sabyeguyz.clinic.databasev2.report;

import com.sabyeguyz.clinic.report.AppointmentReportResponse;
import com.sabyeguyz.clinic.report.CourseBalanceReportResponse;
import com.sabyeguyz.clinic.report.DashboardResponse;
import com.sabyeguyz.clinic.report.ReportPeriod;
import com.sabyeguyz.clinic.report.RevenueReportResponse;
import com.sabyeguyz.clinic.report.StaffSalesReportResponse;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Profile("database-v2")
public class DatabaseV2ReportController {

    private final DatabaseV2ReportRepository reportRepository;

    public DatabaseV2ReportController(DatabaseV2ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @GetMapping("/dashboard/summary")
    public DashboardResponse dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode) {
        return reportRepository.dashboard(new ReportPeriod(from, to), branchCode);
    }

    @GetMapping("/reports/appointments")
    public AppointmentReportResponse appointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode) {
        return reportRepository.appointments(new ReportPeriod(from, to), branchCode);
    }

    @GetMapping("/reports/revenue")
    public RevenueReportResponse revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode) {
        return reportRepository.revenue(new ReportPeriod(from, to), branchCode);
    }

    @GetMapping("/reports/course-balances")
    public CourseBalanceReportResponse courseBalances(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) Long patientId) {
        return reportRepository.courseBalances(new ReportPeriod(from, to), branchCode, patientId);
    }

    @GetMapping("/reports/staff-sales")
    public StaffSalesReportResponse staffSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode) {
        return reportRepository.staffSales(new ReportPeriod(from, to), branchCode);
    }
}
