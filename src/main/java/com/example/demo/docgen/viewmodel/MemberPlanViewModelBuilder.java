package com.example.demo.docgen.viewmodel;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MemberPlanViewModelBuilder implements ViewModelBuilder<MemberPlanViewModel> {

    @Override
    @SuppressWarnings("unchecked")
    public MemberPlanViewModel build(Map<String, Object> rawData) {
        List<Map<String, Object>> rawMembers = (List<Map<String, Object>>) rawData.getOrDefault("members", Collections.emptyList());

        // 1. Identify all unique products and plans
        Map<String, Set<String>> productPlansMap = new LinkedHashMap<>();
        List<String> preferredOrder = Arrays.asList("Medical", "Dental", "Vision");
        for (String p : preferredOrder) {
            productPlansMap.put(p, new TreeSet<>());
        }

        for (Map<String, Object> member : rawMembers) {
            List<Map<String, Object>> selections = (List<Map<String, Object>>) member.getOrDefault("selections", Collections.emptyList());
            for (Map<String, Object> sel : selections) {
                String product = (String) sel.get("product");
                String plan = (String) sel.get("plan");
                productPlansMap.computeIfAbsent(product, k -> new TreeSet<>()).add(plan);
            }
        }
        productPlansMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // 2. Chunk plans into groups of 2 per product
        Map<String, List<List<String>>> productPlanChunks = new LinkedHashMap<>();
        int maxTables = 0;
        for (Map.Entry<String, Set<String>> entry : productPlansMap.entrySet()) {
            List<String> allPlans = new ArrayList<>(entry.getValue());
            List<List<String>> chunks = new ArrayList<>();
            for (int i = 0; i < allPlans.size(); i += 2) {
                chunks.add(allPlans.subList(i, Math.min(i + 2, allPlans.size())));
            }
            productPlanChunks.put(entry.getKey(), chunks);
            maxTables = Math.max(maxTables, chunks.size());
        }

        // 3. Pre-process member selections for easy lookup
        List<MemberSelectionData> memberDataList = new ArrayList<>();
        for (Map<String, Object> rawMember : rawMembers) {
            String name = (String) rawMember.get("name");
            Map<String, Map<String, MemberPlanViewModel.PremiumInfo>> selectionsMap = new HashMap<>();
            List<Map<String, Object>> selections = (List<Map<String, Object>>) rawMember.getOrDefault("selections", Collections.emptyList());
            for (Map<String, Object> sel : selections) {
                String product = (String) sel.get("product");
                String plan = (String) sel.get("plan");
                Double base = ((Number) sel.getOrDefault("base", 0.0)).doubleValue();
                Double bundled = ((Number) sel.getOrDefault("bundled", 0.0)).doubleValue();
                selectionsMap.computeIfAbsent(product, k -> new HashMap<>())
                    .put(plan, MemberPlanViewModel.PremiumInfo.builder()
                        .basePremium(base)
                        .bundledPremium(bundled)
                        .build());
            }
            memberDataList.add(new MemberSelectionData(name, selectionsMap));
        }

        // 4. Build the tables
        List<MemberPlanViewModel.PlanTable> tables = new ArrayList<>();
        for (int t = 0; t < maxTables; t++) {
            List<MemberPlanViewModel.ProductHeader> tableHeaders = new ArrayList<>();
            
            // Determine which products/plans go into this table
            for (Map.Entry<String, List<List<String>>> entry : productPlanChunks.entrySet()) {
                List<List<String>> chunks = entry.getValue();
                if (t < chunks.size()) {
                    tableHeaders.add(MemberPlanViewModel.ProductHeader.builder()
                        .name(entry.getKey())
                        .plans(chunks.get(t))
                        .build());
                }
            }

            // Build member rows for this table
            List<MemberPlanViewModel.MemberRow> tableMembers = new ArrayList<>();
            for (MemberSelectionData mData : memberDataList) {
                tableMembers.add(MemberPlanViewModel.MemberRow.builder()
                    .memberName(mData.name)
                    .selections(mData.selections)
                    .build());
            }

            tables.add(MemberPlanViewModel.PlanTable.builder()
                .products(tableHeaders)
                .members(tableMembers)
                .build());
        }

        MemberPlanViewModel viewModel = MemberPlanViewModel.builder()
            .tables(tables)
            .build();
        return viewModel;
    }

    private static class MemberSelectionData {
        String name;
        Map<String, Map<String, MemberPlanViewModel.PremiumInfo>> selections;
        MemberSelectionData(String name, Map<String, Map<String, MemberPlanViewModel.PremiumInfo>> selections) {
            this.name = name;
            this.selections = selections;
        }
    }
}
