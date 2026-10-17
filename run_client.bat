@echo off
chcp 65001 > nul
echo ====================================================
echo       KHOI CHAY CLIENT CHAT TCP SOCKET - NHOM 03
echo ====================================================
mvn exec:java -Dexec.mainClass="com.chatapp.client.ClientMain"
pause
