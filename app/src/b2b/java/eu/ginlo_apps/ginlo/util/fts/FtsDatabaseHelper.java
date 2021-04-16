// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util.fts;

import android.content.ContentValues;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteCursorDriver;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteQuery;
import net.sqlcipher.database.SQLiteQueryBuilder;

import java.util.HashMap;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class FtsDatabaseHelper
{
   final static String FTS_CONTACTS_TABLE = "CONTACTS";
   public final static String COLUMN_SEARCH_ATTRIBUTES = "SEARCH_ATTRIBUTES";
   public final static String COLUMN_JSON_ATTRIBUTES = "JSON_ATTRIBUTES";
   public final static String COLUMN_NAME = "NAME";
   public final static String COLUMN_FIRST_NAME = "FIRSTNAME";
   public final static String COLUMN_ACCOUNT_GUID = "ACCOUNT_GUID";
   public final static String COLUMN_SIMSME_ID = "SIMSME_ID";
   public final static String COLUMN_CLASS_TYPE = "CLASS_TYPE";
   public final static String COLUMN_MANDANT = "MANDANT";
   public final static String COLUMN_ROW_ID = "rowid";
   private final static String[] ILLEGAL_CHAR = new String[]{"\"", "\\", "(", ")", "*"};

   private final SimsMeApplication mApplication;
   private CursorFactory mCursorFactory;
   private final FtsDatabaseOpenHelper mOpenHelper;
   private static FtsDatabaseHelper instance;

   public interface FtsDatabaseOpenListener
   {
      void ftsDatabaseIsOpen();
      void ftsDatabaseHasError(LocalizedException e);
   }

   public static FtsDatabaseHelper getInstance()
   {
      if (instance == null)
      {
         instance = new FtsDatabaseHelper(SimsMeApplication.getInstance());
      }
      return instance;
   }

   private FtsDatabaseHelper(@NonNull final SimsMeApplication application)
   {
      mApplication = application;
      SQLiteDatabase.loadLibs(mApplication);

      mCursorFactory = new CursorFactory();
      mOpenHelper = new FtsDatabaseOpenHelper(application, mCursorFactory);
   }

   public static boolean existsFtsDatabase(final SimsMeApplication application)
   {
      return FtsDatabaseOpenHelper.existsFtsDatabase(application);
   }

   public void openFtsDatabase(@NonNull final FtsDatabaseOpenListener listener)
   {
      if(mOpenHelper.isDatabaseOpenAndDecrypted())
      {
         listener.ftsDatabaseIsOpen();
         return;
      }

      DbOpenerTask task = new DbOpenerTask(mOpenHelper, listener);
      task.executeOnExecutor(DbOpenerTask.THREAD_POOL_EXECUTOR);
   }

   public void createFtsDatabase(@NonNull final String initialPassword)
           throws LocalizedException
   {
      if (FtsDatabaseHelper.existsFtsDatabase(mApplication))
      {
         throw new LocalizedException(LocalizedException.DB_ALREADY_EXISTS);
      }

      if (!mOpenHelper.openWritableDatabase(initialPassword))
      {
         throw new LocalizedException(mOpenHelper.getOpenError());
      }
   }

   public void closeFtsDatabase()
   {
      mOpenHelper.close();
   }

   public boolean isDatabaseOpenAndDecrypted()
   {
      return mOpenHelper.isDatabaseOpenAndDecrypted();
   }

   /**
    *
    * @param contentValues keys {@link #COLUMN_ACCOUNT_GUID}, {@link #COLUMN_FIRST_NAME}, usw....
    *
    * @throws LocalizedException exception
    */
   public void insertContact(@NonNull final ContentValues contentValues)
         throws LocalizedException
   {
      synchronized (this)
      {
         mOpenHelper.getDatabase().insert(FTS_CONTACTS_TABLE, null, contentValues);
      }
   }

   /**
    * Liefert einen Treffer zurück. Wenn mehr als ein Treffer gefunden wurde, dann wird eine Exception geworfen
    *
    * @param selection Abfrage Spalte z.B. {@link #COLUMN_ACCOUNT_GUID}
    * @param query such text
    * @param columns Rueckgabe Spalten
    * @return map mit Spalten(key) und Wert(Value) aus DB
    * @throws LocalizedException wenn es mehr als ein Treffer gibt #LocalizedException.
    */
   public HashMap<String, String> getUniqueEntry(@NonNull final String selection, @NonNull final String query, @NonNull final String[] columns)
           throws LocalizedException
   {
      SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
      builder.setTables(FTS_CONTACTS_TABLE);

      final String innerSelection = selection + " MATCH ?";
      final String[] selectionArgs = new String[] {query};

      Cursor cursor = builder.query(mOpenHelper.getDatabase(), columns, innerSelection, selectionArgs, null, null, null);

      try
      {
         if (cursor == null)
         {
            return null;
         }
         else if (cursor.getCount() > 1)
         {
            throw new LocalizedException(LocalizedException.NO_UNIQUE_RESULT);
         }
         else if (!cursor.moveToFirst())
         {
            cursor.close();
            return null;
         }
         else
         {
            HashMap<String, String> result = new HashMap<>(columns.length);
            for (int i = 0; i < columns.length; i++)
            {
               String value = cursor.getString(i);
               result.put(columns[i], value);
            }

            return result;
         }
      }
      finally
      {
         StreamUtil.closeStream(cursor);
      }
   }

   /**
    * Liefert einen Cursor zurück
    *
    * @param selection Abfrage Spalte z.B. {@link #COLUMN_ACCOUNT_GUID}
    * @param query such text
    * @param columns Rueckgabe Spalten
    * @param contactClassType null oder
    * @return Cursor
    * @throws LocalizedException exception
    */
   public Cursor searchEntries(@NonNull final String selection, @NonNull final String query, @NonNull final String[] columns, @Nullable String groupByColumn, @Nullable String sortOrderColumn, boolean sortOrderDesc, @Nullable String contactClassType)
      throws LocalizedException
   {
      SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
      builder.setTables(FTS_CONTACTS_TABLE);

      final String innerSelection;
      final String[] selectionArgs;

      String sortOrder = null;

      if (!StringUtil.isNullOrEmpty(sortOrderColumn))
      {
         sortOrder = sortOrderColumn + " " + (sortOrderDesc ? "DESC" : "ASC");
      }

      String innerQuery = query;

      for (String illegal: ILLEGAL_CHAR)
      {
         innerQuery = innerQuery.replace(illegal, "");
      }

      if (!StringUtil.isNullOrEmpty(contactClassType))
      {
         innerSelection = selection + " MATCH ?";
         selectionArgs = new String[] {"\"" + innerQuery + "*\" " + contactClassType + "*"};
      }
      else
      {
         innerSelection = selection + " MATCH ?";
         selectionArgs = new String[] {"\"" + innerQuery + "*\""};
      }

      if(innerQuery.length() < 1)
      {
         throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "query is < 1");
      }

      return builder.query(mOpenHelper.getDatabase(), columns, innerSelection, selectionArgs, groupByColumn, null, sortOrder);
   }

   public void deleteEntry(@NonNull final String selection, @NonNull final String query)
           throws LocalizedException
   {
      final String innerSelection = selection + " MATCH ?";
      final String[] selectionArgs = new String[] {query};
      mOpenHelper.getDatabase().delete(FTS_CONTACTS_TABLE, innerSelection, selectionArgs);
   }

   private class CursorFactory implements SQLiteDatabase.CursorFactory
   {
      /**
       * See
       * {@link net.sqlcipher.database.SQLiteCursor#SQLiteCursor(SQLiteDatabase, SQLiteCursorDriver, String, SQLiteQuery)}.
       *
       * @param db database
       * @param masterQuery  master query
       * @param editTable edit table
       * @param query query
       */
      @Override
      public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query)
      {
         return null;
      }
   }

   private static class DbOpenerTask extends AsyncTask<Void, Void, LocalizedException>
   {
      final FtsDatabaseOpenListener mListener;
      final FtsDatabaseOpenHelper mHelper;

      DbOpenerTask(@NonNull FtsDatabaseOpenHelper helper, @NonNull final FtsDatabaseOpenListener listener)
      {
         mListener = listener;
         mHelper = helper;
      }

      /**
       * Override this method to perform a computation on a background thread. The
       * specified parameters are the parameters passed to {@link #execute}
       * by the caller of this task.
       * <p>
       * This method can call {@link #publishProgress} to publish updates
       * on the UI thread.
       *
       * @param voids The parameters of the task.
       * @return A result, defined by the subclass of this task.
       * @see #onPreExecute()
       * @see #onPostExecute
       * @see #publishProgress
       */
      @Override
      protected LocalizedException doInBackground(Void... voids)
      {
         try
         {
            String encryptedPassword = SimsMeApplication.getInstance().getPreferencesController().getEncryptedFtsDatabasePassword();
            if (StringUtil.isNullOrEmpty(encryptedPassword))
            {
               return new LocalizedException(LocalizedException.NO_DATA_FOUND, "encrypted ftsdatabase Password is null");
            }

            String password = SimsMeApplication.getInstance().getKeyController().decryptBase64StringWithDevicePrivateKey(encryptedPassword);

            if (!mHelper.openWritableDatabase(password))
            {
               return new LocalizedException(mHelper.getOpenError());
            }
         }
         catch (LocalizedException e)
         {
            return e;
         }
         return null;
      }

      @Override
      protected void onPostExecute(LocalizedException exception)
      {
         if (exception != null)
         {
            mListener.ftsDatabaseHasError(exception);
         }
         else
         {
            mListener.ftsDatabaseIsOpen();
         }
      }
   }
}
