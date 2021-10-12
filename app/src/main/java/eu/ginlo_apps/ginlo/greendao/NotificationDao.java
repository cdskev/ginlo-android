// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.greendao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import eu.ginlo_apps.ginlo.greendao.Notification;
import eu.ginlo_apps.ginlo.model.constant.NumberConstants;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;
import org.greenrobot.greendao.internal.DaoConfig;

public class NotificationDao
        extends AbstractDao<eu.ginlo_apps.ginlo.greendao.Notification, Long> {

    public static final String TABLENAME = "NOTIFICATION";

    public NotificationDao(final DaoConfig config) {
        super(config);
    }

    public NotificationDao(final DaoConfig config,
                           final AbstractDaoSession daoSession) {
        super(config, daoSession);
    }

    /**
     * Creates the underlying database table.
     */
    public static void createTable(Database db,
                                   boolean ifNotExists) {
        String constraint = ifNotExists ? "IF NOT EXISTS " : "";

        db.execSQL("CREATE TABLE " + constraint + "'NOTIFICATION' ("  //
                + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                + "'MESSAGE_GUID' TEXT);");  // 1: MESSAGE_GUID
    }

    /**
     * Drops the underlying database table.
     */
    public static void dropTable(final SQLiteDatabase db,
                                 final boolean ifExists) {
        final String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "'NOTIFICATION'";

        db.execSQL(sql);
    }

    @Override
    protected eu.ginlo_apps.ginlo.greendao.Notification readEntity(final Cursor cursor, final int offset) {
        final eu.ginlo_apps.ginlo.greendao.Notification entity = new eu.ginlo_apps.ginlo.greendao.Notification(
                cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0),  // id
                cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1)  // productId
        );
        return entity;
    }

    @Override
    protected Long readKey(final Cursor cursor, final int offset) {
        return cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0);
    }

    @Override
    protected void readEntity(final Cursor cursor, final eu.ginlo_apps.ginlo.greendao.Notification entity, final int offset) {
        entity.setId(cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0));
        entity.setMessageGuid(cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1));
    }

    @Override
    protected void bindValues(final DatabaseStatement stmt, final eu.ginlo_apps.ginlo.greendao.Notification entity) {
        stmt.clearBindings();

        final Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        final String messageGuid = entity.getMessageGuid();

        if (messageGuid != null) {
            stmt.bindString(NumberConstants.INT_2, messageGuid);
        }
    }

    @Override
    protected void bindValues(final SQLiteStatement stmt, final eu.ginlo_apps.ginlo.greendao.Notification entity) {
        stmt.clearBindings();

        final Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        final String messageGuid = entity.getMessageGuid();

        if (messageGuid != null) {
            stmt.bindString(NumberConstants.INT_2, messageGuid);
        }
    }

    @Override
    protected Long updateKeyAfterInsert(final eu.ginlo_apps.ginlo.greendao.Notification entity, final long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    protected Long getKey(final eu.ginlo_apps.ginlo.greendao.Notification entity) {
        if (entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    protected boolean hasKey(final Notification entity) {
        return entity != null && entity.getId() != null;
    }

    @Override
    protected boolean isEntityUpdateable() {
        return true;
    }

    /**
     * Properties of entity Product.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {

        public static final Property Id = new Property(NumberConstants.INT_0, Long.class, "id", true, "_id");

        public static final Property MessageGuid = new Property(NumberConstants.INT_1, String.class, "messageGuid", false, "MESSAGE_GUID");
    }
}
