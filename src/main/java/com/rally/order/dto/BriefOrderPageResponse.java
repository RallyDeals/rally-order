package com.rally.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BriefOrderPageResponse {
    private List<BriefOrderResponse> orders;
    private int page;
    private int limit;
    private long total;
}
