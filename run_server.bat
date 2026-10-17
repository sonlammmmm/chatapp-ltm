@echo off
chcp 65001 > nul
echo ====================================================
echo       KHOI CHAY SERVER TCP SOCKET - NHOM 03
echo ====================================================
mvn exec:java -Dexec.mainClass="com.chatapp.server.ServerMain"
pause
