package com.example.demo.docgen.viewmodel;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MemberPlanViewModel {
    private List<PlanTable> tables;

    @Data
    @Builder
    public static class PlanTable {
        private List<ProductHeader> products;
        private List<MemberRow> members;
    }

    @Data
    @Builder
    public static class ProductHeader {
        private String name;
        private List<String> plans;
        
        public int getColSpan() {
            return plans.size() * 2;
        }
    }

    @Data
    @Builder
    public static class MemberRow {
        private String memberName;
        // Map<ProductName, Map<PlanName, PremiumInfo>>
        private Map<String, Map<String, PremiumInfo>> selections;
    }

    @Data
    @Builder
    public static class PremiumInfo {
        private Double basePremium;
        private Double bundledPremium;
    }
}
