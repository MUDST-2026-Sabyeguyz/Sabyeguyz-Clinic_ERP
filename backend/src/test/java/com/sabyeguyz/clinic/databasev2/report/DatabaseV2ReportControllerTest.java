package com.sabyeguyz.clinic.databasev2.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinic.report.AppointmentReportResponse;
import com.sabyeguyz.clinic.report.CourseBalanceReportResponse;
import com.sabyeguyz.clinic.report.DashboardResponse;
import com.sabyeguyz.clinic.report.ReportPeriod;
import com.sabyeguyz.clinic.report.RevenueReportResponse;
import com.sabyeguyz.clinic.report.StaffSalesReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseV2ReportControllerTest {

    private DatabaseV2ReportRepository reportRepository;
    private DatabaseV2ReportController controller;

    @BeforeEach
    void setUp() {
        reportRepository = mock(DatabaseV2ReportRepository.class);
        controller = new DatabaseV2ReportController(reportRepository);
    }

    @Test
    void dashboardReturnsSummary() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        DashboardResponse mockResponse = new DashboardResponse(
                from, to, "01", 10L, List.of(), 15L,
                new BigDecimal("50000.00"), new BigDecimal("30000.00"),
                new BigDecimal("20000.00"), BigDecimal.ZERO, 15L, 0L
        );
        when(reportRepository.dashboard(any(ReportPeriod.class), eq("01"))).thenReturn(mockResponse);

        DashboardResponse res = controller.dashboard(from, to, "01");
        assertThat(res.totalRevenue()).isEqualByComparingTo("50000.00");
        assertThat(res.uniquePatients()).isEqualTo(10L);
        assertThat(res.branchCode()).isEqualTo("01");
    }

    @Test
    void appointmentsReturnsReport() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        AppointmentReportResponse mockResponse = new AppointmentReportResponse(
                from, to, "01", 20L, 18L, BigDecimal.ZERO, List.of(), List.of(), List.of()
        );
        when(reportRepository.appointments(any(ReportPeriod.class), eq("01"))).thenReturn(mockResponse);

        AppointmentReportResponse res = controller.appointments(from, to, "01");
        assertThat(res.appointments()).isEqualTo(20L);
        assertThat(res.visits()).isEqualTo(18L);
    }

    @Test
    void revenueReturnsReport() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        RevenueReportResponse mockResponse = new RevenueReportResponse(
                from, to, "01", new BigDecimal("100000.00"), List.of(), List.of(), List.of()
        );
        when(reportRepository.revenue(any(ReportPeriod.class), eq("01"))).thenReturn(mockResponse);

        RevenueReportResponse res = controller.revenue(from, to, "01");
        assertThat(res.totalRevenue()).isEqualByComparingTo("100000.00");
    }

    @Test
    void courseBalancesReturnsReport() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        CourseBalanceReportResponse mockResponse = new CourseBalanceReportResponse(
                from, to, "01", 5, 50L, 10L, 40L, new BigDecimal("60000.00"), List.of()
        );
        when(reportRepository.courseBalances(any(ReportPeriod.class), eq("01"), eq(100L))).thenReturn(mockResponse);

        CourseBalanceReportResponse res = controller.courseBalances(from, to, "01", 100L);
        assertThat(res.purchases()).isEqualTo(5);
        assertThat(res.totalRemainingSessions()).isEqualTo(40L);
    }

    @Test
    void staffSalesReturnsReport() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        StaffSalesReportResponse mockResponse = new StaffSalesReportResponse(
                from, to, "01", new BigDecimal("80000.00"), 10L, List.of(),
                new StaffSalesReportResponse.Row(null, null, "ไม่ระบุผู้ขาย", 0, BigDecimal.ZERO)
        );
        when(reportRepository.staffSales(any(ReportPeriod.class), eq("01"))).thenReturn(mockResponse);

        StaffSalesReportResponse res = controller.staffSales(from, to, "01");
        assertThat(res.branchTotal()).isEqualByComparingTo("80000.00");
        assertThat(res.unassigned().fullName()).isEqualTo("ไม่ระบุผู้ขาย");
    }
}
