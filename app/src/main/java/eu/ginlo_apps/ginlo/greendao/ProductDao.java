// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import eu.ginlo_apps.ginlo.greendao.Product;
import eu.ginlo_apps.ginlo.model.constant.NumberConstants;
import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;
import org.greenrobot.greendao.internal.DaoConfig;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.

/**
 * DAO for table PRODUCT.
 */
public class ProductDao
        extends AbstractDao<eu.ginlo_apps.ginlo.greendao.Product, Long> {

    public static final String TABLENAME = "PRODUCT";

    public ProductDao(DaoConfig config) {
        super(config);
    }

    public ProductDao(DaoConfig config,
                      AbstractDaoSession daoSession) {
        super(config, daoSession);
    }

    /**
     * Creates the underlying database table.
     */
    public static void createTable(Database db,
                                   boolean ifNotExists) {
        String constraint = ifNotExists ? "IF NOT EXISTS " : "";

        db.execSQL("CREATE TABLE " + constraint + "'PRODUCT' ("  //
                + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                + "'PRODUCT_ID' TEXT,"  // 1: productId
                + "'PRICE' TEXT,"  // 2: price
                + "'ENCRYPTED_DATA' BLOB,"  // 3: encryptedData
                + "'IV' BLOB);");  // 4: iv
    }

    /**
     * Drops the underlying database table.
     */
    public static void dropTable(SQLiteDatabase db,
                                 boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "'PRODUCT'";

        db.execSQL(sql);
    }

    /**
     * @inheritdoc
     */
    @Override
    protected void bindValues(SQLiteStatement stmt,
                              eu.ginlo_apps.ginlo.greendao.Product entity) {
        stmt.clearBindings();
        entity.beforeSafe();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        String productId = entity.getProductId();

        if (productId != null) {
            stmt.bindString(NumberConstants.INT_2, productId);
        }

        String price = entity.getPrice();

        if (price != null) {
            stmt.bindString(NumberConstants.INT_3, price);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(NumberConstants.INT_4, encryptedData);
        }

        byte[] iv = entity.getIv();

        if (iv != null) {
            stmt.bindBlob(NumberConstants.INT_5, iv);
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
    public eu.ginlo_apps.ginlo.greendao.Product readEntity(Cursor cursor,
                                                          int offset) {
        eu.ginlo_apps.ginlo.greendao.Product entity = new eu.ginlo_apps.ginlo.greendao.Product(  //
                cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0),  // id
                cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1),  // productId
                cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getString(offset + NumberConstants.INT_2),  // price
                cursor.isNull(offset + NumberConstants.INT_3) ? null : cursor.getBlob(offset + NumberConstants.INT_3),  // encryptedData
                cursor.isNull(offset + NumberConstants.INT_4) ? null : cursor.getBlob(offset + NumberConstants.INT_4)  // iv
        );
        return entity;
    }

    /**
     * @inheritdoc
     */
    @Override
    public void readEntity(Cursor cursor,
                           eu.ginlo_apps.ginlo.greendao.Product entity,
                           int offset) {
        entity.setId(cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0));
        entity.setProductId(cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1));
        entity.setPrice(cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getString(offset + NumberConstants.INT_2));
        entity.setEncryptedData(cursor.isNull(offset + NumberConstants.INT_3) ? null : cursor.getBlob(offset + NumberConstants.INT_3));
        entity.setIv(cursor.isNull(offset + NumberConstants.INT_4) ? null : cursor.getBlob(offset + NumberConstants.INT_4));
    }

    @Override
    protected void bindValues(DatabaseStatement stmt, eu.ginlo_apps.ginlo.greendao.Product entity) {
        stmt.clearBindings();
        entity.beforeSafe();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        String productId = entity.getProductId();

        if (productId != null) {
            stmt.bindString(NumberConstants.INT_2, productId);
        }

        String price = entity.getPrice();

        if (price != null) {
            stmt.bindString(NumberConstants.INT_3, price);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(NumberConstants.INT_4, encryptedData);
        }

        byte[] iv = entity.getIv();

        if (iv != null) {
            stmt.bindBlob(NumberConstants.INT_5, iv);
        }
    }

    /**
     * @inheritdoc
     */
    @Override
    protected Long updateKeyAfterInsert(eu.ginlo_apps.ginlo.greendao.Product entity,
                                        long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    /**
     * @inheritdoc
     */
    @Override
    public Long getKey(eu.ginlo_apps.ginlo.greendao.Product entity) {
        if (entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    protected boolean hasKey(Product entity) {
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
     * Properties of entity Product.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    private static class Properties {

        public static final Property Id = new Property(NumberConstants.INT_0, Long.class, "id", true, "_id");

        public static final Property ProductId = new Property(NumberConstants.INT_1, String.class, "productId", false, "PRODUCT_ID");

        public static final Property Price = new Property(NumberConstants.INT_2, String.class, "price", false, "PRICE");

        public static final Property EncryptedData = new Property(NumberConstants.INT_3, byte[].class, "encryptedData", false,
                "ENCRYPTED_DATA");

        public static final Property Iv = new Property(NumberConstants.INT_4, byte[].class, "iv", false, "IV");
    }
}
