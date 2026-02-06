<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: 'Helvetica', sans-serif; margin: 40px; }
        .header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px; }
        .invoice-info-table { width: 100%; margin-top: 20px; border: none; }
        .invoice-info-table td { vertical-align: top; border: none; padding: 0; }
        .table { width: 100%; border-collapse: collapse; margin-top: 30px; }
        .table th, .table td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        .table th { background-color: #f2f2f2; }
        .total { text-align: right; margin-top: 20px; font-size: 1.2em; font-weight: bold; }
    </style>
</head>
<body>
    <div class="header">
        <h1>INVOICE</h1>
    </div>

    <table class="invoice-info-table">
        <tr>
            <td style="width: 50%;">
                <strong>Bill To:</strong><br/>
                ${customer.name}<br/>
                ${customer.address}<br/>
                ${customer.email}
            </td>
            <td style="width: 50%; text-align: right;">
                <strong>Invoice #:</strong> ${invoiceNumber}<br/>
                <strong>Date:</strong> ${date}
            </td>
        </tr>
    </table>

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
                <td>$${item.price?string("0.00")}</td>
                <td>$${(item.quantity * item.price)?string("0.00")}</td>
            </tr>
            </#list>
        </tbody>
    </table>

    <div class="total">
        Total: $${total?string("0.00")}
    </div>
</body>
</html>
