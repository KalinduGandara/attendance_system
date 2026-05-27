package com.attendance.organization.domain;

import com.attendance.common.audit.AuditEntityListener;
import com.attendance.common.audit.Auditable;
import com.attendance.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

@Entity
@Table(name = "holiday")
@Getter
@Setter
@NoArgsConstructor
@Auditable("Holiday")
@EntityListeners(AuditEntityListener.class)
public class Holiday extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "recurring_yearly", nullable = false)
    private boolean recurringYearly;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "holiday_group",
            joinColumns = @JoinColumn(name = "holiday_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<UserGroup> groups = new HashSet<>();
}
