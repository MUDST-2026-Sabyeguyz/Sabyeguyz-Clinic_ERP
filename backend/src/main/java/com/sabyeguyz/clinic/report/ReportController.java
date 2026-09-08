package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Dashboard & Reports", description = "Operational dashboard and revenue, course, and staff reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard figures for a period",
            description = "Unique patients who actually attended, split by gender, plus visit count and revenue "
                    + "broken down into course, single visit, and assessment. Dates are clinic-local (Asia/Bangkok) "
                    + "and both ends are inclusive. Revenue is money actually kept, so a voided receipt contributes "
                    + "zero rather than its original total.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or unknown branch",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DashboardResponse dashboard(
            @Parameter(description = "First day of the period, inclusive", example = "2026-09-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day of the period, inclusive", example = "2026-09-30", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Limit to one branch; omit for every branch", example = "RP")
            @RequestParam(required = false) String branchCode) {
        return service.dashboard(new ReportPeriod(from, to), branchCode);
    }

    @GetMapping("/revenue")
    @Operation(summary = "Revenue report",
            description = "Total revenue with a breakdown by product category, by branch, and a daily series with "
                    + "empty days filled in as zero. The category amounts add up to the total, so the two can be "
                    + "cross-checked against each other.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revenue report returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or unknown branch",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RevenueReportResponse revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode) {
        return service.revenue(new ReportPeriod(from, to), branchCode);
    }

    @GetMapping("/course-balances")
    @Operation(summary = "Course balance report",
            description = "Every course sold in the period with purchased, used, and remaining sessions per patient. "
                    + "Filtered by the purchase date, not by when sessions were consumed. Transfers are not included "
                    + "yet because course transfer is not on develop.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course balance report returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or unknown branch",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Patient not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CourseBalanceReportResponse courseBalances(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode,
            @Parameter(description = "Limit to a single patient") @RequestParam(required = false) Long patientId) {
        return service.courseBalances(new ReportPeriod(from, to), branchCode, patientId);
    }

    @GetMapping("/staff-sales")
    @Operation(summary = "Staff sales report",
            description = "Sales totals per salesperson plus the branch total. Receipts issued without a salesperson "
                    + "are reported in a separate `unassigned` bucket rather than dropped, so the per-person rows "
                    + "and the unassigned bucket always add up to the branch total.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Staff sales report returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or unknown branch",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StaffSalesReportResponse staffSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String branchCode) {
        return service.staffSales(new ReportPeriod(from, to), branchCode);
    }
}
