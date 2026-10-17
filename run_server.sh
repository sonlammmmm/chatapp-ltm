#!/bin/bash
# Script khởi chạy Server trên macOS / Linux
echo "=== KHỞI CHẠY SERVER TCP SOCKET ==="
mvn exec:java -Dexec.mainClass="com.chatapp.server.ServerMain"
