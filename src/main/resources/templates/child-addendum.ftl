<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        h1 { color: #2c3e50; border-bottom: 2px solid #2c3e50; padding-bottom: 10px; }
        .addendum-info { margin-bottom: 20px; font-style: italic; color: #7f8c8d; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { border: 1px solid #bdc3c7; padding: 12px; text-align: left; }
        th { background-color: #ecf0f1; font-weight: bold; }
        .page-footer { margin-top: 30px; text-align: center; font-size: 12px; color: #95a5a6; }
    </style>
</head>
<body>
    <h1>Dependent Addendum</h1>
    <div class="addendum-info">
        Application ID: ${application.applicationId}<br/>
        Primary Applicant: ${application.primaryFirstName} ${application.primaryLastName}<br/>
        Addendum Page: ${addendumPageNumber} of ${totalAddendumPages}
    </div>

    <p>The following dependents exceeded the capacity of the main application form:</p>

    <table>
        <thead>
            <tr>
                <th>First Name</th>
                <th>Last Name</th>
                <th>Date of Birth</th>
                <th>Relationship</th>
            </tr>
        </thead>
        <tbody>
            <#list overflowItems as child>
            <tr>
                <td>${child.demographic.firstName}</td>
                <td>${child.demographic.lastName}</td>
                <td>${child.demographic.dateOfBirth}</td>
                <td>${child.relationship!"Child"}</td>
            </tr>
            </#list>
        </tbody>
    </table>

    <div class="page-footer">
        This addendum is part of the official enrollment application.
    </div>
</body>
</html>
