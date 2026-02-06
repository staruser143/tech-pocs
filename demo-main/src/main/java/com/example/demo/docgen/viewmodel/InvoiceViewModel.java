package com.example.demo.docgen.viewmodel;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InvoiceViewModel {
    private String invoiceNumber;
    private String date;
    private String customerName;
    private String customerAddress;
    private List<InvoiceItem> items;
    private double totalAmount;
    private boolean showDiscountMessage;

    @Data
    @Builder
    public static class InvoiceItem {
        private String description;
        private int quantity;
        private double unitPrice;
        private double lineTotal;
    }
}
