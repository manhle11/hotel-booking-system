package com.hanbay.hotel.common.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanbay.hotel.common.exception.RequestValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DashboardTableQueryTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "amount",
                "unknown,asc",
                "amount,sideways",
                "amount,desc,createdAt",
                "amount;drop table payments,asc",
                "amount,desc;drop table payments",
                ",asc"
            })
    void rejectsUntrustedFieldsDirectionsAndMultipleColumns(String sort) {
        assertThatThrownBy(() -> DashboardTableQuery.orderBy(DashboardTableQuery.Table.PAYMENTS, sort))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void sortsNumbersAsValuesAndAlwaysUsesStableIdAndNullsLast() {
        assertThat(DashboardTableQuery.orderBy(DashboardTableQuery.Table.PAYMENTS, " amount , DESC "))
                .isEqualTo("\"amount\" DESC NULLS LAST, id ASC");
        assertThat(DashboardTableQuery.orderBy(DashboardTableQuery.Table.PAYMENTS, "paidAt,asc"))
                .isEqualTo("\"paidAt\" ASC NULLS LAST, id ASC");
    }
}
