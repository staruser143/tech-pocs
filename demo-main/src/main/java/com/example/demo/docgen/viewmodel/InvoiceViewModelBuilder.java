package com.example.demo.docgen.viewmodel;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class InvoiceViewModelBuilder implements ViewModelBuilder<InvoiceViewModel> {

    @Override
    @SuppressWarnings("unchecked")
    public InvoiceViewModel build(Map<String, Object> rawData) {
        // Extract data from raw map (simulating what would come from JSON)
        String invoiceNumber = (String) rawData.getOrDefault("invoiceNumber", "N/A");
        String date = (String) rawData.getOrDefault("date", "");
        
        Map<String, Object> customer = (Map<String, Object>) rawData.get("customer");
        String customerName = "";
        String customerAddress = "";
        
        if (customer != null) {
            customerName = (String) customer.getOrDefault("companyName", "");
            Map<String, Object> address = (Map<String, Object>) customer.get("address");
            if (address != null) {
                customerAddress = String.format("%s, %s, %s %s",
                    address.get("street"),
                    address.get("city"),
                    address.get("state"),
                    address.get("zipCode"));
            }
        }

        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) rawData.getOrDefault("items", new ArrayList<>());
        List<InvoiceViewModel.InvoiceItem> items = rawItems.stream()
            .map(item -> {
                double unitPrice = ((Number) item.getOrDefault("unitPrice", 0.0)).doubleValue();
                int quantity = ((Number) item.getOrDefault("quantity", 0)).intValue();
                return InvoiceViewModel.InvoiceItem.builder()
                    .description((String) item.get("description"))
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(unitPrice * quantity)
                    .build();
            })
            .collect(Collectors.toList());

        double totalAmount = items.stream().mapToDouble(InvoiceViewModel.InvoiceItem::getLineTotal).sum();

        return InvoiceViewModel.builder()
            .invoiceNumber(invoiceNumber)
            .date(date)
            .customerName(customerName)
            .customerAddress(customerAddress)
            .items(items)
            .totalAmount(totalAmount)
            .showDiscountMessage(totalAmount > 1000)
            .build();
    }
}
