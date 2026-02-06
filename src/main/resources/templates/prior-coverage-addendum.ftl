<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        h1 { color: #2c3e50; border-bottom: 2px solid #2c3e50; padding-bottom: 10px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { border: 1px solid #bdc3c7; padding: 10px; text-align: left; }
        th { background-color: #ecf0f1; font-weight: bold; }
        .header-info { margin-bottom: 20px; font-size: 0.9em; color: #7f8c8d; }
    </style>
</head>
<body>
    <h1>Prior Coverage Addendum</h1>
    <div class="header-info">
        Application ID: ${application.applicationId}<br/>
        Primary Applicant: ${application.primaryFirstName} ${application.primaryLastName}
    </div>
    
    <p>The following prior coverage information exceeded the space available on the main application form:</p>
    
    <table>
        <thead>
            <tr>
                <th>Applicant Name</th>
                <th>Provider</th>
                <th>Policy Number</th>
                <th>Effective Date</th>
            </tr>
        </thead>
        <tbody>
            <#list overflowItems as coverage>
            <tr>
                <td>${coverage.applicantName!"N/A"}</td>
                <td>${coverage.provider!""}</td>
                <td>${coverage.policyNumber!""}</td>
                <td>${coverage.effectiveDate!""}</td>
            </tr>
            </#list>
        </tbody>
    </table>
</body>
</html>
