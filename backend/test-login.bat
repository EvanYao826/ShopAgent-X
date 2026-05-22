@echo off
curl -s -X POST "http://localhost:8080/api/admin/login" -H "Content-Type: application/x-www-form-urlencoded" -d "username=admin&password=admin123"
pause
