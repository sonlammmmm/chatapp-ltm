#!/bin/bash
# Script khởi chạy Client trên macOS / Linux
echo "=== KHỞI CHẠY CLIENT CHAT TCP SOCKET ==="
mvn exec:java -Dexec.mainClass="com.chatapp.client.ClientMain"
