package com.artillexstudios.axvaults.lifecycle;

import java.sql.DriverManager;
import java.sql.SQLException;

public final class JdbcCleanup {
    private JdbcCleanup() {}

    public static void release(ClassLoader owner) {
        DriverManager.drivers().filter(driver -> ownedBy(driver.getClass().getClassLoader(), owner))
                .forEach(driver -> {
                    try {
                        DriverManager.deregisterDriver(driver);
                    } catch (SQLException ex) {
                        throw new IllegalStateException("Could not deregister JDBC driver", ex);
                    }
                });
        // MySQL owns an additional cleanup thread outside Hikari's pool.
        try {
            Class<?> cleanup = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", false, owner);
            if (ownedBy(cleanup.getClassLoader(), owner)) cleanup.getMethod("uncheckedShutdown").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // MySQL is optional.
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not stop MySQL cleanup thread", ex);
        }
    }

    static boolean ownedBy(ClassLoader loader, ClassLoader owner) {
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            if (current == owner) return true;
        }
        return false;
    }
}
