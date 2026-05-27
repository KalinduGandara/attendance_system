package com.attendance.organization.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import com.attendance.common.jpa.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
@Auditable("Employee")
@EntityListeners(AuditEntityListener.class)
public class Employee extends BaseEntity {

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "employee_code", nullable = false, length = 64, unique = true)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 64)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 64)
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "department_id", columnDefinition = "BINARY(16)")
    private UUID departmentId;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "manager_id", columnDefinition = "BINARY(16)")
    private UUID managerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 16)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "employee_group",
            joinColumns = @JoinColumn(name = "employee_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<UserGroup> groups = new HashSet<>();
}
