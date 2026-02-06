<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: 'Helvetica', sans-serif; margin: 40px; }
        .header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px; }
        .invoice-info { margin-top: 20px; }
        .table { width: 100%; border-collapse: collapse; margin-top: 30px; }
        .table th, .table td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        .table th { background-color: #f2f2f2; }
        .total { text-align: right; margin-top: 20px; font-size: 1.2em; font-weight: bold; }
        .discount-message { color: green; font-style: italic; margin-top: 10px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>INVOICE (ViewModel)</h1>
    </div>

    <div class="invoice-info">
        <div style="float: left;">
            <strong>Bill To:</strong><br/>
            ${customerName}<br/>
            ${customerAddress}
        </div>
        <div style="float: right; text-align: right;">
            <strong>Invoice #:</strong> ${invoiceNumber}<br/>
            <strong>Date:</strong> ${date}
        </div>
        <div style="clear: both;"></div>
    </div>

    <table class="table">
        <thead>
            <tr>
                <th>Description</th>
                <th>Quantity</th>
                <th>Unit Price</th>
                <th>Amount</th>
            </tr>
        </thead>
        <tbody>
            <#list items as item>
            <tr>
                <td>${item.description}</td>
                <td>${item.quantity}</td>
                <td>$${item.unitPrice?string("0.00")}</td>
                <td>$${item.lineTotal?string("0.00")}</td>
            </tr>
            </#list>
        </tbody>
    </table>

    <div class="total">
        Total: $${totalAmount?string("0.00")}
    </div>

    <#if showDiscountMessage>
        <p class="discount-message">Pay within 10 days for 2% discount!</p>
    </#if>
</body>
</html>
