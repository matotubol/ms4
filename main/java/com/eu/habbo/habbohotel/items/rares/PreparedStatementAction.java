package com.eu.habbo.habbohotel.items.rares;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface PreparedStatementAction<R> {
    R execute(PreparedStatement stmt) throws SQLException;
}
