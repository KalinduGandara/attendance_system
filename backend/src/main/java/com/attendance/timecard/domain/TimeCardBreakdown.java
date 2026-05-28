package com.attendance.timecard.domain;

import com.attendance.common.jpa.BaseChildEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "time_card_breakdown")
@Getter
@Setter
@NoArgsConstructor
public class TimeCardBreakdown extends BaseChildEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_time_card_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_tcb_card"))
    private DailyTimeCard dailyTimeCard;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "time_code_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID timeCodeId;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "rated_minutes", nullable = false)
    private int ratedMinutes;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;
}
