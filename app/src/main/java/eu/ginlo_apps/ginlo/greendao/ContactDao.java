// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.model.constant.NumberConstants;
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
public class ContactDao
        extends AbstractDao<eu.ginlo_apps.ginlo.greendao.Contact, Long> {

    public static final String TABLENAME = "CONTACT";

    public ContactDao(DaoConfig config) {
        super(config);
    }

    public ContactDao(DaoConfig config,
                      AbstractDaoSession daoSession) {
        super(config, daoSession);
    }

    /**
     * Creates the underlying database table.
     */
    public static void createTable(Database db,
                                   boolean ifNotExists) {
        String constraint = ifNotExists ? "IF NOT EXISTS " : "";

        db.execSQL("CREATE TABLE " + constraint + "'CONTACT' ("  //
                + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                + "'ACCOUNT_GUID' TEXT,"  // 1: accountGuid
                + "'LOOKUP_KEY' TEXT,"  // 2: lookupKey
                + "'HASH' TEXT,"  // 3: hash
                + "'BCRYPT' TEXT,"  // 4: bcrypt
                + "'PUBLIC_KEY' TEXT,"  // 5: publicKey
                + "'TIMESTAMP' INTEGER,"  // 6: timestamp
                + "'LAST_KNOWN_ROW_ID' INTEGER,"  // 7: lastKnownRowId
                + "'IS_HIDDEN' INTEGER,"  // 8: isHidden
                + "'IS_FIRST_CONTACT' INTEGER,"  // 9: isFirstContact
                + "'IS_BLOCKED' INTEGER,"  // 10: isBlocked
                + "'IS_SIMS_ME_CONTACT' INTEGER,"  // 11: isSimsMeContact
                + "'ENCRYPTED_DATA' BLOB,"  // 12: encryptedData
                + "'IV' BLOB," // 13: iv
                + "'MANDANT' TEXT," //14: mandant
                + "'CHECKSUM' TEXT," //15: checksum
                + "'ATTRIBUTES' TEXT," //16: attributes
                + "'VERSION' INTEGER" //17: version
                + ");");
    }

    /**
     * Drops the underlying database table.
     */
    public static void dropTable(SQLiteDatabase db,
                                 boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "'CONTACT'";

        db.execSQL(sql);
    }

    /**
     * @inheritdoc
     */
    @Override
    protected void bindValues(SQLiteStatement stmt,
                              eu.ginlo_apps.ginlo.greendao.Contact entity) {
        stmt.clearBindings();
        entity.beforeSafe();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        String accountGuid = entity.getAccountGuid();

        if (accountGuid != null) {
            stmt.bindString(NumberConstants.INT_2, accountGuid);
        }

        String lookupKey = entity.getLookupKey();

        if (lookupKey != null) {
            stmt.bindString(NumberConstants.INT_3, lookupKey);
        }

        String hash = entity.getHash();

        if (hash != null) {
            stmt.bindString(NumberConstants.INT_4, hash);
        }

        String bcrypt = entity.getBcrypt();

        if (bcrypt != null) {
            stmt.bindString(NumberConstants.INT_5, bcrypt);
        }

        String publicKey = entity.getPublicKey();

        if (publicKey != null) {
            stmt.bindString(NumberConstants.INT_6, publicKey);
        }

        Long timestamp = entity.getTimestamp();

        if (timestamp != null) {
            stmt.bindLong(NumberConstants.INT_7, timestamp);
        }

        Long lastKnownRowId = entity.getLastKnownRowId();

        if (lastKnownRowId != null) {
            stmt.bindLong(NumberConstants.INT_8, lastKnownRowId);
        }

        Boolean isHidden = entity.getIsHidden();

        if (isHidden != null) {
            stmt.bindLong(NumberConstants.INT_9, isHidden ? 1L : 0L);
        }

        Boolean isFirstContact = entity.getIsFirstContact();

        if (isFirstContact != null) {
            stmt.bindLong(NumberConstants.INT_10, isFirstContact ? 1L : 0L);
        }

        Boolean isBlocked = entity.getIsBlocked();

        if (isBlocked != null) {
            stmt.bindLong(NumberConstants.INT_11, isBlocked ? 1L : 0L);
        }

        Boolean isSimsMeContact = entity.getIsSimsMeContact();

        if (isSimsMeContact != null) {
            stmt.bindLong(NumberConstants.INT_12, isSimsMeContact ? 1L : 0L);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(13, encryptedData);
        }

        byte[] iv = entity.getIv();

        if (iv != null) {
            stmt.bindBlob(14, iv);
        }

        String mandant = entity.getMandant();

        if (mandant != null) {
            stmt.bindString(NumberConstants.INT_15, mandant);
        }

        String checksum = entity.getChecksum();

        if (checksum != null) {
            stmt.bindString(NumberConstants.INT_16, checksum);
        }

        String attributes = entity.getAttributes();

        if (attributes != null) {
            stmt.bindString(NumberConstants.INT_17, attributes);
        }

        Long version = entity.getVersion();

        if (version != null) {
            stmt.bindLong(NumberConstants.INT_18, version);
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
    public eu.ginlo_apps.ginlo.greendao.Contact readEntity(Cursor cursor,
                                                          int offset) {
        eu.ginlo_apps.ginlo.greendao.Contact entity = new eu.ginlo_apps.ginlo.greendao.Contact(  //
                cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0),  // id
                cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1),  // accountGuid
                cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getString(offset + NumberConstants.INT_2),  // lookupKey
                cursor.isNull(offset + NumberConstants.INT_3) ? null : cursor.getString(offset + NumberConstants.INT_3),  // hash
                cursor.isNull(offset + NumberConstants.INT_4) ? null : cursor.getString(offset + NumberConstants.INT_4),  // bcrypt
                cursor.isNull(offset + NumberConstants.INT_5) ? null : cursor.getString(offset + NumberConstants.INT_5),  // publicKey
                cursor.isNull(offset + NumberConstants.INT_6) ? null : cursor.getLong(offset + NumberConstants.INT_6),  // timestamp
                cursor.isNull(offset + NumberConstants.INT_7) ? null : cursor.getLong(offset + NumberConstants.INT_7),  // lastKnownRowId
                cursor.isNull(offset + NumberConstants.INT_8) ? null : (cursor.getShort(offset + NumberConstants.INT_8) != 0),  // isHidden
                cursor.isNull(offset + NumberConstants.INT_9) ? null : (cursor.getShort(offset + NumberConstants.INT_9) != 0),  // isFirstContact
                cursor.isNull(offset + NumberConstants.INT_10) ? null : (cursor.getShort(offset + NumberConstants.INT_10) != 0),  // isBlocked
                cursor.isNull(offset + NumberConstants.INT_11) ? null : (cursor.getShort(offset + NumberConstants.INT_11) != 0),  // isSimsMeContact
                cursor.isNull(offset + NumberConstants.INT_12) ? null : cursor.getBlob(offset + NumberConstants.INT_12),  // encryptedData
                cursor.isNull(offset + NumberConstants.INT_13) ? null : cursor.getBlob(offset + NumberConstants.INT_13),  // iv
                cursor.isNull(offset + NumberConstants.INT_14) ? null : cursor.getString(offset + NumberConstants.INT_14),  // mandant
                cursor.isNull(offset + NumberConstants.INT_15) ? null : cursor.getString(offset + NumberConstants.INT_15),  // checksum
                cursor.isNull(offset + NumberConstants.INT_16) ? null : cursor.getString(offset + NumberConstants.INT_16),  // attributes
                cursor.isNull(offset + NumberConstants.INT_17) ? null : cursor.getLong(offset + NumberConstants.INT_17) //version
        );
        return entity;
    }

    /**
     * @inheritdoc
     */
    @Override
    public void readEntity(Cursor cursor,
                           eu.ginlo_apps.ginlo.greendao.Contact entity,
                           int offset) {
        entity.setId(cursor.isNull(offset + NumberConstants.INT_0) ? null : cursor.getLong(offset + NumberConstants.INT_0));
        entity.setAccountGuid(cursor.isNull(offset + NumberConstants.INT_1) ? null : cursor.getString(offset + NumberConstants.INT_1));
        entity.setLookupKey(cursor.isNull(offset + NumberConstants.INT_2) ? null : cursor.getString(offset + NumberConstants.INT_2));
        entity.setHash(cursor.isNull(offset + NumberConstants.INT_3) ? null : cursor.getString(offset + NumberConstants.INT_3));
        entity.setBcrypt(cursor.isNull(offset + NumberConstants.INT_4) ? null : cursor.getString(offset + NumberConstants.INT_4));
        entity.setPublicKey(cursor.isNull(offset + NumberConstants.INT_5) ? null : cursor.getString(offset + NumberConstants.INT_5));
        entity.setTimestamp(cursor.isNull(offset + NumberConstants.INT_6) ? null : cursor.getLong(offset + NumberConstants.INT_6));
        entity.setLastKnownRowId(cursor.isNull(offset + NumberConstants.INT_7) ? null : cursor.getLong(offset + NumberConstants.INT_7));
        entity.setIsHidden(cursor.isNull(offset + NumberConstants.INT_8) ? null : (cursor.getShort(offset + NumberConstants.INT_8) != 0));
        entity.setIsFirstContact(cursor.isNull(offset + NumberConstants.INT_9) ? null : (cursor.getShort(offset + NumberConstants.INT_9) != 0));
        entity.setIsBlocked(cursor.isNull(offset + NumberConstants.INT_10) ? null : (cursor.getShort(offset + NumberConstants.INT_10) != 0));
        entity.setIsSimsMeContact(cursor.isNull(offset + NumberConstants.INT_11) ? null : (cursor.getShort(offset + NumberConstants.INT_11) != 0));
        entity.setEncryptedData(cursor.isNull(offset + NumberConstants.INT_12) ? null : cursor.getBlob(offset + NumberConstants.INT_12));
        entity.setIv(cursor.isNull(offset + NumberConstants.INT_13) ? null : cursor.getBlob(offset + NumberConstants.INT_13));
        entity.setMandant(cursor.isNull(offset + NumberConstants.INT_14) ? null : cursor.getString(offset + NumberConstants.INT_14));
        entity.setChecksum(cursor.isNull(offset + NumberConstants.INT_15) ? null : cursor.getString(offset + NumberConstants.INT_15));
        entity.setAttributes(cursor.isNull(offset + NumberConstants.INT_16) ? null : cursor.getString(offset + NumberConstants.INT_16));
        entity.setVersion(cursor.isNull(offset + NumberConstants.INT_17) ? null : cursor.getLong(offset + NumberConstants.INT_17));
    }

    @Override
    protected void bindValues(DatabaseStatement stmt, eu.ginlo_apps.ginlo.greendao.Contact entity) {
        stmt.clearBindings();
        entity.beforeSafe();

        Long id = entity.getId();

        if (id != null) {
            stmt.bindLong(NumberConstants.INT_1, id);
        }

        String accountGuid = entity.getAccountGuid();

        if (accountGuid != null) {
            stmt.bindString(NumberConstants.INT_2, accountGuid);
        }

        String lookupKey = entity.getLookupKey();

        if (lookupKey != null) {
            stmt.bindString(NumberConstants.INT_3, lookupKey);
        }

        String hash = entity.getHash();

        if (hash != null) {
            stmt.bindString(NumberConstants.INT_4, hash);
        }

        String bcrypt = entity.getBcrypt();

        if (bcrypt != null) {
            stmt.bindString(NumberConstants.INT_5, bcrypt);
        }

        String publicKey = entity.getPublicKey();

        if (publicKey != null) {
            stmt.bindString(NumberConstants.INT_6, publicKey);
        }

        Long timestamp = entity.getTimestamp();

        if (timestamp != null) {
            stmt.bindLong(NumberConstants.INT_7, timestamp);
        }

        Long lastKnownRowId = entity.getLastKnownRowId();

        if (lastKnownRowId != null) {
            stmt.bindLong(NumberConstants.INT_8, lastKnownRowId);
        }

        Boolean isHidden = entity.getIsHidden();

        if (isHidden != null) {
            stmt.bindLong(NumberConstants.INT_9, isHidden ? 1L : 0L);
        }

        Boolean isFirstContact = entity.getIsFirstContact();

        if (isFirstContact != null) {
            stmt.bindLong(NumberConstants.INT_10, isFirstContact ? 1L : 0L);
        }

        Boolean isBlocked = entity.getIsBlocked();

        if (isBlocked != null) {
            stmt.bindLong(NumberConstants.INT_11, isBlocked ? 1L : 0L);
        }

        Boolean isSimsMeContact = entity.getIsSimsMeContact();

        if (isSimsMeContact != null) {
            stmt.bindLong(NumberConstants.INT_12, isSimsMeContact ? 1L : 0L);
        }

        byte[] encryptedData = entity.getEncryptedData();

        if (encryptedData != null) {
            stmt.bindBlob(NumberConstants.INT_13, encryptedData);
        }

        byte[] iv = entity.getIv();

        if (iv != null) {
            stmt.bindBlob(NumberConstants.INT_14, iv);
        }

        String mandant = entity.getMandant();

        if (mandant != null) {
            stmt.bindString(NumberConstants.INT_15, mandant);
        }
        String checksum = entity.getChecksum();

        if (checksum != null) {
            stmt.bindString(NumberConstants.INT_16, checksum);
        }

        String attributes = entity.getAttributes();

        if (attributes != null) {
            stmt.bindString(NumberConstants.INT_17, attributes);
        }

        Long version = entity.getVersion();

        if (version != null) {
            stmt.bindLong(NumberConstants.INT_18, version);
        }
    }

    /**
     * @inheritdoc
     */
    @Override
    protected Long updateKeyAfterInsert(eu.ginlo_apps.ginlo.greendao.Contact entity,
                                        long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    /**
     * @inheritdoc
     */
    @Override
    public Long getKey(eu.ginlo_apps.ginlo.greendao.Contact entity) {
        if (entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    protected boolean hasKey(Contact entity) {
        if (entity != null && entity.getId() != null) {
            return true;
        }
        return false;
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

        public static final Property LookupKey = new Property(NumberConstants.INT_2, String.class, "lookupKey", false, "LOOKUP_KEY");

        public static final Property Hash = new Property(NumberConstants.INT_3, String.class, "hash", false, "HASH");

        public static final Property Bcrypt = new Property(NumberConstants.INT_4, String.class, "bcrypt", false, "BCRYPT");

        public static final Property PublicKey = new Property(NumberConstants.INT_5, String.class, "publicKey", false, "PUBLIC_KEY");

        public static final Property Timestamp = new Property(NumberConstants.INT_6, Long.class, "timestamp", false, "TIMESTAMP");

        public static final Property LastKnownRowId = new Property(NumberConstants.INT_7, Long.class, "lastKnownRowId", false,
                "LAST_KNOWN_ROW_ID");

        public static final Property IsHidden = new Property(NumberConstants.INT_8, Boolean.class, "isHidden", false, "IS_HIDDEN");

        public static final Property IsFirstContact = new Property(NumberConstants.INT_9, Boolean.class, "isFirstContact", false,
                "IS_FIRST_CONTACT");

        public static final Property IsBlocked = new Property(NumberConstants.INT_10, Boolean.class, "isBlocked", false, "IS_BLOCKED");

        public static final Property IsSimsMeContact = new Property(NumberConstants.INT_11, Boolean.class, "isSimsMeContact", false,
                "IS_SIMS_ME_CONTACT");

        public static final Property EncryptedData = new Property(NumberConstants.INT_12, byte[].class, "encryptedData", false,
                "ENCRYPTED_DATA");

        public static final Property Iv = new Property(NumberConstants.INT_13, byte[].class, "iv", false, "IV");

        public static final Property Mandant = new Property(NumberConstants.INT_14, String.class, "mandant", false, "MANDANT");

        public static final Property Checksum = new Property(NumberConstants.INT_15, String.class, "checksum", false, "CHECKSUM");

        public static final Property Attributes = new Property(NumberConstants.INT_16, String.class, "attributes", false, "ATTRIBUTES");

        public static final Property Version = new Property(NumberConstants.INT_17, Long.class, "version", false, "VERSION");
    }
}
