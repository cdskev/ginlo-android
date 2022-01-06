// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.greendao;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DeviceDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import org.greenrobot.greendao.database.Database;

import java.util.ArrayList;

/**
 * Klasse, um eine SchemVersion auf die nächste SchemaVersion anzuheben (ohne
 * alle daten zu droppen)
 */
public class MigratingOpenHelper
        extends eu.ginlo_apps.ginlo.greendao.DaoMaster.OpenHelper {

    private final ArrayList<SQLException> mExceptionList;
    private boolean dbHasBeenUpdated = false;

    public MigratingOpenHelper(final Context context,
                               final String name,
                               final CursorFactory factory) {
        super(context, name, factory);
        mExceptionList = new ArrayList<>();
        setLoadSQLCipherNativeLibs(false);
    }

    @Override
    public void onCreate(final Database db) {
        LogUtil.i("greenDAO", "Creating tables for schema version " + eu.ginlo_apps.ginlo.greendao.DaoMaster.SCHEMA_VERSION);
        DaoMaster.createAllTables(db, false);
        createIndixes(db);
        createChannelIndize(db);
        createCompanyContactTableIndex(db);
        createDeviceTableIndex(db);
    }

    @Override
    public void onUpgrade(final Database db,
                          final int oldVersion,
                          final int newVersion) {
        LogUtil.i("greenDAO", "Upgrading schema from version " + oldVersion + " to " + newVersion + " ");
        dbHasBeenUpdated = true;

        if (oldVersion <= 56) {
            createIndixesForOldVersions(db);
        }

        if (oldVersion <= 57) //1.3
        {
            createChannelTable(db);
            createChannelIndize(db);
        }
        if (oldVersion <= 58) {
            updateChannelTable(db);
        }
        if (oldVersion <= 59) {
            updateChannelTablePromotion(db);
        }
        if (oldVersion <= 60) {
            createCategoryTable(db);
            updateChannelTableCategories(db);
        }
        if (oldVersion <= 61) {
            updateChannelTableNotification(db);
        }
        if (oldVersion <= 62) {
            updateChannelTableSettings(db);
        }
        if (oldVersion <= 63) //1.4
        {
            updateAccountTableState(db);
        }
        if (oldVersion <= 64) //1.5
        {
            updateChatTableLastMsgId(db);
        }

        if (oldVersion <= 65) // 1.5 Bugfix Release
        {
            createDestructionTable(db);
            createIndixes(db);
        }

        if (oldVersion <= 66) // 1.6
        {
            addIsDeletedToChannel(db);
        }

        if (oldVersion <= 67) //1.7
        {
            addNewMessageTableColumns(db);
            addLastChatModifiedDateToChat(db);
            addMandantToContact(db);
        }

        if (oldVersion <= 68) //1.8
        {
            addMessageAttibutesColumn(db);
        }

        if (oldVersion <= 69) //1.8 sprint 3
        {
            addSignatureSha256(db);
            addKey2(db);
        }

        if (oldVersion <= 70) //1.8 sprint 4
        {
            addImportance(db);
        }

        if (oldVersion <= 71) //1.8 sprint 5
        {
            createCompanyContactTable(db);
            createCompanyContactTableIndex(db);
        }

        if (oldVersion <= 72) //1.8
        {
            deleteOldInternalMessages(db);
        }

        if (oldVersion <= 73) //1.8.1
        {
            //Bugfix Bug 42596
            createCompanyContactTableIndex(db);
        }

        if (oldVersion <= 74) //1.9
        {
            addTypeToChannel(db);
        }
        /// 2.0 hatte schon die 75 daher weiter mit 76
        if (oldVersion <= 76) //2.1
        {
            updateAccountTableAccountID(db);
            createDeviceTable(db);
            createDeviceTableIndex(db);
            updateContactTablePrivateIndex(db);
        }

        if (oldVersion <= 77) //2.1
        {
            updateCompanyContactTableGuid(db);
        }

        if (oldVersion <= 78) // 2.1
        {
            updateCompanyContactTableAccountId(db);
        }

        if (oldVersion <= 79) // 2.3
        {
            createNotificationTable(db);
        }
    }

    @Override
    public void onDowngrade(final SQLiteDatabase db,
                            final int oldVersion,
                            final int newVersion) {
    }

    private void createIndixes(final Database db) {
        //
        final String[] createIndexes = new String[]
                {
                        "create index if not exists idx_account_idx1 on account(account_guid)",
                        "create index if not exists idx_chat_idx1 on chat(CHAT_GUID)",
                        "create index if not exists idx_contact_idx1 on contact(ACCOUNT_GUID)",
                        "create index if not exists idx_contact_idx2 on contact(LOOKUP_KEY)",
                        "create index if not exists idx_contact_idx3 on contact(hash)",
                        "create index if not exists idx_contact_idx4 on contact(bcrypt)",
                        "create index if not exists idx_contact_idx5 on contact(is_hidden)",
                        "create index if not exists idx_contact_idx6 on contact(IS_SIMS_ME_CONTACT)",
                        "create index if not exists idx_message_idx1 on message(guid)",
                        "create index if not exists idx_message_idx2 on message('from')",
                        "create index if not exists idx_message_idx3 on message('to')",
                        "create index if not exists idx_message_idx4 on message(REQUEST_GUID)",
                        "create index if not exists idx_message_idx5 on message(DATE_SEND)",
                        "create index if not exists idx_message_idx6 on message(DATE_SEND_CONFIRM)",
                        "create index if not exists idx_destruction_date_idx1 on NEW_DESTRUCTION_DATE(GUID)"
                };

        for (String index : createIndexes) {
            try {
                db.execSQL(index);
            } catch (SQLException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage());
                mExceptionList.add(e);
            }
        }
    }

    private void createIndixesForOldVersions(final Database db) {
        // Fix fuer BUG 38196 - es wurde auch ein Index fuer NEW_DESTRUCTION_DATE gebaut, die Tabelle existierte aber noch nicht
        final String[] createIndexes = new String[]
                {
                        "create index if not exists idx_account_idx1 on account(account_guid)",
                        "create index if not exists idx_chat_idx1 on chat(CHAT_GUID)",
                        "create index if not exists idx_contact_idx1 on contact(ACCOUNT_GUID)",
                        "create index if not exists idx_contact_idx2 on contact(LOOKUP_KEY)",
                        "create index if not exists idx_contact_idx3 on contact(hash)",
                        "create index if not exists idx_contact_idx4 on contact(bcrypt)",
                        "create index if not exists idx_contact_idx5 on contact(is_hidden)",
                        "create index if not exists idx_contact_idx6 on contact(IS_SIMS_ME_CONTACT)",
                        "create index if not exists idx_message_idx1 on message(guid)",
                        "create index if not exists idx_message_idx2 on message('from')",
                        "create index if not exists idx_message_idx3 on message('to')",
                        "create index if not exists idx_message_idx4 on message(REQUEST_GUID)",
                        "create index if not exists idx_message_idx5 on message(DATE_SEND)",
                        "create index if not exists idx_message_idx6 on message(DATE_SEND_CONFIRM)"
                };

        for (String index : createIndexes) {
            try {
                db.execSQL(index);
            } catch (SQLException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage());
                mExceptionList.add(e);
            }
        }
    }

    private void createChannelIndize(final Database db) {
        try {
            db.execSQL("create index if not exists idx_channel_idx1 on channel(guid)");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateChannelTable(final Database db) {
        try {
            db.execSQL("alter table CHANNEL add column 'SHORT_LINK_TEXT' TEXT");
            // Channels neu laden ...
            db.execSQL("update CHANNEL set CHECKSUM = ''");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateChannelTablePromotion(final Database db) {
        try {
            db.execSQL("alter table CHANNEL add column 'PROMOTION' INTEGER");
            db.execSQL("alter table CHANNEL add column 'EXTERNAL_URL' TEXT");

            // Channels neu laden ...
            db.execSQL("update CHANNEL set CHECKSUM = ''");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createCategoryTable(final Database db) {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS 'CHANNEL_CATEGORY' ("  //
                    + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                    + "'IDENT' TEXT,"  // 1: ident
                    + "'TITLE_KEY' TEXT,"  // 2: titleKey
                    + "'IMAGE_KEY' TEXT,"  // 3: imageKey
                    + "'ITEMS' TEXT);");  // 4: items
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createDestructionTable(final Database db) {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS 'NEW_DESTRUCTION_DATE' (" + //
                    "'_id' INTEGER PRIMARY KEY ," + // 0: id
                    "'GUID' TEXT," + // 1: guid
                    "'NEW_DESTRUCTION_DATE' INTEGER);");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateChannelTableCategories(final Database db) {
        try {
            db.execSQL("alter table CHANNEL add column 'SEARCH_TEXT' TEXT");
            db.execSQL("alter table CHANNEL add column 'CATEGORY' TEXT");

            // Channels neu laden ...
            db.execSQL("update CHANNEL set CHECKSUM = ''");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateChannelTableNotification(final Database db) {
        try {
            db.execSQL("alter table CHANNEL add column 'DISABLE_NOTIFICATION' INTEGER");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateChannelTableSettings(final Database db) {
        try {
            db.execSQL("alter table CHANNEL add column 'WELCOME_TEXT' TEXT");
            db.execSQL("alter table CHANNEL add column 'SUGGESTION_TEXT' TEXT");
            db.execSQL("alter table CHANNEL add column 'FEEDBACK_CONTACT' TEXT");

            // Channels neu laden ...
            db.execSQL("update CHANNEL set CHECKSUM = ''");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateAccountTableState(final Database db) {
        // status fuer account einfuegen
        try {
            db.execSQL("alter table ACCOUNT add column 'STATE' INTEGER");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateChatTableLastMsgId(final Database db) {
        // letzte Message am Chat merken
        try {
            db.execSQL("alter table CHAT add column 'LAST_MSG_ID' INTEGER");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createChannelTable(final Database db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS 'CHANNEL' ("  //
                + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                + "'GUID' TEXT,"  // 1: guid
                + "'SHORT_DESC' TEXT,"  // 2: shortDesc
                + "'CHANNEL_JSON_OBJECT' TEXT,"  // 3: channelJsonObject
                + "'FILTER_JSON_OBJECT' TEXT,"  // 4: filterJsonObject
                + "'CHECKSUM' TEXT,"  // 5: checksum
                + "'IS_SUBSCRIBED' INTEGER,"  // 6: isSubscribed
                + "'AES_KEY' TEXT,"  // 7: aesKey
                + "'IV' TEXT );");  // 8: iv
    }

    private void addIsDeletedToChannel(final Database db) {
        // letzte Message am Chat merken
        try {
            db.execSQL("alter table CHANNEL add column 'IS_DELETED' INTEGER");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addNewMessageTableColumns(final Database db) {
        // getimte Nachrichten
        // signature Pruefung
        // prefetched Date
        try {
            db.execSQL("alter table MESSAGE add column 'DATE_PREFETCHED_PERSISTENCE' INTEGER");
            db.execSQL("alter table MESSAGE add column 'DATE_SEND_TIMED' INTEGER");
            db.execSQL("alter table MESSAGE add column 'IS_SIGNATURE_VALID' INTEGER");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addLastChatModifiedDateToChat(final Database db) {
        try {
            db.execSQL("alter table CHAT add column 'LAST_MODIFIED_DATE' INTEGER");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addMandantToContact(final Database db) {
        try {
            db.execSQL("alter table CONTACT add column 'MANDANT' TEXT");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addMessageAttibutesColumn(final Database db) {
        // attributes
        try {
            db.execSQL("alter table MESSAGE add column 'ATTRIBUTES' TEXT");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addSignatureSha256(final Database db) {
        // attributes
        try {
            db.execSQL("alter table MESSAGE add column 'SIGNATURE_SHA256' BLOB");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addKey2(final Database db) {
        // attributes
        try {
            db.execSQL("alter table MESSAGE add column 'ENCRYPTED_FROM_KEY2' TEXT");
            db.execSQL("alter table MESSAGE add column 'ENCRYPTED_TO_KEY2' TEXT");
            db.execSQL("alter table MESSAGE add column 'KEY2_IV' TEXT");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addImportance(final Database db) {
        try {
            db.execSQL("alter table MESSAGE add column 'IMPORTANCE' TEXT");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createCompanyContactTable(final Database db) {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS 'COMPANY_CONTACT' ("  //
                    + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                    + "'ACCOUNT_GUID' TEXT,"  // 1: accountGuid
                    + "'CHECKSUM' TEXT,"  // 2: checksum
                    + "'KEY_IV' TEXT,"  // 3: key-iv
                    + "'ENCRYPTED_DATA' BLOB," // 4: encryptedData
                    + "'PUBLIC_KEY' TEXT"  // 5: public key
                    + ");");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createCompanyContactTableIndex(final Database db) {
        try {
            db.execSQL("create index if not exists idx_company_contact_idx1 on COMPANY_CONTACT(ACCOUNT_GUID)");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void deleteOldInternalMessages(final Database db) {
        try {
            db.execSQL("delete from MESSAGE where TYPE = 2 and READ = 1");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void addTypeToChannel(final Database db) {
        try {
            db.execSQL("alter table CHANNEL add column 'TYPE' TEXT");
            db.execSQL("update CHANNEL set TYPE = '" + Channel.TYPE_CHANNEL + "'");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateAccountTableAccountID(final Database db) {
        // status fuer account einfuegen
        try {
            db.execSQL("alter table ACCOUNT add column 'ACCOUNT_ID' TEXT");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createDeviceTable(final Database db) {
        try {
            eu.ginlo_apps.ginlo.greendao.DeviceDao.createTable(db, false);
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createDeviceTableIndex(final Database db) {
        db.execSQL("create index if not exists idx_device_idx1 on " + eu.ginlo_apps.ginlo.greendao.DeviceDao.TABLENAME + "(ACCOUNT_GUID)");
        db.execSQL("create index if not exists idx_device_idx2 on " + DeviceDao.TABLENAME + "(GUID)");
    }

    private void updateContactTablePrivateIndex(final Database db) {
        try {
            db.execSQL("alter table CONTACT add column 'CHECKSUM' TEXT");
            db.execSQL("alter table CONTACT add column 'ATTRIBUTES' TEXT");
            db.execSQL("alter table CONTACT add column 'VERSION' INTEGER");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateCompanyContactTableGuid(final Database db) {
        try {
            db.execSQL("alter table COMPANY_CONTACT add column 'GUID' TEXT");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void updateCompanyContactTableAccountId(final Database db) {
        try {
            db.execSQL("alter table COMPANY_CONTACT add column 'ACCOUNT_ID' TEXT");
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    private void createNotificationTable(final Database db) {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS 'NOTIFICATION' ("  //
                    + "'_id' INTEGER PRIMARY KEY ,"  // 0: id
                    + "'MESSAGE_GUID' TEXT);");  // 1: MESSAGE_GUID
        } catch (SQLException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage());
            mExceptionList.add(e);
        }
    }

    public boolean getDbHasBeenUpdated() {
        return dbHasBeenUpdated;
    }

    /**
     * getUpdateExceptions
     *
     * @return
     */
    public ArrayList<SQLException> getUpdateExceptions() {
        return new ArrayList<>(mExceptionList);
    }
}
