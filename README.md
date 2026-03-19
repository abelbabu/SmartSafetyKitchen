# 🔥 Smart Kitchen Safety System

## 📱 Overview
This project is an IoT-based smart kitchen safety system using ESP32 and an Android application.

## ⚙️ Features
Gas leak detection
Flame detection with timeout control
Automatic gas cutoff system
Real-time monitoring using Firebase
Mobile app alerts and control
Event logging system

## 🧩 Components Used
ESP32
Gas Sensor (MQ series)
Flame Sensor
DHT22 (Temperature & Humidity)
Relay Module
Buzzer and LED

## 📡 Working
ESP32 collects sensor data and sends it to Firebase Realtime Database.  
The Android app reads this data in real time and displays system status.  
If danger is detected, gas supply is automatically cut off and user is alerted.

## 🔐 Security Note
Sensitive data like WiFi credentials and API keys are kept private.

## 🚀 Future Scope
User login system
Device pairing
Smart notifications
