<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: 'Helvetica', sans-serif; margin: 20px; font-size: 12px; }
        h2 { text-align: center; color: #333; }
        .plan-table { width: 100%; border-collapse: collapse; margin-top: 20px; table-layout: fixed; }
        .plan-table th, .plan-table td { border: 1px solid #ccc; padding: 8px; text-align: center; word-wrap: break-word; }
        .plan-table th { background-color: #f4f4f4; font-weight: bold; }
        .product-header { background-color: #e9ecef; font-size: 14px; }
        .plan-header { background-color: #f8f9fa; }
        .sub-header { font-size: 10px; color: #666; }
        .member-name { text-align: left; font-weight: bold; width: 150px; }
        .currency { text-align: right; }
        .empty-cell { color: #ccc; font-style: italic; }
    </style>
</head>
<body>
    <h2>Member Plan Selection Summary</h2>

    <#list tables as table>
    <div class="table-container" style="margin-bottom: 40px;">
        <#if tables?size gt 1>
            <p><strong>Table ${table?index + 1} of ${tables?size}</strong></p>
        </#if>
        <table class="plan-table">
            <thead>
                <!-- Row 1: Products -->
                <tr>
                    <th rowspan="3" class="member-name">Member Name</th>
                    <#list table.products as product>
                        <th colspan="${product.colSpan}" class="product-header">${product.name}</th>
                    </#list>
                </tr>
                <!-- Row 2: Plans -->
                <tr>
                    <#list table.products as product>
                        <#list product.plans as plan>
                            <th colspan="2" class="plan-header">${plan}</th>
                        </#list>
                    </#list>
                </tr>
                <!-- Row 3: Base/Bundled Labels -->
                <tr class="sub-header">
                    <#list table.products as product>
                        <#list product.plans as plan>
                            <th>Base</th>
                            <th>Bundled</th>
                        </#list>
                    </#list>
                </tr>
            </thead>
            <tbody>
                <#list table.members as member>
                <tr>
                    <td class="member-name">${member.memberName}</td>
                    <#list table.products as product>
                        <#list product.plans as plan>
                            <#assign selection = (member.selections[product.name][plan])!>
                            <#if selection?has_content>
                                <td class="currency">$${selection.basePremium?string("0.00")}</td>
                                <td class="currency">$${selection.bundledPremium?string("0.00")}</td>
                            <#else>
                                <td class="empty-cell">-</td>
                                <td class="empty-cell">-</td>
                            </#if>
                        </#list>
                    </#list>
                </tr>
                </#list>
            </tbody>
        </table>
    </div>
    </#list>
</body>
</html>
