// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util.fts;

import androidx.annotation.NonNull;

import net.sqlcipher.DatabaseErrorHandler;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.io.File;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class FtsDatabaseOpenHelper implements net.sqlcipher.DatabaseErrorHandler, SQLiteDatabaseHook
{
   private final static String TAG = "FtsDatabaseOpenHelper";
   private final static String FTS_DATABASE = "fts_database.db";
   private final static int FTS_DB_VERSION = 1;
   private boolean mDbIsDecrypted;
   private final InnerDatabaseOpenHelper mOpenHelper;
   private SQLiteDatabase mDatabase;

   FtsDatabaseOpenHelper(@NonNull final SimsMeApplication application, @NonNull final SQLiteDatabase.CursorFactory cursorFactory)
   {
      mOpenHelper = new InnerDatabaseOpenHelper(application, null,this, this);
   }

   static boolean existsFtsDatabase(final SimsMeApplication application)
   {
      File dbPath = application.getDatabasePath(FTS_DATABASE);

      return dbPath != null && dbPath.exists();
   }

   public static boolean deleteFtsDatabase(final SimsMeApplication application)
   {
      File dbPath = application.getDatabasePath(FTS_DATABASE);

      if (dbPath.exists())
      {
         return dbPath.delete();
      }

      return false;
   }

   /**
    * Bei einen Fehler ist die Antwort null und der Fehler kann per {@link #getOpenError()} abgefragt werden.
    *
    * @param password password
    * @return true oder false wenn ein Fehler aufgetreten ist
    */
   boolean openWritableDatabase(@NonNull final String password)
   {
      mDatabase = mOpenHelper.getWritableDatabase(password);

      if (mDatabase != null)
      {
         if (mDatabase.isOpen())
         {
            mDbIsDecrypted = true;
            return true;
         }
         else
         {
            mDatabase = null;
            return false;
         }
      }

      return false;
   }

   /**
    *
    * @return LocalizedException Identifier
    */
   String getOpenError()
   {
      if (!mDbIsDecrypted)
      {
         return LocalizedException.DECRYPT_DATA_FAILED;
      }
      else if(!StringUtil.isNullOrEmpty(mOpenHelper.mOpenError))
      {
         return mOpenHelper.mOpenError;
      }

      return LocalizedException.DB_IS_NOT_READY;
   }

   void close()
   {
      mDatabase = null;
      mDbIsDecrypted = false;
      mOpenHelper.close();
   }

   boolean isDatabaseOpenAndDecrypted()
   {
      return mDatabase != null && mDatabase.isOpen() && mDbIsDecrypted;
   }

   public SQLiteDatabase getDatabase()
           throws LocalizedException
   {
      if (!isDatabaseOpenAndDecrypted())
      {
         throw new LocalizedException(LocalizedException.DB_IS_NOT_READY);
      }
      return mDatabase;
   }

   /**
    * Called immediately before opening the database.
    *
    * @param database database
    */
   @Override
   public void preKey(SQLiteDatabase database)
   {

   }

   /**
    * Called immediately after opening the database.
    *
    * @param database database
    */
   @Override
   public void postKey(SQLiteDatabase database)
   {
      LogUtil.d("FTS_OPEN", "after decrypt");
      mDbIsDecrypted = true;
   }

   /**
    * defines the method to be invoked when database corruption is detected.
    *
    * @param dbObj the {@link SQLiteDatabase} object representing the database on which corruption
    *              is detected.
    */
   @Override
   public void onCorruption(SQLiteDatabase dbObj)
   {

   }

   private static class InnerDatabaseOpenHelper extends SQLiteOpenHelper
   {

      private static final String FTS_TABLE_CREATE =
              "CREATE VIRTUAL TABLE " + FtsDatabaseHelper.FTS_CONTACTS_TABLE +
                      " USING fts3 (" +
                      FtsDatabaseHelper.COLUMN_ACCOUNT_GUID + ", " +
                      FtsDatabaseHelper.COLUMN_FIRST_NAME + ", " +
                      FtsDatabaseHelper.COLUMN_NAME + ", " +
                      FtsDatabaseHelper.COLUMN_CLASS_TYPE + ", " +
                      FtsDatabaseHelper.COLUMN_MANDANT + ", " +
                      FtsDatabaseHelper.COLUMN_JSON_ATTRIBUTES + ", " +
                      FtsDatabaseHelper.COLUMN_SEARCH_ATTRIBUTES + ")";

      private String mOpenError;

      InnerDatabaseOpenHelper(@NonNull final SimsMeApplication application, final SQLiteDatabase.CursorFactory cursorFactory, SQLiteDatabaseHook hook, DatabaseErrorHandler errorHandler)
      {
         super(application, FTS_DATABASE, cursorFactory, FTS_DB_VERSION, hook, errorHandler);
      }

      /**
       * Called when the database is created for the first time. This is where the
       * creation of tables and the initial population of the tables should happen.
       *
       * @param db The database.
       */
      @Override
      public void onCreate(SQLiteDatabase db)
      {
         try
         {
            LogUtil.d("FTS_OPEN", "onCreate()");

            db.execSQL(FTS_TABLE_CREATE);
         }
         catch (SQLException e)
         {
            LogUtil.e(TAG, "onCreate()", e);
            mOpenError = LocalizedException.DB_SQL_STATEMENT_FAILED;
         }
      }

      /**
       * Called when the database needs to be upgraded. The implementation
       * should use this method to drop tables, add tables, or do anything else it
       * needs to upgrade to the new schema version.
       *
       * <p>The SQLite ALTER TABLE documentation can be found
       * <a href="http://sqlite.org/lang_altertable.html">here</a>. If you add new columns
       * you can use ALTER TABLE to insert them into a live table. If you rename or remove columns
       * you can use ALTER TABLE to rename the old table, then create the new table and then
       * populate the new table with the contents of the old table.
       *
       * @param db         The database.
       * @param oldVersion The old database version.
       * @param newVersion The new database version.
       */
      @Override
      public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
      {
         LogUtil.d("FTS_OPEN", "onUpgrade()");
      }
   }
}
