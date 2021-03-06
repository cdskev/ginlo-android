// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import eu.ginlo_apps.ginlo.greendao.StatusText;
import eu.ginlo_apps.ginlo.model.constant.NumberConstants;
import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;
import org.greenrobot.greendao.internal.DaoConfig;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.

/**
 * DAO for table STATUS_TEXT.
 */
public class StatusTextDao
        extends AbstractDao<eu.ginlo_apps.ginlo.greendao.StatusText, Long> {

    public static final String TABLENAME = "STATUS_TEXT";

    public StatusTextDao(DaoConfig config) {
        super(config);
    }

    public StatusTextDao(DaoConfig config,
                         AbstractDaoSession daoSession) {
        super(config, daoSession);
    }

    /**
     * Creates the underlying database table.
     */
    public static void createTable(Database db,
                                   boolean ifNotExists) {
        String constraint = ifNotExists ? "IF NOT EXISTS " : "";

        db.execSQL("CREATE TABLE " + constraint + "'STATUS_TEXT' ("  //
                + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                + "'ENCRYPTED_DATA' BLOB,"  // 1: encryptedData
                + "'IV' BLOB);");  // 2: iv
    }

    /**
     * Drops the underlying database table.
     */
    public static void dropTable(SQLiteDatabase db,
                                 boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "'STATUS_TEXT'";

        db.execSQL(sql);
    }

    /**
     * @inheritdoc
     */
    @Override
    protected void bindValues(SQLiteStatement stmt,
                              eu.ginlo_apps.ginlo.greendao.StatusText entity) {
        stmt.clearBindings();
        entity.beforeSafe();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(NumberConstants.INT_2, encryptedData);
        }

        byte[] iv = entity.getIv();

        if (iv != null) {
            stmt.bindBlob(NumberConstants.INT_3, iv);
        }
    }

    /**
     * @inheritdoc
     */
    @Override
    public Long readKey(Cursor cursor,
                        int offset) {
        return cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0);
    }

    /**
     * @inheritdoc
     */
    @Override
    public eu.ginlo_apps.ginlo.greendao.StatusText readEntity(Cursor cursor,
                                                             int offset) {
        eu.ginlo_apps.ginlo.greendao.StatusText entity = new eu.ginlo_apps.ginlo.greendao.StatusText(  //
                cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0),  // id
                cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getBlob(offset + NumberConstants.INT_1),  // encryptedData
                cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getBlob(offset + NumberConstants.INT_2)  // iv
        );
        return entity;
    }

    /**
     * @inheritdoc
     */
    @Override
    public void readEntity(Cursor cursor,
                           eu.ginlo_apps.ginlo.greendao.StatusText entity,
                           int offset) {
        entity.setId(cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0));
        entity.setEncryptedData(cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getBlob(offset + NumberConstants.INT_1));
        entity.setIv(cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getBlob(offset + NumberConstants.INT_2));
    }

    @Override
    protected void bindValues(DatabaseStatement stmt, eu.ginlo_apps.ginlo.greendao.StatusText entity) {
        stmt.clearBindings();
        entity.beforeSafe();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(NumberConstants.INT_2, encryptedData);
        }

        byte[] iv = entity.getIv();

        if (iv != null) {
            stmt.bindBlob(NumberConstants.INT_3, iv);
        }
    }

    /**
     * @inheritdoc
     */
    @Override
    protected Long updateKeyAfterInsert(eu.ginlo_apps.ginlo.greendao.StatusText entity,
                                        long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    /**
     * @inheritdoc
     */
    @Override
    public Long getKey(eu.ginlo_apps.ginlo.greendao.StatusText entity) {
        if (entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    protected boolean hasKey(StatusText entity) {
        return entity != null && entity.getId() != null;
    }

    /**
     * @inheritdoc
     */
    @Override
    protected boolean isEntityUpdateable() {
        return true;
    }

    /**
     * Properties of entity StatusText.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    private static class Properties {

        public static final Property Id = new Property(NumberConstants.INT_0, Long.class, "id", true, "_id");

        public static final Property EncryptedData = new Property(NumberConstants.INT_1, byte[].class, "encryptedData", false,
                "ENCRYPTED_DATA");

        public static final Property Iv = new Property(NumberConstants.INT_2, byte[].class, "iv", false, "IV");
    }
}
