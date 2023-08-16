package com.eu.habbo.habbohotel.items.rares;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface PreparedStatementVoidAction {
    void execute(PreparedStatement stmt) throws SQLException;
}
