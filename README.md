# NFC-Based Student Lab Access App

## Summary
* [Project Overview](#po)
* [Team Members and Roles](#team)
* [System Architecure](#sa)
* [Technologies](#tech)

## <a name="po"></a>Project Overview
The NFC-Based Student Lab Access System is a secure and intelligent solution designed to modernize lab room access for ENCS students. Instead of manually entering room codes, students, can simply tap their NFC-enabled student card on a reader installed at the lab entrance to unlock the door.

The system integrates NFC hardware, a microcontroller-based access control unit, and a connected Android application to ensure secure authentication and real-time monitoring.

In addition to access control, the system includes a live occupancy tracker that monitors the number of students inside each lab. This helps prevent overcrowding, improves safety compliance, and provides students with real-time visibility of room availability through the mobile app.

This project delivers a scalable, secure, and user-friendly lab management system that improves both accessibility and efficiency.

## <a name="team"></a>Team Members
* Sonia Singh 40098260 (Product Owner)
* Zineb Bamouh 40263096
* Twfik Tommy Kahla 40285861
* Samin Karimi 40127432
* Riad Rakan 40264879

## <a name="sa"></a>System Architecture
The system follows a client–server architecture composed of three main layers: hardware, backend, and mobile application.

## <a name="tech"></a>Technologies
This web application uses these technologies

**Mobile App**
* Java
* Android SDK
* Gradle (Kotlin DSL)

**Hardware**
* NFC reader module
* ESP-32 Microcontroller
* IR Beam sensors
