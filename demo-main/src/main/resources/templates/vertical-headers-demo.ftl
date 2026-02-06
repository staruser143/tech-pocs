<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: 'Helvetica', sans-serif; margin: 20px; }
        .table { width: 100%; border-collapse: collapse; margin-top: 30px; }
        .table th, .table td { border: 1px solid #ddd; padding: 8px; text-align: center; }
        .table th { 
            background-color: #f2f2f2; 
            height: 200px; 
            width: 40px;
            padding: 0;
            vertical-align: middle;
            text-align: center;
            border: 1px solid #ddd;
        }
        
        .vertical-header {
            display: block;
            transform: rotate(-90deg);
            -webkit-transform: rotate(-90deg);
            white-space: nowrap;
            /* The key is to make the width of the div equal to the height of the cell */
            width: 200px;
            /* And use negative margins to center the rotated box back into the narrow cell */
            margin-left: -80px; 
            margin-right: -80px;
        }
    </style>
</head>
<body>
    <h1>Vertical Column Headers Example</h1>
    
    <table class="table">
        <thead>
            <tr>
                <th style="vertical-align: middle; width: 150px; height: auto;">Category</th>
                <th><div class="vertical-header">Performance Metric A</div></th>
                <th><div class="vertical-header">Security Compliance B</div></th>
                <th><div class="vertical-header">User Satisfaction C</div></th>
                <th><div class="vertical-header">Resource Usage D</div></th>
                <th><div class="vertical-header">Cost Efficiency E</div></th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>Region North</td>
                <td>95%</td>
                <td>Pass</td>
                <td>4.5/5</td>
                <td>Low</td>
                <td>High</td>
            </tr>
            <tr>
                <td>Region South</td>
                <td>88%</td>
                <td>Pass</td>
                <td>4.2/5</td>
                <td>Medium</td>
                <td>Medium</td>
            </tr>
        </tbody>
    </table>
</body>
</html>
