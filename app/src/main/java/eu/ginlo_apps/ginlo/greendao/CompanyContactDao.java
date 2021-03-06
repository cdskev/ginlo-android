// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.NumberConstants;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;
import org.greenrobot.greendao.internal.DaoConfig;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.

/**
 * DAO for table CONTACT.
 */
public class CompanyContactDao
        extends AbstractDao<eu.ginlo_apps.ginlo.greendao.CompanyContact, Long> {

    public static final String TABLENAME = "COMPANY_CONTACT";

    public CompanyContactDao(DaoConfig config) {
        super(config);
    }

    public CompanyContactDao(DaoConfig config,
                             AbstractDaoSession daoSession) {
        super(config, daoSession);
    }

    /**
     * Creates the underlying database table.
     */
    public static void createTable(Database db,
                                   boolean ifNotExists) {
        String constraint = ifNotExists ? "IF NOT EXISTS " : "";

        db.execSQL("CREATE TABLE " + constraint + "'COMPANY_CONTACT' ("  //
                + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                + "'ACCOUNT_GUID' TEXT,"  // 1: accountGuid
                + "'CHECKSUM' TEXT,"  // 2: checksum
                + "'KEY_IV' TEXT,"  // 3: key-iv
                + "'ENCRYPTED_DATA' BLOB,"  // 4: encryptedData
                + "'PUBLIC_KEY' TEXT,"  // 5: public key
                + "'GUID' TEXT,"  // 6: guid
                + "'ACCOUNT_ID' TEXT"  // 7: simsme id
                + ");");
    }

    /**
     * Drops the underlying database table.
     */
    public static void dropTable(SQLiteDatabase db,
                                 boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "'COMPANY_CONTACT'";

        db.execSQL(sql);
    }

    /**
     * @inheritdoc
     */
    @Override
    protected void bindValues(SQLiteStatement stmt,
                              eu.ginlo_apps.ginlo.greendao.CompanyContact entity) {
        stmt.clearBindings();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        String accountGuid = entity.getAccountGuid();

        if (accountGuid != null) {
            stmt.bindString(NumberConstants.INT_2, accountGuid);
        }

        String checksum = entity.getChecksum();

        if (checksum != null) {
            stmt.bindString(NumberConstants.INT_3, checksum);
        }

        String keyIv = entity.getKeyIv();

        if (keyIv != null) {
            stmt.bindString(NumberConstants.INT_4, keyIv);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(NumberConstants.INT_5, encryptedData);
        }

        String publicKey = entity.getPublicKey();

        if (publicKey != null) {
            stmt.bindString(NumberConstants.INT_6, publicKey);
        }

        String guid = entity.getGuid();

        if (guid != null) {
            stmt.bindString(NumberConstants.INT_7, guid);
        }

        String accountId = entity.getAccountId();

        if (accountId != null) {
            stmt.bindString(NumberConstants.INT_8, accountId);
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
    public eu.ginlo_apps.ginlo.greendao.CompanyContact readEntity(Cursor cursor,
                                                                 int offset) {

        return new eu.ginlo_apps.ginlo.greendao.CompanyContact(  //
                cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0),  // id
                cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1),  // accountGuid
                cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getString(offset + NumberConstants.INT_2),  // checksum
                cursor.isNull(offset + NumberConstants.INT_3) ? null : cursor.getString(offset + NumberConstants.INT_3),  // keyIv
                cursor.isNull(offset + NumberConstants.INT_4) ? null : cursor.getBlob(offset + NumberConstants.INT_4),  // encryptedData
                cursor.isNull(offset + NumberConstants.INT_5) ? null : cursor.getString(offset + NumberConstants.INT_5), // public key
                cursor.isNull(offset + NumberConstants.INT_6) ? null : cursor.getString(offset + NumberConstants.INT_6),  // guid
                cursor.isNull(offset + NumberConstants.INT_7) ? null : cursor.getString(offset + NumberConstants.INT_7)  // account id
        );
    }

    /**
     * @inheritdoc
     */
    @Override
    public void readEntity(Cursor cursor,
                           eu.ginlo_apps.ginlo.greendao.CompanyContact entity,
                           int offset) {
        entity.setId(cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0));
        entity.setAccountGuid(cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1));
        entity.setChecksum(cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getString(offset + NumberConstants.INT_2));
        entity.setKeyIv(cursor.isNull(offset + NumberConstants.INT_3) ? null : cursor.getString(offset + NumberConstants.INT_3));
        entity.setEncryptedData(cursor.isNull(offset + NumberConstants.INT_4) ? null : cursor.getBlob(offset + NumberConstants.INT_4));
        entity.setPublicKey(cursor.isNull(offset + NumberConstants.INT_5) ? null : cursor.getString(offset + NumberConstants.INT_5));
        entity.setGuid(cursor.isNull(offset + NumberConstants.INT_6) ? null : cursor.getString(offset + NumberConstants.INT_6));
        entity.setAccountId(cursor.isNull(offset + NumberConstants.INT_7) ? null : cursor.getString(offset + NumberConstants.INT_7));
    }

    @Override
    protected void bindValues(DatabaseStatement stmt, eu.ginlo_apps.ginlo.greendao.CompanyContact entity) {
        stmt.clearBindings();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        String accountGuid = entity.getAccountGuid();

        if (accountGuid != null) {
            stmt.bindString(NumberConstants.INT_2, accountGuid);
        }

        String checksum = entity.getChecksum();

        if (checksum != null) {
            stmt.bindString(NumberConstants.INT_3, checksum);
        }

        String keyIv = entity.getKeyIv();

        if (keyIv != null) {
            stmt.bindString(NumberConstants.INT_4, keyIv);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(NumberConstants.INT_5, encryptedData);
        }

        String publicKey = entity.getPublicKey();

        if (publicKey != null) {
            stmt.bindString(NumberConstants.INT_6, publicKey);
        }

        String guid = entity.getGuid();

        if (guid != null) {
            stmt.bindString(NumberConstants.INT_7, guid);
        }

        String accountId = entity.getAccountId();

        if (accountId != null) {
            stmt.bindString(NumberConstants.INT_8, accountId);
        }
    }

    /**
     * @inheritdoc
     */
    @Override
    protected Long updateKeyAfterInsert(eu.ginlo_apps.ginlo.greendao.CompanyContact entity,
                                        long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    /**
     * @inheritdoc
     */
    @Override
    public Long getKey(eu.ginlo_apps.ginlo.greendao.CompanyContact entity) {
        if (entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    protected boolean hasKey(CompanyContact entity) {
        return entity != null && entity.getId() != null;
    }

    public int countEntries(final ContactController.IndexType indexType) {
        Cursor cursor = null;
        try {
            SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            builder.setTables("COMPANY_CONTACT");

            String selection;
            String[] selectionArg;
            if (indexType.equals(ContactController.IndexType.INDEX_TYPE_COMPANY)) {
                selection = Properties.guid.columnName + " LIKE ?";
                selectionArg = new String[]{AppConstants.GUID_COMPANY_INDEX_ENTRY_PREFIX + "%"};
            } else {
                selection = Properties.guid.columnName + " IS NULL";
                selectionArg = null;
            }

            String query = builder.buildQuery(new String[]{"COUNT(*)"}, selection, null, null, null, null);

            cursor = db.rawQuery(query, selectionArg);

            if (cursor == null) {
                return -1;
            }

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            LogUtil.w("CompanyContactDao", "countEntries", e);
            return -1;
        } finally {
            StreamUtil.closeStream(cursor);
        }
        return -1;
    }

    /**
     * @inheritdoc
     */
    @Override
    protected boolean isEntityUpdateable() {
        return true;
    }

    /**
     * Properties of entity Contact.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {

        public static final Property Id = new Property(NumberConstants.INT_0, Long.class, "id", true, "_id");

        public static final Property AccountGuid = new Property(NumberConstants.INT_1, String.class, "accountGuid", false,
                "ACCOUNT_GUID");

        public static final Property checksum = new Property(NumberConstants.INT_2, String.class, "checksum", false, "CHECKSUM");

        public static final Property keyIv = new Property(NumberConstants.INT_3, String.class, "keyIv", false, "KEY_IV");

        public static final Property EncryptedData = new Property(NumberConstants.INT_4, byte[].class, "encryptedData", false,
                "ENCRYPTED_DATA");

        public static final Property publicKey = new Property(NumberConstants.INT_5, String.class, "publicKey", false, "PUBLIC_KEY");

        public static final Property guid = new Property(NumberConstants.INT_6, String.class, "guid", false, "GUID");

        public static final Property accountId = new Property(NumberConstants.INT_7, String.class, "accountId", false, "ACCOUNT_ID");
    }
}
