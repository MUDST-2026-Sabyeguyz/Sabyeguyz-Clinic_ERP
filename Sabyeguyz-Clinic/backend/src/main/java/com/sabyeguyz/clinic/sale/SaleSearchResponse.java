package com.sabyeguyz.clinic.sale;

import java.util.List;

public record SaleSearchResponse(
        List<SaleSummaryResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
