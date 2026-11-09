package com.luokuiai.liquibase.dameng;

import java.math.BigInteger;

final class DamengSequenceSql {
    private DamengSequenceSql() {
    }

    static void appendOptions(StringBuilder sql, BigInteger increment, BigInteger minimum,
            BigInteger maximum, BigInteger cache, Boolean cycle, Boolean ordered) {
        if (increment != null) {
            sql.append(" INCREMENT BY ").append(increment);
        }
        if (minimum != null) {
            sql.append(" MINVALUE ").append(minimum);
        }
        if (maximum != null) {
            sql.append(" MAXVALUE ").append(maximum);
        }
        if (cache != null) {
            sql.append(BigInteger.ZERO.equals(cache) ? " NOCACHE" : " CACHE " + cache);
        }
        if (cycle != null) {
            sql.append(cycle ? " CYCLE" : " NOCYCLE");
        }
        if (ordered != null) {
            sql.append(ordered ? " ORDER" : " NOORDER");
        }
    }
}
