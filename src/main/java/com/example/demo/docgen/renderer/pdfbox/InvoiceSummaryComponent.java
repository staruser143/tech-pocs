package com.example.demo.docgen.renderer.pdfbox;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.viewmodel.InvoiceViewModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A PDFBox component that renders an invoice summary using the InvoiceViewModel
 */
@Component("invoiceSummaryComponent")
public class InvoiceSummaryComponent implements PdfComponent {

    @Override
    public void render(PDDocument document, PDPage page, RenderContext context, Object data) throws IOException {
        if (!(data instanceof InvoiceViewModel)) {
            throw new IllegalArgumentException("InvoiceSummaryComponent requires an InvoiceViewModel");
        }

        InvoiceViewModel viewModel = (InvoiceViewModel) data;

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
            contentStream.newLineAtOffset(50, 750);
            contentStream.showText("INVOICE SUMMARY (PDFBox Component)");
            
            contentStream.setFont(PDType1Font.HELVETICA, 12);
            contentStream.newLineAtOffset(0, -30);
            contentStream.showText("Invoice Number: " + viewModel.getInvoiceNumber());
            
            contentStream.newLineAtOffset(0, -20);
            contentStream.showText("Customer: " + viewModel.getCustomerName());
            
            contentStream.newLineAtOffset(0, -20);
            contentStream.showText("Total Amount: $" + String.format("%.2f", viewModel.getTotalAmount()));
            
            if (viewModel.isShowDiscountMessage()) {
                contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 10);
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Special discount applied to this invoice!");
            }
            
            contentStream.endText();
        }
    }
}
