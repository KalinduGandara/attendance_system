## 1. Introduction

### 1.1 Purpose

This Software Requirements Specification (SRS) details the complete feature set of the BioStar 2 Time and Attendance (T&A) System. It outlines the functional, interface, and system requirements for a web-based platform designed to automate workforce management, enforce complex organizational policies, and minimize payroll inaccuracies.

### 1.2 Scope

BioStar 2 T&A operates as a hierarchical, module-based extension of the BioStar 2 access control platform. It leverages biometric hardware to prevent time theft and seamlessly shares user, user group, and device configurations with the Access Control (AC) module to provide a unified workforce tracking environment.

---

## 2. Operating Environment & System Architecture

### 2.1 Software and Communication

* **Web Client:** Optimized for modern web browsers, explicitly requiring Google Chrome version 100 or higher.
* **Network Protocols:** Operates over default network ports 3000 (HTTP) and 3002 (HTTPS).
* **Database Integration:** Supports MariaDB (the standard for T&A since version 2.2.1) and MS SQL.

### 2.2 Licensing

* **Base Tier:** The system supports up to 100 users natively without requiring a dedicated, paid T&A license.

---

## 3. Functional Requirements

### 3.1 Hardware & External Interfaces

* **FR-1.1 Module-Based Designation:** Any device registered in the BioStar 2 Access Control (AC) module can be designated as a T&A recording device.
* **FR-1.2 Multi-Credential Authentication:** The system shall support RFID cards, mobile credentials (NFC/BLE), QR codes, and biometric inputs.
* **FR-1.3 Biometric Anti-Spoofing:** The system shall utilize hardware-level fingerprint and facial recognition to explicitly prevent "buddy punching" (one employee clocking in for another).
* **FR-1.4 Wiegand Compatibility:** The platform shall integrate with third-party Wiegand devices for T&A event management.

### 3.2 Time Code Management

* **FR-2.1 Code Categories:** The system shall organize unlimited custom Time Codes into three primary categories: Attendance Management, Overtime Management, and Leave Management.
* **FR-2.2 Granular Time Rates:** Administrators shall be able to assign time rates ranging from 0.0 to 10.0 to any code (e.g., configuring a 2.0 rate to double calculated overtime hours).
* **FR-2.3 Visual Organization:** The interface shall support customizable color assignments for Time Codes to allow instant visual identification within schedules and reports.

### 3.3 Work Shift Configuration

* **FR-3.1 Shift Architectures:** The system shall support multiple operational shifts:
* **Fixed:** Enforces strict check-in and check-out parameters.
* **Flexible:** Allows employees to determine their own start and end times within required daily hour thresholds.
* **Floating:** Dynamically assigns a shift based on the time of the employee's first punch.


* **FR-3.2 Break & Meal Tracking:**
* **Automatic Meal Deduction:** Seamlessly deducts required meal times without requiring employees to interact with hardware.
* **Break by Punch:** Tracks explicit break durations by requiring "break start" and "break end" events at the device.


* **FR-3.3 Rounding & Grace Periods:** Managers shall configure time-rounding rules (shift, punch-in, punch-out) and allowed grace periods directly within the shift configuration menu.

### 3.4 Scheduling & Rostering

* **FR-4.1 Visual Schedule Templates:** The system shall allow the creation of daily or weekly repeated shift cycles via a drag-and-drop interface.
* **FR-4.2 Calendar-Based Assignment:** Administrators can assign templates to individual users or groups across specific date ranges, automatically accounting for established company holidays and registered leaves.
* **FR-4.3 Temporary Overrides:** The system shall allow authorized users to apply Temporary Schedules to override an employee's standard template for a defined duration (e.g., moving a fixed-shift worker to a flexible shift for one week).
* **FR-4.4 Tiered Overtime Rules:** The system shall support complex, chronological overtime flows applied directly to shifts (e.g., applying "Overtime A" after 8 hours of work, scaling to "Overtime B" after 11 hours).

### 3.5 Time Card & Monitoring Interface

* **FR-5.1 Dual-View Time Card:** The primary dashboard shall provide both a Calendar View and a List View to deliver a single-glance overview of daily work hours, shift names, leave states, and absences.
* **FR-5.2 Exception Management:** The UI shall permit authorized managers to manually edit punch records, register retroactive leave, and resolve attendance exceptions.

### 3.6 Reporting

* **FR-6.1 Standardized Report Suite:** The system shall natively generate seven comprehensive report types: Daily, Daily Summary, Individual, Individual Summary, Leave, Exception, and Modified Punch Log History.
* **FR-6.2 Customization & Export:** All reports shall support full-dataset sorting, allow the inclusion of Custom User Fields, and be completely exportable to CSV format for payroll ingestion.

---

## 4. System Management & Security

* **NFR-1 Audit Trails:** The system must track and log all configuration changes and manual punch log edits to ensure a forensic trail for HR compliance.
* **NFR-2 Automated Data Maintenance:** The platform shall support scheduled, automatic backups of configuration files.
* **NFR-3 Data Retention Polices:** The system shall allow administrators to configure the automatic deletion of old punch logs after a designated retention period to manage database performance and privacy compliance.